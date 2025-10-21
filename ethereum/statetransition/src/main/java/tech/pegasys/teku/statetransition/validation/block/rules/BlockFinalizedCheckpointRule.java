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

package tech.pegasys.teku.statetransition.validation.block.rules;

import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class BlockFinalizedCheckpointRule implements StatelessValidationRule {

  private final GossipValidationHelper gossipValidationHelper;

  public BlockFinalizedCheckpointRule(final GossipValidationHelper gossipValidationHelper) {
    this.gossipValidationHelper = gossipValidationHelper;
  }

  /*
   * [REJECT] The current finalized_checkpoint is an ancestor of block
   * -- i.e. get_checkpoint_block(store, block.parent_root, store.finalized_checkpoint.epoch) == store.finalized_checkpoint.root
   */
  @Override
  public Optional<InternalValidationResult> validate(SignedBeaconBlock block) {
    if (!gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
        block.getSlot(), block.getParentRoot())) {
      return Optional.of(reject("Block does not descend from finalized checkpoint"));
    }
    return Optional.empty();
  }
}
