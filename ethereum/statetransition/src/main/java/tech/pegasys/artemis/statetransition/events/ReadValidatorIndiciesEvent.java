package tech.pegasys.artemis.statetransition.events;

import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;

public class ReadValidatorIndiciesEvent {

  private final BeaconStateWithCache startState;

  public ReadValidatorIndiciesEvent(final BeaconStateWithCache startState) {
    this.startState = startState;
  }

  public BeaconStateWithCache getStartState() {
    return startState;
  }
}
