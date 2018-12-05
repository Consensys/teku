package net.consensys.beaconchain.datastructures;

import net.consensys.beaconchain.util.uint.UInt384;

public class SpecialAttestationData {

  private int[] aggregate_signature_poc_0_indices;
  private int[] aggregate_signature_poc_1_indices;
  private AttestationData data;
  private UInt384[] aggregate_signature;

  public SpecialAttestationData() {

  }

}
