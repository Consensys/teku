package main.java.net.consensys.beacon.state;

import main.java.net.consensys.beacon.datastructures.CrosslinkRecord;
import main.java.net.consensys.beacon.datastructures.ShardAndCommittee;
import main.java.net.consensys.beacon.datastructures.ValidatorRecord;
import org.web3j.abi.datatypes.generated.Int16;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.Int64;
import util.src.main.java.net.consensys.beacon.util.Hash32;

public class CrystallizedState {

  private ValidatorRecord[] validators;
  private Int64 lastStateRecalc;
  private ShardAndCommittee[][] shardAndCommitteeForSlots; // may need to fix this
  private Int64 lastJustifiedSlot;
  private Int64 lastFinalizedSlot;
  private Int64 currentDynasty;
  private Int16 crosslinkingStartShard;
  private CrosslinkRecord[] crosslinkRecords;
  private Int256 totalDeposits;
  private Hash32 dynastySeed;
  private Int64 dynastyStart;

  public CrystallizedState() {
  }

}
