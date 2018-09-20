package net.consensys.beaconchain.datastructures;

import org.web3j.abi.datatypes.generated.Int16;
import org.web3j.abi.datatypes.generated.Int24;

public class ShardAndCommittee {

    private Int16 shard_id;
    private Int24[] committee;

    public ShardAndCommittee() {

    }

}