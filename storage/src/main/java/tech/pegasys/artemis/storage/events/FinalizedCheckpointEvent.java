/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.artemis.storage.events;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import tech.pegasys.artemis.datastructures.state.Checkpoint;

public class FinalizedCheckpointEvent {
  private final Checkpoint finalizedCheckpoint;

  public FinalizedCheckpointEvent(final Checkpoint finalizedCheckpoint) {
    this.finalizedCheckpoint = finalizedCheckpoint;
  }

  public Checkpoint getCheckpoint() {
    return finalizedCheckpoint;
  }

  public UnsignedLong getFinalizedSlot() {
    return finalizedCheckpoint.getEpochStartSlot();
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FinalizedCheckpointEvent)) {
      return false;
    }
    final FinalizedCheckpointEvent that = (FinalizedCheckpointEvent) o;
    return Objects.equals(finalizedCheckpoint, that.finalizedCheckpoint);
  }

  @Override
  public int hashCode() {
    return Objects.hash(finalizedCheckpoint);
  }
}
