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

package tech.pegasys.teku.api.response.v1.beacon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.api.schema.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class FinalityCheckpointsResponse {
  @JsonProperty("previous_justified")
  public final Checkpoint previous_justified;

  @JsonProperty("current_justified")
  public final Checkpoint current_justified;

  @JsonProperty("finalized")
  public final Checkpoint finalized;

  @JsonCreator
  public FinalityCheckpointsResponse(
      @JsonProperty("previous_justified") Checkpoint previous_justified,
      @JsonProperty("current_justified") Checkpoint current_justified,
      @JsonProperty("finalized") Checkpoint finalized) {
    this.previous_justified = previous_justified;
    this.current_justified = current_justified;
    this.finalized = finalized;
  }

  public static FinalityCheckpointsResponse fromState(BeaconState state) {
    if (state.getFinalized_checkpoint().getEpoch().equals(UInt64.ZERO)) {
      return new FinalityCheckpointsResponse(Checkpoint.EMPTY, Checkpoint.EMPTY, Checkpoint.EMPTY);
    }
    return new FinalityCheckpointsResponse(
        new Checkpoint(state.getPrevious_justified_checkpoint()),
        new Checkpoint(state.getCurrent_justified_checkpoint()),
        new Checkpoint(state.getFinalized_checkpoint()));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final FinalityCheckpointsResponse that = (FinalityCheckpointsResponse) o;
    return Objects.equals(previous_justified, that.previous_justified)
        && Objects.equals(current_justified, that.current_justified)
        && Objects.equals(finalized, that.finalized);
  }

  @Override
  public int hashCode() {
    return Objects.hash(previous_justified, current_justified, finalized);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("previous_justified", previous_justified)
        .add("current_justified", current_justified)
        .add("finalized", finalized)
        .toString();
  }
}
