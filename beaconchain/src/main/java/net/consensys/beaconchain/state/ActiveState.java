package net.consensys.beaconchain.state;

import net.consensys.beaconchain.datastructures.AttestationRecord;
import net.consensys.beaconchain.datastructures.SpecialObject;
import net.consensys.beaconchain.ethereum.core.Hash;

public class ActiveState {

  private AttestationRecord[] pending_attestations;
  private Hash[] recent_block_hashes;
  private SpecialObject[] pending_specials;

  public ActiveState() {

  }

}
