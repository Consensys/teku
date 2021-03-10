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

package tech.pegasys.teku.spec.datastructures.blocks;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class CheckpointEpochs {
  private final UInt64 justifiedEpoch;
  private final UInt64 finalizedEpoch;

  public CheckpointEpochs(final UInt64 justifiedEpoch, final UInt64 finalizedEpoch) {
    this.justifiedEpoch = justifiedEpoch;
    this.finalizedEpoch = finalizedEpoch;
  }

  public static CheckpointEpochs fromBlockAndState(final StateAndBlockSummary blockAndState) {
    final BeaconState state = blockAndState.getState();
    return new CheckpointEpochs(
        state.getCurrent_justified_checkpoint().getEpoch(),
        state.getFinalized_checkpoint().getEpoch());
  }

  public UInt64 getJustifiedEpoch() {
    return justifiedEpoch;
  }

  public UInt64 getFinalizedEpoch() {
    return finalizedEpoch;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CheckpointEpochs that = (CheckpointEpochs) o;
    return Objects.equals(justifiedEpoch, that.justifiedEpoch)
        && Objects.equals(finalizedEpoch, that.finalizedEpoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(justifiedEpoch, finalizedEpoch);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("justifiedEpoch", justifiedEpoch)
        .add("finalizedEpoch", finalizedEpoch)
        .toString();
  }
}
