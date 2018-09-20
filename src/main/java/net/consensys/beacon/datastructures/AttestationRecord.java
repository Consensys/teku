package main.java.net.consensys.beacon.datastructures;

import org.web3j.abi.datatypes.generated.Int16;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.Int64;
import util.src.main.java.net.consensys.beacon.util.Hash32;

public class AttestationRecord {

  private Int64 slot;
  private Int16 shard;
  private Hash32[] obliqueParentHashes;
  private Hash32 shardBlockHash;
  private byte attesterBitfield;
  private Int64 justifiedSlot;
  private Hash32 justifiedBlockHash;
  private Int256[] aggregateSig;

  public AttestationRecord() {
  }
  
}