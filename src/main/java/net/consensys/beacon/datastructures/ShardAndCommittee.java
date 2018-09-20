package main.java.net.consensys.beacon.datastructures;

import org.web3j.abi.datatypes.generated.Int16;
import org.web3j.abi.datatypes.generated.Int24;

public class ShardAndCommittee {

  private Int16 shardId;
  private Int24[] committee;

  public ShardAndCommittee() {
  }
}