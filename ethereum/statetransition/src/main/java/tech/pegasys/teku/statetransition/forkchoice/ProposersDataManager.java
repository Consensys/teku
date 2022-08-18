/*
 * Copyright ConsenSys Software Inc., 2022
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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ProposersDataManager implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  private static final long PROPOSER_PREPARATION_EXPIRATION_EPOCHS = 2;
  private static final long VALIDATOR_REGISTRATION_EXPIRATION_EPOCHS = 2;

  private final Spec spec;
  private final EventThread eventThread;
  private final RecentChainData recentChainData;
  private final ExecutionLayerChannel executionLayerChannel;
  private final Map<UInt64, PreparedProposerInfo> preparedProposerInfoByValidatorIndex =
      new ConcurrentHashMap<>();
  private final Map<UInt64, RegisteredValidatorInfo> validatorRegistrationInfoByValidatorIndex =
      new ConcurrentHashMap<>();
  private final Optional<Eth1Address> proposerDefaultFeeRecipient;

  private final Subscribers<ProposersDataManagerSubscriber> subscribers = Subscribers.create(true);

  public ProposersDataManager(
      final EventThread eventThread,
      final Spec spec,
      final MetricsSystem metricsSystem,
      final ExecutionLayerChannel executionLayerChannel,
      final RecentChainData recentChainData,
      final Optional<Eth1Address> proposerDefaultFeeRecipient) {
    final LabelledGauge labelledGauge =
        metricsSystem.createLabelledGauge(
            TekuMetricCategory.BEACON,
            "proposers_data_total",
            "Total number proposers/validators under management",
            "type");

    labelledGauge.labels(preparedProposerInfoByValidatorIndex::size, "prepared_proposers");
    labelledGauge.labels(validatorRegistrationInfoByValidatorIndex::size, "registered_validators");

    this.spec = spec;
    this.eventThread = eventThread;
    this.recentChainData = recentChainData;
    this.proposerDefaultFeeRecipient = proposerDefaultFeeRecipient;
    this.executionLayerChannel = executionLayerChannel;
  }

  public long subscribeToProposersDataChanges(ProposersDataManagerSubscriber subscriber) {
    return subscribers.subscribe(subscriber);
  }

  public boolean unsubscribeToProposersDataChanges(long subscriberId) {
    return subscribers.unsubscribe(subscriberId);
  }

  @Override
  public void onSlot(UInt64 slot) {
    // do clean up at second slot of each epoch
    if (!slot.mod(spec.getSlotsPerEpoch(slot)).equals(UInt64.ONE)) {
      return;
    }

    // Remove expired prepared proposers
    preparedProposerInfoByValidatorIndex.values().removeIf(info -> info.hasExpired(slot));

    // Remove expired registered validators
    validatorRegistrationInfoByValidatorIndex.values().removeIf(info -> info.hasExpired(slot));
  }

  public void updatePreparedProposers(
      final Collection<BeaconPreparableProposer> preparedProposers, final UInt64 currentSlot) {
    updatePreparedProposerCache(preparedProposers, currentSlot);
    subscribers.deliver(ProposersDataManagerSubscriber::onPreparedProposersUpdated);
  }

  public SafeFuture<Void> updateValidatorRegistrations(
      final SszList<SignedValidatorRegistration> signedValidatorRegistrations,
      final UInt64 currentSlot) {
    return executionLayerChannel
        .builderRegisterValidators(signedValidatorRegistrations, currentSlot)
        .thenCompose(__ -> recentChainData.getBestState().orElseThrow())
        .thenAccept(
            headState ->
                updateValidatorRegistrationCache(
                    headState, signedValidatorRegistrations, currentSlot));
  }

  private void updatePreparedProposerCache(
      final Collection<BeaconPreparableProposer> preparedProposers, final UInt64 currentSlot) {
    final UInt64 expirySlot =
        currentSlot.plus(
            spec.getSlotsPerEpoch(currentSlot) * PROPOSER_PREPARATION_EXPIRATION_EPOCHS);
    preparedProposers.forEach(
        proposer ->
            preparedProposerInfoByValidatorIndex.put(
                proposer.getValidatorIndex(),
                new PreparedProposerInfo(expirySlot, proposer.getFeeRecipient())));
  }

  private void updateValidatorRegistrationCache(
      final BeaconState headState,
      final SszList<SignedValidatorRegistration> signedValidatorRegistrations,
      final UInt64 currentSlot) {
    final UInt64 expirySlot =
        currentSlot.plus(
            spec.getSlotsPerEpoch(currentSlot) * VALIDATOR_REGISTRATION_EXPIRATION_EPOCHS);

    signedValidatorRegistrations.forEach(
        signedValidatorRegistration ->
            spec.getValidatorIndex(
                    headState, signedValidatorRegistration.getMessage().getPublicKey())
                .ifPresentOrElse(
                    index ->
                        validatorRegistrationInfoByValidatorIndex.put(
                            UInt64.valueOf(index),
                            new RegisteredValidatorInfo(expirySlot, signedValidatorRegistration)),
                    () ->
                        LOG.warn(
                            "validator index not found for public key {}",
                            signedValidatorRegistration.getMessage().getPublicKey())));

    subscribers.deliver(ProposersDataManagerSubscriber::onValidatorRegistrationsUpdated);
  }

  public SafeFuture<Optional<PayloadBuildingAttributes>> calculatePayloadBuildingAttributes(
      final UInt64 blockSlot,
      final boolean inSync,
      final ForkChoiceUpdateData forkChoiceUpdateData,
      final boolean mandatory) {
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
                calculatePayloadBuildingAttributes(blockSlot, epoch, maybeState, mandatory),
            eventThread);
  }

  private Optional<PayloadBuildingAttributes> calculatePayloadBuildingAttributes(
      final UInt64 blockSlot,
      final UInt64 epoch,
      final Optional<BeaconState> maybeState,
      final boolean mandatory) {
    eventThread.checkOnEventThread();
    if (maybeState.isEmpty()) {
      return Optional.empty();
    }
    final BeaconState state = maybeState.get();
    final UInt64 proposerIndex = UInt64.valueOf(spec.getBeaconProposerIndex(state, blockSlot));
    final PreparedProposerInfo proposerInfo =
        preparedProposerInfoByValidatorIndex.get(proposerIndex);
    if (proposerInfo == null && !mandatory) {
      // Proposer is not one of our validators. No need to propose a block.
      return Optional.empty();
    }
    final UInt64 timestamp = spec.computeTimeAtSlot(state, blockSlot);
    final Bytes32 random = spec.getRandaoMix(state, epoch);
    final Optional<SignedValidatorRegistration> validatorRegistration =
        Optional.ofNullable(validatorRegistrationInfoByValidatorIndex.get(proposerIndex))
            .map(RegisteredValidatorInfo::getSignedValidatorRegistration);
    return Optional.of(
        new PayloadBuildingAttributes(
            timestamp, random, getFeeRecipient(proposerInfo, blockSlot), validatorRegistration));
  }

  // this function MUST return a fee recipient.
  private Eth1Address getFeeRecipient(
      final PreparedProposerInfo preparedProposerInfo, final UInt64 blockSlot) {
    if (preparedProposerInfo != null) {
      return preparedProposerInfo.getFeeRecipient();
    }
    if (proposerDefaultFeeRecipient.isPresent()) {
      VALIDATOR_LOGGER.executionPayloadPreparedUsingBeaconDefaultFeeRecipient(blockSlot);
      return proposerDefaultFeeRecipient.get();
    }
    VALIDATOR_LOGGER.executionPayloadPreparedUsingBurnAddressForFeeRecipient(blockSlot);
    return Eth1Address.ZERO;
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

  public Map<UInt64, PreparedProposerInfo> getPreparedProposerInfo() {
    return preparedProposerInfoByValidatorIndex;
  }

  public Map<UInt64, RegisteredValidatorInfo> getValidatorRegistrationInfo() {
    return validatorRegistrationInfoByValidatorIndex;
  }

  public boolean isProposerDefaultFeeRecipientDefined() {
    return proposerDefaultFeeRecipient.isPresent();
  }

  public interface ProposersDataManagerSubscriber {
    void onPreparedProposersUpdated();

    void onValidatorRegistrationsUpdated();
  }
}
