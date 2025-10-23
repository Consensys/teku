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
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.StatefulValidationRule;

public record ExpectedProposerRule(GossipValidationHelper gossipValidationHelper)
    implements StatefulValidationRule {

  /*
   * [REJECT] The block is proposed by the expected proposer_index for the block's slot in the context
   * of the current shuffling (defined by parent_root/slot).
   * If the proposer_index cannot immediately be verified against the expected shuffling,
   * the block MAY be queued for later processing while proposers for the block's branch are calculated
   * -- in such a case do not REJECT, instead IGNORE this message.
   */
  @Override
  public Optional<InternalValidationResult> validate(
      final SignedBeaconBlock block, final BeaconState parentState) {
    if (!gossipValidationHelper.isProposerTheExpectedProposer(
        block.getProposerIndex(), block.getSlot(), parentState)) {
      return Optional.of(
          reject("Block proposed by incorrect proposer (%s)", block.getProposerIndex()));
    }
    return Optional.empty();
  }
}
