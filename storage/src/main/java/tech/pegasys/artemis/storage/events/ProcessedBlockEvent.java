package tech.pegasys.artemis.storage.events;

import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Checkpoint;

public class ProcessedBlockEvent {
  private final BeaconStateWithCache postState;
  private final BeaconBlock processedBlock;
  private final Checkpoint justifiedCheckpoint;
  private final Checkpoint finalizedCheckpoint;

  public ProcessedBlockEvent(
      final BeaconStateWithCache postState,
      final BeaconBlock processedBlock,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint) {
    this.postState = postState;
    this.processedBlock = processedBlock;
    this.justifiedCheckpoint = justifiedCheckpoint;
    this.finalizedCheckpoint = finalizedCheckpoint;
  }

  public BeaconStateWithCache getPostState() {
    return postState;
  }

  public BeaconBlock getProcessedBlock() {
    return processedBlock;
  }

  public Checkpoint getJustifiedCheckpoint() {
    return justifiedCheckpoint;
  }

  public Checkpoint getFinalizedCheckpoint() {
    return finalizedCheckpoint;
  }
}
