package main.java.net.consensys.beacon.datastructures;

import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Int128;
import org.web3j.abi.datatypes.generated.Int16;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.Int64;
import util.src.main.java.net.consensys.beacon.util.Hash32;

public class ValidatorRecord {

  private Int256 pubkey;
  private Int16 withdrawalShard;
  private Address withdrawalAddress;
  private Hash32 randaoCommitment;
  private Int128 balance;
  private Int64 startDynasty;
  private Int64 endDynasty;

  public ValidatorRecord() {
  }

}
