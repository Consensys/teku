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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.StatelessValidationRule;

public class ExecutionPayloadParentRootRule implements StatelessValidationRule {

  /*
   * [REJECT] The bid's parent (defined by bid.parent_block_root) equals the block's parent (defined by block.parent_root).
   */
  @Override
  public Optional<InternalValidationResult> validate(final SignedBeaconBlock block) {
    final Bytes32 executionPayloadBidParentRoot =
        block
            .getMessage()
            .getBody()
            .getOptionalSignedExecutionPayloadBid()
            .orElseThrow(
                () -> new IllegalStateException("block missing signed execution payload bid"))
            .getMessage()
            .getParentBlockRoot();
    if (!executionPayloadBidParentRoot.equals(block.getParentRoot())) {
      return Optional.of(
          InternalValidationResult.reject("Execution payload has invalid parent block root"));
    }

    return Optional.empty();
  }
}
