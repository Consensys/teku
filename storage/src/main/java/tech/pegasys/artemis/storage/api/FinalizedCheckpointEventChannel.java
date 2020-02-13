package tech.pegasys.artemis.storage.api;

import tech.pegasys.artemis.storage.events.FinalizedCheckpointEvent;

public interface FinalizedCheckpointEventChannel {
  void onFinalizedCheckpoint(FinalizedCheckpointEvent event);
}
