package net.consensys.beaconchain.datastructures.blocks;

import net.consensys.beaconchain.ethereum.core.Hash;

public class BeaconBlock {

  private Uint64 slot;
  private Hash randao_reveal;
  private Hash candidate_pow_receipt_root;
  private Hash[] ancestor_hashes;
  private Hash state_root;
  private AttestationRecord[] attestations;
  private SpecialRecord[] specials;
  private Uint384[] proposer_signature;

  public BeaconBlock() {

  }

}
