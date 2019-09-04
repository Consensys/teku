package tech.pegasys.artemis.storage.events;

import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;

public class NodeStartEvent {
  private final BeaconStateWithCache state;

  public NodeStartEvent(final BeaconStateWithCache state) {
    this.state = state;
  }

  public BeaconStateWithCache getState() {
    return state;
  }
}
