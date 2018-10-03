package net.consensys.beaconchain.datastructures;

import net.consensys.beaconchain.ethereum.core.Hash;

import org.web3j.abi.datatypes.generated.Int64;

public class CrosslinkRecord {

  private Hash hash;
  private Int64 dynasty;
  private Int64 slot;

  public CrosslinkRecord() {

  }

}
