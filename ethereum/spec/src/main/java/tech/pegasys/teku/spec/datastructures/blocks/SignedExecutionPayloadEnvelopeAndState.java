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

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/** Helper datastructure that holds a signed execution envelope with its corresponding state */
public class SignedExecutionPayloadEnvelopeAndState extends StateAndExecutionPayloadSummary {
  private final SignedExecutionPayloadEnvelope executionPayloadEnvelope;

  public SignedExecutionPayloadEnvelopeAndState(
      final SignedExecutionPayloadEnvelope executionPayloadEnvelope, final BeaconState state) {
    super(state, executionPayloadEnvelope.getMessage().getPayload());
    this.executionPayloadEnvelope = executionPayloadEnvelope;
  }

  public SignedExecutionPayloadEnvelope getExecutionPayloadEnvelope() {
    return executionPayloadEnvelope;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof SignedExecutionPayloadEnvelopeAndState)) {
      return false;
    }
    final SignedExecutionPayloadEnvelopeAndState that = (SignedExecutionPayloadEnvelopeAndState) o;
    return Objects.equals(getExecutionPayloadEnvelope(), that.getExecutionPayloadEnvelope())
        && Objects.equals(getState(), that.getState());
  }

  @Override
  public int hashCode() {
    return Objects.hash(executionPayloadEnvelope, state);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executionPayloadEnvelope", executionPayloadEnvelope)
        .add("state", state)
        .toString();
  }
}
