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

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.StatelessValidationRule;

public class BlockParentSeenRule implements StatelessValidationRule {

  private static final Logger LOG = LogManager.getLogger();

  private final GossipValidationHelper gossipValidationHelper;

  public BlockParentSeenRule(final GossipValidationHelper gossipValidationHelper) {
    this.gossipValidationHelper = gossipValidationHelper;
  }

  /*
   * [IGNORE] The block's parent (defined by block.parent_root) has been seen (via gossip or non-gossip sources)
   * (a client MAY queue blocks for processing once the parent block is retrieved).
   */
  @Override
  public Optional<InternalValidationResult> validate(final SignedBeaconBlock block) {
    final Optional<UInt64> maybeParentBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(block.getParentRoot());
    if (maybeParentBlockSlot.isEmpty()) {
      LOG.trace(
          "BlockValidator: Parent block does not exist. It will be saved for future processing");
      return Optional.of(InternalValidationResult.SAVE_FOR_FUTURE);
    }
    return Optional.empty();
  }
}
