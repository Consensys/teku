package main.java.net.consensys.beacon.datastructures;

import org.web3j.abi.datatypes.generated.Int64;
import util.src.main.java.net.consensys.beacon.util.Hash32;

public class CrosslinkRecord {

  private Int64 dynasty;
  private Int64 slot;
  private Hash32 hash;

  public CrosslinkRecord() {

  }

}
