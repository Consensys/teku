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

package tech.pegasys.teku.statetransition.validation.block.rules.phase0;

import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.block.rules.StatelessValidationRule;

public class BlockParentSlotRule implements StatelessValidationRule {

  private final GossipValidationHelper gossipValidationHelper;

  public BlockParentSlotRule(final GossipValidationHelper gossipValidationHelper) {
    this.gossipValidationHelper = gossipValidationHelper;
  }

  /*
   * [REJECT] The block is from a higher slot than its parent.
   */
  @Override
  public Optional<InternalValidationResult> validate(final SignedBeaconBlock block) {
    final Optional<UInt64> maybeParentBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(block.getParentRoot());
    final UInt64 parentBlockSlot =
        maybeParentBlockSlot.orElseThrow(
            () -> new IllegalStateException("Unable to get parent block slot."));
    if (parentBlockSlot.isGreaterThanOrEqualTo(block.getSlot())) {
      return Optional.of(reject("Parent block is after child block."));
    }
    return Optional.empty();
  }
}
