package net.consensys.beaconchain.datastructures;

import net.consensys.beaconchain.ethereum.core.Hash;
import org.web3j.abi.datatypes.generated.Int64;

public class Block {

    private AttestationRecord[] attestations;
    private Hash active_state_root;
    private Hash crystallized_state_root;
    private Hash parent_hash;
    private Hash pow_chain_ref;
    private Hash randao_reveal;
    private Int64 slot_number;
    private SpecialObject[] specials;

    public Block() {

    }

}
