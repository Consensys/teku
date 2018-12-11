package net.consensys.beaconchain.datastructures.BeaconChainOperations;

import net.consensys.beaconchain.util.uint.UInt384;

public class SlashableVoteData {

  private int[] aggregate_signature_poc_0_indices;
  private int[] aggregate_signature_poc_1_indices;
  private AttestationData data;
  private UInt384[] aggregate_signature;

  public SlashableVoteData() {

  }
}
