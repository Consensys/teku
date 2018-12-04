package net.consensys.beaconchain.datastructures.specials;

import net.consensys.beaconchain.datastructures.blocks.AttestationData;

public class SpecialAttestationData {

  private Uint24[] aggregate_signature_poc_0_indices;
  private Uint24[] aggregate_signature_poc_1_indices;
  private AttestationData data;
  private Uint384[] aggregate_signature;

  public SpecialAttestationData() {

  }

}
