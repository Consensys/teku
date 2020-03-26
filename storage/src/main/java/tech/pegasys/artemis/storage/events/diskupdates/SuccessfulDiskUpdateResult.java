package tech.pegasys.artemis.storage.events.diskupdates;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.state.Checkpoint;

import java.util.Set;

public class SuccessfulDiskUpdateResult implements DiskUpdateResult {
  private final Set<Bytes32> prunedBlockRoots;
  private final Set<Checkpoint> prunedCheckpoints;

  public SuccessfulDiskUpdateResult(
      final Set<Bytes32> prunedBlockRoots, final Set<Checkpoint> prunedCheckpoints) {
    this.prunedBlockRoots = prunedBlockRoots;
    this.prunedCheckpoints = prunedCheckpoints;
  }

  @Override
  public boolean isSuccessful() {
    return true;
  }

  @Override
  public RuntimeException getError() {
    return null;
  }

  @Override
  public Set<Checkpoint> getPrunedCheckpoints() {
    return prunedCheckpoints;
  }

  @Override
  public Set<Bytes32> getPrunedBlockRoots() {
    return prunedBlockRoots;
  }
}
