package ch.epfl.dedis.byzcoin.contracts;

import ch.epfl.dedis.byzcoin.ByzCoinRPC;
import ch.epfl.dedis.byzcoin.Instance;
import ch.epfl.dedis.byzcoin.InstanceId;
import ch.epfl.dedis.byzcoin.transaction.Argument;
import ch.epfl.dedis.byzcoin.transaction.ClientTransaction;
import ch.epfl.dedis.byzcoin.transaction.Instruction;
import ch.epfl.dedis.byzcoin.transaction.Invoke;
import ch.epfl.dedis.lib.darc.Signer;
import ch.epfl.dedis.lib.exception.CothorityCommunicationException;
import ch.epfl.dedis.lib.exception.CothorityCryptoException;
import ch.epfl.dedis.lib.exception.CothorityException;
import ch.epfl.dedis.lib.exception.CothorityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * NamingInstance is represents an instance that can be used to give names to instance IDs.
 * This instance is a singleton on ByzCoin just like the config instance.
 */
public class NamingInstance {
    public static String ContractId = "naming";
    private Instance instance;
    private ByzCoinRPC bc;

    private final static Logger logger = LoggerFactory.getLogger(NamingInstance.class);

    /**
     * Loads the singleton naming instance from ByzCoin. A naming instance cannot be created from this class because it
     * is a singleton on ByzCoin and it is created at start-up.
     *
     * @param bc is a running ByzCoin service
     * @return a reference to the instance
     * @throws CothorityNotFoundException      if the instance cannot be found on ByzCoin, perhaps an old version that
     *                                         does not support naming is used
     * @throws CothorityCommunicationException if there is a communication error
     * @throws CothorityCryptoException        if result that we got back form ByzCoin contains a wrong proof
     */
    public static NamingInstance fromByzcoin(ByzCoinRPC bc) throws CothorityNotFoundException, CothorityCommunicationException, CothorityCryptoException {
        // The naming instance is a singleton, the 32 byte buffer that starts with a 1 holds the instance.
        byte[] iidBuf = new byte[32];
        iidBuf[0] = 1;
        return new NamingInstance(bc, Instance.fromByzcoin(bc, new InstanceId(iidBuf)));
    }

    /**
     * Asynchronously assigns a name to an instance ID. After the instance is named, ByzCoin.resolveInstanceID can be
     * used to resolve the name. Once set, the name or instance ID cannot be changed. It is not allowed to set a name
     * that was previously removed.
     *
     * @param instanceName is the name given to the instance ID
     * @param iID          is the to-be-named instance ID
     * @param owners       is a list of signers that holds the keys to the "_name" rule in the Darc the guards iID
     * @param ownerCtrs    is the list of monotonically increasing counters that will go into the instruction,
     *                     they must match the signers who will eventually sign the instruction
     * @throws CothorityException if any error occurs
     */
    public void set(String instanceName, InstanceId iID, List<Signer> owners, List<Long> ownerCtrs) throws CothorityException {
        bc.sendTransaction(makeAddTx(instanceName, iID, owners, ownerCtrs));
    }

    /**
     * Assigns a name to an instance ID and wait for confirmation. After the instance is named,
     * ByzCoin.resolveInstanceID can be used to resolve the name. Once set, the name or instance ID cannot be changed.
     * It is not allowed to set a name that was previously removed.
     *
     * @param instanceName is the name given to the instance ID
     * @param iID          is the to-be-named instance ID
     * @param owners       is a list of signers that holds the keys to the "_name" rule in the Darc the guards iID
     * @param ownerCtrs    is the list of monotonically increasing counters that will go into the instruction,
     *                     they must match the signers who will eventually sign the instruction
     * @param wait         how many blocks to wait for inclusion of the transaction
     * @throws CothorityException if any error occurs
     */
    public void setAndWait(String instanceName, InstanceId iID, List<Signer> owners, List<Long> ownerCtrs, int wait) throws CothorityException {
        bc.sendTransactionAndWait(makeAddTx(instanceName, iID, owners, ownerCtrs), wait);
    }

    /**
     * This method asynchronously removes an instance name. Once removed, the name cannot be used in set again.
     *
     * @param instanceName is the name of the instance to remove
     * @param iID          is instance ID which must have been named previously to instanceName
     * @param owners       is a list of signers that holds the keys to the "_name" rule in the Darc the guards iID
     * @param ownerCtrs    is the list of monotonically increasing counters that will go into the instruction,
     *                     they must match the signers who will eventually sign the instruction
     * @throws CothorityException
     */
    public void remove(String instanceName, InstanceId iID, List<Signer> owners, List<Long> ownerCtrs) throws CothorityException {
        bc.sendTransaction(makeRemoveTx(instanceName, iID, owners, ownerCtrs));
    }

    /**
     * This method removes an instance name and then waits for confirmation. Once removed, the name cannot be used in
     * set again.
     *
     * @param instanceName is the name of the instance to remove
     * @param iID          is instance ID which must have been named previously to instanceName
     * @param owners       is a list of signers that holds the keys to the "_name" rule in the Darc the guards iID
     * @param ownerCtrs    is the list of monotonically increasing counters that will go into the instruction,
     *                     they must match the signers who will eventually sign the instruction
     * @param wait         how many blocks to wait for inclusion of the transaction
     * @throws CothorityException
     */
    public void removeAndWait(String instanceName, InstanceId iID, List<Signer> owners, List<Long> ownerCtrs, int wait) throws CothorityException {
        bc.sendTransactionAndWait(makeRemoveTx(instanceName, iID, owners, ownerCtrs), wait);
    }

    private NamingInstance(ByzCoinRPC bc, Instance instance) throws CothorityNotFoundException {
        if (!instance.getContractId().equals(ContractId)) {
            logger.error("wrong contractId: {}", instance.getContractId());
            throw new CothorityNotFoundException("this is not a value instance");
        }
        this.bc = bc;
        this.instance = instance;
    }

    private ClientTransaction makeTx(String instanceName, InstanceId iID, List<Signer> owners, List<Long> ownerCtrs, String cmd) throws CothorityCryptoException {
        List<Argument> args = new ArrayList<>();
        args.add(new Argument("name", instanceName.getBytes()));
        args.add(new Argument("instanceID", iID.getId()));
        Invoke inv = new Invoke(ContractId, cmd, args);
        Instruction namingInst = new Instruction(
                this.instance.getId(),
                owners.stream().map(Signer::getIdentity).collect(Collectors.toList()),
                ownerCtrs,
                inv);
        ClientTransaction ct = new ClientTransaction(Collections.singletonList(namingInst));
        ct.signWith(owners);
        return ct;
    }

    private ClientTransaction makeRemoveTx(String instanceName, InstanceId iID, List<Signer> owners, List<Long> ownerCtrs) throws CothorityCryptoException {
        return makeTx(instanceName, iID, owners, ownerCtrs, "remove");
    }

    private ClientTransaction makeAddTx(String instanceName, InstanceId iID, List<Signer> owners, List<Long> ownerCtrs) throws CothorityCryptoException {
        return makeTx(instanceName, iID, owners, ownerCtrs, "add");
    }
}
