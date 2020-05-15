package byzcoin

import (
	"errors"
	"time"

	"go.dedis.ch/cothority/v3/skipchain"
	"go.dedis.ch/onet/v3"
	"go.dedis.ch/onet/v3/log"
	"go.dedis.ch/onet/v3/network"
	"golang.org/x/xerrors"
)

func init() {
	network.RegisterMessages(&AddTxRequest{}, &RequestAdded{})
	_, err := onet.GlobalProtocolRegister(rollupTxProtocol, NewRollupTxProtocol)
	log.ErrFatal(err)
}

const rollupTxProtocol = "RollupTxProtocol"

// RollupTxProtocol is a protocol for collecting pending transactions.
// It allows nodes to send new transactions to the root.
// Clients can send their transaction to any node,
// and the nodes will make sure that the transaction gets included in one of
// the next blocks.
type RollupTxProtocol struct {
	*onet.TreeNodeInstance
	NewTx             *AddTxRequest
	CtxChan           chan ClientTransaction
	CommonVersionChan chan Version
	SkipchainID       skipchain.SkipBlockID
	LatestID          skipchain.SkipBlockID
	DoneChan          chan error
	Finish            chan bool
	closing           chan bool

	addRequestChan   chan structAddTxRequest
	requestAddedChan chan structRequestAdded
}

type structAddTxRequest struct {
	*onet.TreeNode
	AddTxRequest
}

type structRequestAdded struct {
	*onet.TreeNode
	RequestAdded
}

// RequestAdded is the message that is sent in the requestAddedChan after a
// channel has been registered, in order for Dispatch() to become aware of
// the newly registered channel.
type RequestAdded struct {
}

// NewRollupTxProtocol is used for registering the protocol.
// This was in the signature before.
func NewRollupTxProtocol(node *onet.TreeNodeInstance) (onet.ProtocolInstance, error) {
	c := &RollupTxProtocol{
		TreeNodeInstance: node,
		// If we do not buffer this channel then the protocol
		// might be blocked from stopping when the receiver
		// stops reading from this channel.
		CommonVersionChan: make(chan Version, len(node.List())),
		DoneChan:          make(chan error, 2),
		Finish:            make(chan bool),
		closing:           make(chan bool),
	}
	if err := node.RegisterChannels(&c.addRequestChan, &c.requestAddedChan); err != nil {
		return c, xerrors.Errorf("registering channels: %v", err)
	}
	return c, nil
}

// Start starts the protocol, it should only be called on the root node.
func (p *RollupTxProtocol) Start() error {
	if !p.IsRoot() {
		return errors.New("only the root should call start")
	}
	if len(p.SkipchainID) == 0 {
		return errors.New("missing skipchain ID")
	}
	if len(p.LatestID) == 0 {
		return errors.New("missing latest skipblock ID")
	}
	err := p.SendTo(p.Children()[0], p.NewTx)
	if err != nil {
		p.Done()
		p.DoneChan <- err
	}

	return nil
}

// Dispatch runs the protocol.
func (p *RollupTxProtocol) Dispatch() error {
	defer p.Done()
	if p.IsRoot() {
		select {
		case <-p.requestAddedChan:
			p.DoneChan <- nil
			return nil
		case <-p.Finish:
			return nil
		case <-p.closing:
			return nil
		case <-time.After(defaultInterval):
			err := errors.New("timeout while waiting for leader's reply")
			p.DoneChan <- err
			return err
		}
	}

	select {
	case arc := <-p.addRequestChan:
		p.CtxChan <- (arc).Transaction
		return p.SendToParent(&RequestAdded{})
	case <-p.Finish:
	case <-p.closing:
	}

	return nil
}

// Shutdown closes the closing channel to abort any waiting on messages.
func (p *RollupTxProtocol) Shutdown() error {
	close(p.closing)
	return nil
}
