package net.consensys.beaconchain.datastructures;

import net.consensys.beaconchain.ethereum.core.Hash;
import org.web3j.abi.datatypes.Bytes;
import org.web3j.abi.datatypes.generated.Int16;
import org.web3j.abi.datatypes.generated.Int64;
import org.web3j.abi.datatypes.generated.Int256;

public class AttestationRecord {

    private Bytes attester_bitfield;
    private Hash justified_block_hash;
    private Hash shard_block_hash;
    private Hash[] oblique_parent_hashes;
    private Int16 shard_id;
    private Int64 justified_slot;
    private Int64 slot;
    private Int256[] aggregate_sig;

    public AttestationRecord() {

    }

}