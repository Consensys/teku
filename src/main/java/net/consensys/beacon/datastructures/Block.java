package main.java.net.consensys.beacon.datastructures;

import main.java.net.consensys.beacon.datastructures.AttestationRecord;
import org.web3j.abi.datatypes.generated.Int64;
import util.src.main.java.net.consensys.beacon.util.Hash32;

public class Block {

  private AttestationRecord[] attestations;
  private Hash32 parentHash;
  private Int64 slotNumber;
  private Hash32 randaoReveal;
  private Hash32 powChainReference;
  private Hash32 activeStateRoot;
  private Hash32 crystallizedStateRoot;

  public Block(Hash32 parentHash, Int64 slotNumber, Hash32 powChainReference, 
      Hash32 activeStateRoot, Hash32 crystallizedStateRoot) {
    this.attestations = new AttestationRecord[1]; // how big should this list be?
    this.parentHash = parentHash; // can't change once set.
    this.slotNumber = slotNumber;
    this.powChainReference = powChainReference;
    this.activeStateRoot = activeStateRoot;
    this.crystallizedStateRoot = crystallizedStateRoot;
  }
}
