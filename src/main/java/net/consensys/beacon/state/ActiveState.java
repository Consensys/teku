package main.java.net.consensys.beacon.state;

import main.java.net.consensys.beacon.datastructures.AttestationRecord;
import util.src.main.java.net.consensys.beacon.util.Hash32;

public class ActiveState {

  private AttestationRecord[] pendingAttestations;
  private Hash32[] recentBlockHashes;

  public ActiveState() {
  }

  // TODO: reset ActiveState. what happens? converted to CrystallizedState?	
}
