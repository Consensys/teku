package tech.pegasys.artemis.storage.events.diskupdates;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.state.Checkpoint;

import java.util.Collections;
import java.util.Set;

public class FailedDiskUpdateResult implements DiskUpdateResult {
  private final RuntimeException error;

  FailedDiskUpdateResult(final RuntimeException error) {
    this.error = error;
  }

  @Override
  public boolean isSuccessful() {
    return false;
  }

  @Override
  public RuntimeException getError() {
    return error;
  }

  @Override
  public Set<Bytes32> getPrunedBlockRoots() {
    return Collections.emptySet();
  }

  @Override
  public Set<Checkpoint> getPrunedCheckpoints() {
    return Collections.emptySet();
  }
}
