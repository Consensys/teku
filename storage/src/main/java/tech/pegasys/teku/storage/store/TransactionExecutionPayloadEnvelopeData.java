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

package tech.pegasys.teku.storage.store;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndExecutionPayloadAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedExecutionPayloadEnvelopeAndState;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class TransactionExecutionPayloadEnvelopeData
    extends SignedExecutionPayloadEnvelopeAndState {
  private final SignedBeaconBlock block;
  private final BlockCheckpoints checkpoints;

  public TransactionExecutionPayloadEnvelopeData(
      final SignedBeaconBlock block,
      final SignedExecutionPayloadEnvelope executionPayloadEnvelope,
      final BeaconState state,
      final BlockCheckpoints checkpoints) {
    super(executionPayloadEnvelope, state);
    this.block = block;
    this.checkpoints = checkpoints;
  }

  public SignedBeaconBlock getBlock() {
    return block;
  }

  public BlockCheckpoints getCheckpoints() {
    return checkpoints;
  }

  public BlockAndExecutionPayloadAndCheckpoints toBlockAndExecutionPayloadAndCheckpoints() {
    return new BlockAndExecutionPayloadAndCheckpoints(
        getBlock(), getExecutionPayloadEnvelope(), getCheckpoints());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TransactionExecutionPayloadEnvelopeData that =
        (TransactionExecutionPayloadEnvelopeData) o;
    return Objects.equals(getExecutionPayloadEnvelope(), that.getExecutionPayloadEnvelope())
        && Objects.equals(getState(), that.getState())
        && Objects.equals(checkpoints, that.checkpoints);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getExecutionPayloadEnvelope(), getState(), checkpoints);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executionPayloadEnvelope", getExecutionPayloadEnvelope())
        .add("state", getState())
        .add("checkpoints", checkpoints)
        .toString();
  }
}
