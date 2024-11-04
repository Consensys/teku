/*
 * Copyright Consensys Software Inc., 2022
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class StateAndExecutionPayloadSummary {
  protected final BeaconState state;
  protected final ExecutionPayloadSummary executionPayloadSummary;

  protected StateAndExecutionPayloadSummary(
      final BeaconState state, final ExecutionPayloadSummary executionPayloadSummary) {
    checkNotNull(state);
    checkNotNull(executionPayloadSummary);
    checkArgument(
        executionPayloadSummary.getStateRoot().equals(state.hashTreeRoot()),
        "Execution payload state root must match the supplied state");
    this.state = state;
    this.executionPayloadSummary = executionPayloadSummary;
  }

  public static StateAndExecutionPayloadSummary create(
      final BeaconState state, final ExecutionPayloadSummary executionPayloadSummary) {
    return new StateAndExecutionPayloadSummary(state, executionPayloadSummary);
  }

  public BeaconState getState() {
    return state;
  }

  public ExecutionPayloadSummary getExecutionPayloadSummary() {
    return executionPayloadSummary;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final StateAndExecutionPayloadSummary that = (StateAndExecutionPayloadSummary) o;
    return Objects.equals(state, that.state)
        && Objects.equals(executionPayloadSummary, that.executionPayloadSummary);
  }

  @Override
  public int hashCode() {
    return Objects.hash(state, executionPayloadSummary);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executionPayloadSummary", executionPayloadSummary)
        .toString();
  }
}
