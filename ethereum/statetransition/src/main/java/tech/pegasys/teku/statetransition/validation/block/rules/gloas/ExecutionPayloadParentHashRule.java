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

package tech.pegasys.teku.statetransition.validation.block.rules.gloas;

import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.StatefulValidationRule;

public record ExecutionPayloadParentHashRule() implements StatefulValidationRule {

  /*
   * [REJECT] The block's execution payload parent (defined by bid.parent_block_hash) passes all validation
   */
  @Override
  public Optional<InternalValidationResult> validate(
      final SignedBeaconBlock block, final BeaconState parentState) {
    final ExecutionPayloadBid executionPayloadBid =
        block
            .getMessage()
            .getBody()
            .getOptionalSignedExecutionPayloadBid()
            .orElseThrow(
                () -> new IllegalStateException("block missing signed execution payload bid"))
            .getMessage();
    final BeaconStateGloas parentStateGloas = BeaconStateGloas.required(parentState);
    if (!executionPayloadBid.getParentBlockHash().equals(parentStateGloas.getLatestBlockHash())) {
      return Optional.of(reject("Execution payload bid has invalid parent block hash"));
    }
    return Optional.empty();
  }
}
