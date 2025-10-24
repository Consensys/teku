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
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.StatelessValidationRule;

public record LatestFinalizedSlotRule(GossipValidationHelper gossipValidationHelper)
    implements StatelessValidationRule {

  private static final Logger LOG = LogManager.getLogger();

  /*
   * [IGNORE] The block is from a slot greater than the latest finalized slot -- i.e. validate that
   * signed_beacon_block.message.slot > compute_start_slot_at_epoch(store.finalized_checkpoint.epoch)
   * (a client MAY choose to validate and store such blocks for additional purposes
   * -- e.g. slashing detection, archive nodes, etc).
   */
  @Override
  public Optional<InternalValidationResult> validate(final SignedBeaconBlock block) {
    if (gossipValidationHelper.isSlotFinalized(block.getSlot())) {
      LOG.trace("BlockValidator: Block is too old. It will be dropped");
      return Optional.of(InternalValidationResult.IGNORE);
    }
    return Optional.empty();
  }
}
