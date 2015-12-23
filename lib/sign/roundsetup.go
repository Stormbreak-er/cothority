package sign

import (
	"github.com/dedis/cothority/lib/dbg"
)

/*
RoundSetup merely traverses the tree and counts the number of nodes.
This can be used to check the validity of the tree.
*/

// The name type of this round implementation
const RoundSetupType = "setup"

type RoundSetup struct {
	*RoundStruct
	Counted chan int
}

func init() {
	RegisterRoundFactory(RoundSetupType,
		func(node *Node) Round {
			return NewRoundSetup(node)
		})
}

func NewRoundSetup(node *Node) *RoundSetup {
	dbg.Lvl3("Making new RoundSetup", node.Name())
	round := &RoundSetup{}
	round.RoundStruct = NewRoundStruct(node, RoundSetupType)
	round.Counted = make(chan int, 1)
	return round
}

func (round *RoundSetup) Announcement(viewNbr, roundNbr int, in *AnnouncementMessage, out []*AnnouncementMessage) error {
	return nil
}

func (round *RoundSetup) Commitment(in []*CommitmentMessage, out *CommitmentMessage) error {
	out.Messages = 1
	if !round.IsLeaf {
		for _, i := range in {
			out.Messages += i.Messages
		}
	}
	if round.IsRoot {
		dbg.Lvl2("Number of nodes found:", out.Messages)
		round.Counted <- out.Messages
	}
	return nil
}

func (round *RoundSetup) Challenge(in *ChallengeMessage, out []*ChallengeMessage) error {
	return nil
}

func (round *RoundSetup) Response(in []*ResponseMessage, out *ResponseMessage) error {
	return nil
}

func (round *RoundSetup) SignatureBroadcast(in *SignatureBroadcastMessage, out []*SignatureBroadcastMessage) error {
	return nil
}
