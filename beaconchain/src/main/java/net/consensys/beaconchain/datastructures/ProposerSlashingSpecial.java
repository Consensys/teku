package net.consensys.beaconchain.datastructures;

import net.consensys.beaconchain.util.uint.UInt384;

public class ProposerSlashingSpecial {

  private int proposer_index;
  private ProposalSignedData proposal_data_1;
  private UInt384[] proposal_signature_1;
  private ProposalSignedData proposal_data_2;
  private UInt384[] proposal_signature_2;

  public ProposerSlashingSpecial() {

  }

}
