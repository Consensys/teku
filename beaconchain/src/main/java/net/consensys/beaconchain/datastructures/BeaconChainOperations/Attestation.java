package net.consensys.beaconchain.datastructures.BeaconChainOperations;

import net.consensys.beaconchain.util.bytes.Bytes32;
import net.consensys.beaconchain.util.uint.UInt384;


public class Attestation {

  private AttestationData data;
  private Bytes32 participation_bitfield;
  private Bytes32 custody_bitfield;
  private UInt384 aggregate_signature;

  public Attestation() {

  }

}
