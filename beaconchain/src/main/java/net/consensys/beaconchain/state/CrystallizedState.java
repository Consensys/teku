package net.consensys.beaconchain.state;

import net.consensys.beaconchain.datastructures.CrosslinkRecord;
import net.consensys.beaconchain.datastructures.ListOfValidators;
import net.consensys.beaconchain.datastructures.ShardAndCommittee;
import net.consensys.beaconchain.ethereum.core.Hash;
import org.web3j.abi.datatypes.generated.Int32;
import org.web3j.abi.datatypes.generated.Int64;

public class CrystallizedState {

    private CrosslinkRecord[] crosslink_records;
    private Hash dynasty_seed;
    private Int32[] penalized_in_wp;
    private Int64 current_dynasty;
    private Int64 dynasty_start;
    private Int64 justified_streak;
    private Int64 last_finalized_slot;
    private Int64 last_justified_slot;
    private Int64 last_state_recalc;
    private ListOfValidators validators;
    private ShardAndCommittee[][] shard_and_committee_for_slots;

    public CrystallizedState() {

    }

}