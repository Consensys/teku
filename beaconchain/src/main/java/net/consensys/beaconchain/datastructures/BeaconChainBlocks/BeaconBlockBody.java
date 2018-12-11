package net.consensys.beaconchain.datastructures.BeaconChainBlocks;

import net.consensys.beaconchain.datastructures.BeaconChainOperations.*;

public class BeaconBlockBody {

  private Attestation[] attestations;
  private ProposerSlashing[] proposer_slashings;
  private CasperSlashing[] casper_slashings;
  private Deposit[] deposits;
  private Exit[] exits;

  public BeaconBlockBody() {

  }
}
