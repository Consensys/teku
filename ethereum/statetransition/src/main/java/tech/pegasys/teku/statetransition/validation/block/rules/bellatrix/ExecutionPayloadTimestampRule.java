/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.validation.block.rules.bellatrix;

import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.Optional;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.StatefulValidationRule;

public class ExecutionPayloadTimestampRule implements StatefulValidationRule {

  private final Spec spec;

  public ExecutionPayloadTimestampRule(final Spec spec) {
    this.spec = spec;
  }

  /*
   * [REJECT] The block's execution payload timestamp is correct with respect to the slot
   * -- i.e. execution_payload.timestamp == compute_time_at_slot(state, block.slot)
   */
  @Override
  public Optional<InternalValidationResult> validate(
      final SignedBeaconBlock block, final BeaconState parentState) {
    Optional<ExecutionPayload> executionPayload =
        block.getMessage().getBody().getOptionalExecutionPayload();

    if (executionPayload.isEmpty()) {
      return Optional.of(reject("Missing execution payload"));
    }
    if (executionPayload
            .get()
            .getTimestamp()
            .compareTo(spec.computeTimeAtSlot(parentState, block.getSlot()))
        != 0) {
      return Optional.of(
          reject("Execution Payload timestamp is not consistence with and block slot time"));
    }
    return Optional.empty();
  }
}
