package net.consensys.beaconchain.datastructures.specials;

import net.consensys.beaconchain.datastructures.blocks.ProposalSignedData;

public class ProposerSlashingSpecial {

  private Uint24 proposer_index;
  private ProposalSignedData proposal_data_1;
  private Uint384[] proposal_signature_1;
  private ProposalSignedData proposal_data_2;
  private Uint384[] proposal_signature_2;

  public ProposerSlashingSpecial() {

  }

}
