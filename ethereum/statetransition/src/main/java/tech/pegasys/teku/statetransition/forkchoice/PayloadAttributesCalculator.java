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

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.PayloadAttributes;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

public class PayloadAttributesCalculator {
  private static final long MAX_PROPOSER_SEEN_EPOCHS = 2;

  private final Spec spec;
  private final EventThread eventThread;
  private final RecentChainData recentChainData;
  private final Map<UInt64, ProposerInfo> proposerInfoByValidatorIndex = new ConcurrentHashMap<>();
  private final Optional<? extends Bytes20> proposerDefaultFeeRecipient;

  public PayloadAttributesCalculator(
      final Spec spec,
      final EventThread eventThread,
      final RecentChainData recentChainData,
      final Optional<? extends Bytes20> proposerDefaultFeeRecipient) {
    this.spec = spec;
    this.eventThread = eventThread;
    this.recentChainData = recentChainData;
    this.proposerDefaultFeeRecipient = proposerDefaultFeeRecipient;
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
      final ForkChoiceUpdateData forkChoiceUpdateData,
      final boolean mandatoryPayloadAttributes) {
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
            maybeState ->
                calculatePayloadAttributes(
                    blockSlot, epoch, maybeState, mandatoryPayloadAttributes),
            eventThread);
  }

  private Optional<PayloadAttributes> calculatePayloadAttributes(
      final UInt64 blockSlot,
      final UInt64 epoch,
      final Optional<BeaconState> maybeState,
      final boolean mandatoryPayloadAttributes) {
    eventThread.checkOnEventThread();
    if (maybeState.isEmpty()) {
      return Optional.empty();
    }
    final BeaconState state = maybeState.get();
    final UInt64 proposerIndex = UInt64.valueOf(spec.getBeaconProposerIndex(state, blockSlot));
    final ProposerInfo proposerInfo = proposerInfoByValidatorIndex.get(proposerIndex);
    if (proposerInfo == null && !mandatoryPayloadAttributes) {
      // Proposer is not one of our validators. No need to propose a block.
      return Optional.empty();
    }
    final UInt64 timestamp = spec.computeTimeAtSlot(state, blockSlot);
    final Bytes32 random = spec.getRandaoMix(state, epoch);
    return Optional.of(
        new PayloadAttributes(timestamp, random, getFeeRecipient(proposerInfo, blockSlot)));
  }

  // this function MUST return a fee recipient.
  private Bytes20 getFeeRecipient(final ProposerInfo proposerInfo, final UInt64 blockSlot) {
    if (proposerInfo != null) {
      return proposerInfo.feeRecipient;
    }
    if (proposerDefaultFeeRecipient.isPresent()) {
      VALIDATOR_LOGGER.executionPayloadPreparedUsingBeaconDefaultFeeRecipient(blockSlot);
      return proposerDefaultFeeRecipient.get();
    }
    throw new IllegalStateException(
        "Unable to determine proposer fee recipient address for slot " + blockSlot);
  }

  private SafeFuture<Optional<BeaconState>> getStateInEpoch(final UInt64 requiredEpoch) {
    final Optional<ChainHead> chainHead = recentChainData.getChainHead();
    if (chainHead.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    final ChainHead head = chainHead.get();
    if (spec.computeEpochAtSlot(head.getSlot()).equals(requiredEpoch)) {
      return head.getState().thenApply(Optional::of);
    } else {
      return recentChainData.retrieveStateAtSlot(
          new SlotAndBlockRoot(spec.computeStartSlotAtEpoch(requiredEpoch), head.getRoot()));
    }
  }

  public List<Map<String, Object>> getData() {
    return proposerInfoByValidatorIndex.entrySet().stream()
        .map(
            proposerInfoEntry ->
                ImmutableMap.<String, Object>builder()
                    // changing the following attributes require a change to
                    // tech.pegasys.teku.api.response.v1.teku.ProposerInfoSchema
                    .put("proposer_index", proposerInfoEntry.getKey())
                    .put("fee_recipient", proposerInfoEntry.getValue().feeRecipient)
                    .put("expiry_slot", proposerInfoEntry.getValue().expirySlot)
                    .build())
        .collect(Collectors.toList());
  }

  public boolean isProposerDefaultFeeRecipientDefined() {
    return proposerDefaultFeeRecipient.isPresent();
  }
}
