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

package tech.pegasys.teku.statetransition.validation.block;

import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.StatefulValidationRule;
import tech.pegasys.teku.statetransition.validation.StatelessValidationRule;

public class BlockGossipValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final GossipValidationHelper gossipValidationHelper;
  private final BlockGossipValidationPipelines blockGossipValidationPipelines;
  private final ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher;
  private final EquivocationChecker equivocationChecker;

  public BlockGossipValidator(
      final Spec spec,
      final GossipValidationHelper gossipValidationHelper,
      final ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher,
      final EquivocationChecker equivocationChecker) {
    this.spec = spec;
    this.gossipValidationHelper = gossipValidationHelper;
    this.receivedBlockEventsChannelPublisher = receivedBlockEventsChannelPublisher;
    this.equivocationChecker = equivocationChecker;
    this.blockGossipValidationPipelines =
        new BlockGossipValidationPipelines(spec, gossipValidationHelper, equivocationChecker);
  }

  public SafeFuture<InternalValidationResult> validate(
      final SignedBeaconBlock block, final boolean markAsReceived) {

    final SpecMilestone specMilestone =
        spec.getForkSchedule().getSpecMilestoneAtSlot(block.getSlot());

    // Execute stateless validation rules
    final List<StatelessValidationRule> statelessPipeline =
        blockGossipValidationPipelines.getStatelessPipelineFor(specMilestone);

    for (final StatelessValidationRule rule : statelessPipeline) {
      final Optional<InternalValidationResult> result = rule.validate(block);
      if (result.isPresent()) {
        return SafeFuture.completedFuture(result.get());
      }
    }

    final Optional<UInt64> maybeParentBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(block.getParentRoot());

    // Execute stateful validation rules
    return gossipValidationHelper
        .getParentStateInBlockEpoch(
            maybeParentBlockSlot.orElseThrow(), block.getParentRoot(), block.getSlot())
        .thenCompose(
            maybeParentState -> {
              if (maybeParentState.isEmpty()) {
                LOG.trace(
                    "Block was available but state wasn't. Must have been pruned by finalized.");
                return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
              }
              final BeaconState parentState = maybeParentState.get();
              final List<StatefulValidationRule> statefulPipeline =
                  blockGossipValidationPipelines.getStatefulPipelineFor(specMilestone);

              for (final StatefulValidationRule rule : statefulPipeline) {
                final Optional<InternalValidationResult> result = rule.validate(block, parentState);
                if (result.isPresent()) {
                  return SafeFuture.completedFuture(result.get());
                }
              }
              return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
            })
        .thenPeek(
            result -> {
              if (result.isAccept()) {
                if (markAsReceived) {
                  equivocationChecker.markBlockAsSeen(block);
                }
                receivedBlockEventsChannelPublisher.onBlockValidated(block);
              }
            });
  }

  public EquivocationChecker.EquivocationCheckResult performBlockEquivocationCheck(
      final boolean markAsReceived, final SignedBeaconBlock block) {
    final EquivocationChecker.EquivocationCheckResult equivocationCheckResult =
        equivocationChecker.check(block);
    if (markAsReceived
        && equivocationCheckResult.equals(
            EquivocationChecker.EquivocationCheckResult.FIRST_BLOCK_FOR_SLOT_PROPOSER)) {
      equivocationChecker.markBlockAsSeen(block);
    }
    return equivocationCheckResult;
  }
}
