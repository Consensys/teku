/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.versions.merge.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.PayloadAttributes;
import tech.pegasys.teku.storage.client.RecentChainData;

public class PayloadAttributesCalculator {
  private static final long MAX_PROPOSER_SEEN_EPOCHS = 2;

  private final Spec spec;
  private final EventThread eventThread;
  private final RecentChainData recentChainData;
  private final Map<UInt64, ProposerInfo> proposerInfoByValidatorIndex = new HashMap<>();

  public PayloadAttributesCalculator(
      final Spec spec, final EventThread eventThread, final RecentChainData recentChainData) {
    this.spec = spec;
    this.eventThread = eventThread;
    this.recentChainData = recentChainData;
  }

  public void updateProposers(
      final Collection<BeaconPreparableProposer> proposers, final UInt64 currentSlot) {
    // Remove expired validators
    proposerInfoByValidatorIndex.values().removeIf(info -> info.hasExpired(currentSlot));

    // Update validators
    final UInt64 expirySlot =
        currentSlot.plus(spec.getSlotsPerEpoch(currentSlot) * MAX_PROPOSER_SEEN_EPOCHS);
    for (BeaconPreparableProposer proposer : proposers) {
      proposerInfoByValidatorIndex.put(
          proposer.getValidatorIndex(), new ProposerInfo(expirySlot, proposer.getFeeRecipient()));
    }
  }

  public SafeFuture<Optional<PayloadAttributes>> calculatePayloadAttributes(
      final UInt64 blockSlot,
      final boolean inSync,
      final ForkChoiceUpdateData forkChoiceUpdateData) {
    eventThread.checkOnEventThread();
    if (!inSync) {
      // We don't produce blocks while syncing so don't bother preparing the payload
      return SafeFuture.completedFuture(Optional.empty());
    }
    if (!forkChoiceUpdateData.hasHeadBlockHash()) {
      // No forkChoiceUpdated message will be sent so no point calculating payload attributes
      return SafeFuture.completedFuture(Optional.empty());
    }
    if (!recentChainData.isJustifiedCheckpointFullyValidated()) {
      // If we've optimistically synced far enough that our justified checkpoint is optimistic,
      // stop producing blocks because the majority of validators see the optimistic chain as valid.
      return SafeFuture.completedFuture(Optional.empty());
    }
    final UInt64 epoch = spec.computeEpochAtSlot(blockSlot);
    return getStateInEpoch(epoch)
        .thenApplyAsync(
            maybeState -> calculatePayloadAttributes(blockSlot, epoch, maybeState), eventThread);
  }

  private Optional<PayloadAttributes> calculatePayloadAttributes(
      final UInt64 blockSlot, final UInt64 epoch, final Optional<BeaconState> maybeState) {
    eventThread.checkOnEventThread();
    if (maybeState.isEmpty()) {
      return Optional.empty();
    }
    final BeaconState state = maybeState.get();
    final UInt64 proposerIndex = UInt64.valueOf(spec.getBeaconProposerIndex(state, blockSlot));
    final ProposerInfo proposerInfo = proposerInfoByValidatorIndex.get(proposerIndex);
    if (proposerInfo == null) {
      // Proposer is not one of our validators. No need to propose a block.
      return Optional.empty();
    }
    final UInt64 timestamp = spec.computeTimeAtSlot(state, blockSlot);
    final Bytes32 random = spec.getRandaoMix(state, epoch);
    return Optional.of(new PayloadAttributes(timestamp, random, proposerInfo.feeRecipient));
  }

  private SafeFuture<Optional<BeaconState>> getStateInEpoch(final UInt64 requiredEpoch) {
    final Optional<StateAndBlockSummary> chainHead = recentChainData.getChainHead();
    if (chainHead.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    final StateAndBlockSummary head = chainHead.get();
    if (spec.computeEpochAtSlot(head.getSlot()).equals(requiredEpoch)) {
      return SafeFuture.completedFuture(Optional.of(head.getState()));
    } else {
      return recentChainData.retrieveStateAtSlot(
          new SlotAndBlockRoot(spec.computeStartSlotAtEpoch(requiredEpoch), head.getRoot()));
    }
  }
}
