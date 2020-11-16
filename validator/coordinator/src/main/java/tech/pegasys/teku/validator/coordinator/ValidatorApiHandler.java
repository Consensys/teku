/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.validator.coordinator;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.getCurrentTargetRoot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.getPreviousTargetRoot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_committee_count_per_slot;
import static tech.pegasys.teku.infrastructure.logging.LogFormatter.formatBlock;
import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;
import static tech.pegasys.teku.util.config.Constants.GENESIS_SLOT;
import static tech.pegasys.teku.util.config.Constants.MAX_VALIDATORS_PER_COMMITTEE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.CommitteeAssignmentUtil;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;
import tech.pegasys.teku.core.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.genesis.GenesisData;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.util.AttestationUtil;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.datastructures.util.ValidatorsUtil;
import tech.pegasys.teku.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingFunction;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.events.block.ProposedBlockEvent;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.sync.events.SyncState;
import tech.pegasys.teku.sync.events.SyncStateProvider;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.AttesterDuty;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.ProposerDuty;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;

public class ValidatorApiHandler implements ValidatorApiChannel {
  private static final Logger LOG = LogManager.getLogger();
  /**
   * Number of epochs ahead of the current head that duties can be requested. This provides some
   * tolerance for validator clients clocks being slightly ahead while still limiting the number of
   * empty slots that may need to be processed when calculating duties.
   */
  private static final int DUTY_EPOCH_TOLERANCE = 1;

  private final CombinedChainDataClient combinedChainDataClient;
  private final SyncStateProvider syncStateProvider;
  private final StateTransition stateTransition;
  private final BlockFactory blockFactory;
  private final BlockImportChannel blockImportChannel;
  private final AggregatingAttestationPool attestationPool;
  private final AttestationManager attestationManager;
  private final AttestationTopicSubscriber attestationTopicSubscriber;
  private final ActiveValidatorTracker activeValidatorTracker;
  private final EventBus eventBus;
  private final DutyMetrics dutyMetrics;
  private final PerformanceTracker performanceTracker;

  public ValidatorApiHandler(
      final CombinedChainDataClient combinedChainDataClient,
      final SyncStateProvider syncStateProvider,
      final StateTransition stateTransition,
      final BlockFactory blockFactory,
      final BlockImportChannel blockImportChannel,
      final AggregatingAttestationPool attestationPool,
      final AttestationManager attestationManager,
      final AttestationTopicSubscriber attestationTopicSubscriber,
      final ActiveValidatorTracker activeValidatorTracker,
      final EventBus eventBus,
      final DutyMetrics dutyMetrics,
      final PerformanceTracker performanceTracker) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.syncStateProvider = syncStateProvider;
    this.stateTransition = stateTransition;
    this.blockFactory = blockFactory;
    this.blockImportChannel = blockImportChannel;
    this.attestationPool = attestationPool;
    this.attestationManager = attestationManager;
    this.attestationTopicSubscriber = attestationTopicSubscriber;
    this.activeValidatorTracker = activeValidatorTracker;
    this.eventBus = eventBus;
    this.dutyMetrics = dutyMetrics;
    this.performanceTracker = performanceTracker;
  }

  @Override
  public SafeFuture<Optional<Fork>> getFork() {
    return SafeFuture.completedFuture(
        combinedChainDataClient.getBestState().map(BeaconState::getFork));
  }

  @Override
  public SafeFuture<Optional<GenesisData>> getGenesisData() {
    return SafeFuture.completedFuture(combinedChainDataClient.getGenesisData());
  }

  @Override
  public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndices(
      final List<BLSPublicKey> publicKeys) {
    return SafeFuture.completedFuture(
        combinedChainDataClient
            .getBestState()
            .map(
                state -> {
                  final Map<BLSPublicKey, Integer> results = new HashMap<>();
                  publicKeys.forEach(
                      publicKey ->
                          ValidatorsUtil.getValidatorIndex(state, publicKey)
                              .ifPresent(index -> results.put(publicKey, index)));
                  return results;
                })
            .orElse(emptyMap()));
  }

  @Override
  public SafeFuture<Optional<AttesterDuties>> getAttestationDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndexes) {
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    if (epoch.isGreaterThan(
        combinedChainDataClient
            .getCurrentEpoch()
            .plus(Constants.MIN_SEED_LOOKAHEAD + DUTY_EPOCH_TOLERANCE))) {
      return SafeFuture.failedFuture(
          new IllegalArgumentException(
              String.format(
                  "Attestation duties were requested %s epochs ahead, only 1 epoch in future is supported.",
                  epoch.minus(combinedChainDataClient.getCurrentEpoch()).toString())));
    }
    final UInt64 slot = CommitteeUtil.getEarliestQueryableSlotForTargetEpoch(epoch);
    LOG.trace("Retrieving attestation duties from epoch {} using state at slot {}", epoch, slot);
    return combinedChainDataClient
        .getLatestStateAtSlot(slot)
        .thenApply(
            optionalState ->
                optionalState
                    .map(state -> processSlots(state, slot))
                    .map(
                        state ->
                            getAttesterDutiesFromIndexesAndState(state, epoch, validatorIndexes)));
  }

  @Override
  public SafeFuture<Optional<ProposerDuties>> getProposerDuties(final UInt64 epoch) {
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    if (epoch.isGreaterThan(combinedChainDataClient.getCurrentEpoch().plus(DUTY_EPOCH_TOLERANCE))) {
      return SafeFuture.failedFuture(
          new IllegalArgumentException(
              String.format(
                  "Proposer duties were requested for a future epoch (current: %s, requested: %s).",
                  combinedChainDataClient.getCurrentEpoch().toString(), epoch.toString())));
    }
    final UInt64 slot = compute_start_slot_at_epoch(epoch);
    LOG.trace("Retrieving proposer duties from epoch {} using state at slot {}", epoch, slot);
    return combinedChainDataClient
        .getLatestStateAtSlot(slot)
        .thenApply(
            optionalState ->
                optionalState
                    .map(state -> processSlots(state, slot))
                    .map(state -> getProposerDutiesFromIndexesAndState(state, epoch)));
  }

  private BeaconState processSlots(final BeaconState startingState, final UInt64 targetSlot) {
    if (startingState.getSlot().compareTo(targetSlot) >= 0) {
      return startingState;
    }
    try {
      return stateTransition.process_slots(startingState, targetSlot);
    } catch (SlotProcessingException | EpochProcessingException e) {
      throw new IllegalStateException("Unable to process slots", e);
    }
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      final UInt64 slot, final BLSSignature randaoReveal, final Optional<Bytes32> graffiti) {
    performanceTracker.reportBlockProductionAttempt(compute_epoch_at_slot(slot));
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    return createFromBlockAndState(
        slot.minus(UInt64.ONE),
        blockAndState ->
            blockFactory.createUnsignedBlock(
                blockAndState.getState(), blockAndState.getBlock(), slot, randaoReveal, graffiti));
  }

  private <T> SafeFuture<Optional<T>> createFromBlockAndState(
      final UInt64 maximumSlot, final ExceptionThrowingFunction<BeaconBlockAndState, T> creator) {

    return combinedChainDataClient
        .getBlockAndStateInEffectAtSlot(maximumSlot)
        .thenApplyChecked(
            maybeBlockAndState -> {
              if (maybeBlockAndState.isEmpty()) {
                return Optional.empty();
              }
              return Optional.of(creator.apply(maybeBlockAndState.get()));
            });
  }

  @Override
  public SafeFuture<Optional<Attestation>> createUnsignedAttestation(
      final UInt64 slot, final int committeeIndex) {
    performanceTracker.reportAttestationProductionAttempt(compute_epoch_at_slot(slot));
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }

    final UInt64 minQuerySlot = CommitteeUtil.getEarliestQueryableSlotForTargetSlot(slot);

    return combinedChainDataClient
        .getSignedBlockAndStateInEffectAtSlot(slot)
        .thenCompose(
            maybeBlockAndState -> {
              if (maybeBlockAndState.isEmpty()) {
                return SafeFuture.completedFuture(Optional.empty());
              }
              final SignedBlockAndState blockAndState = maybeBlockAndState.get();
              final BeaconBlock block = blockAndState.getBlock().getMessage();
              if (blockAndState.getSlot().compareTo(minQuerySlot) < 0) {
                // The current effective block is too far in the past - so roll the state
                // forward to the minimum epoch
                final UInt64 epoch = compute_epoch_at_slot(minQuerySlot);
                return combinedChainDataClient
                    .getCheckpointState(epoch, blockAndState)
                    .thenApply(
                        checkpointState ->
                            Optional.of(
                                createAttestation(
                                    block, checkpointState.getState(), slot, committeeIndex)));
              } else {
                final Attestation attestation =
                    createAttestation(block, blockAndState.getState(), slot, committeeIndex);
                return SafeFuture.completedFuture(Optional.of(attestation));
              }
            });
  }

  @Override
  public SafeFuture<Optional<AttestationData>> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return createUnsignedAttestation(slot, committeeIndex)
        .thenApply(maybeAttestation -> maybeAttestation.map(Attestation::getData));
  }

  private Attestation createAttestation(
      final BeaconBlock block,
      final BeaconState state,
      final UInt64 slot,
      final int committeeIndex) {
    final int committeeCount =
        get_committee_count_per_slot(state, compute_epoch_at_slot(slot)).intValue();

    if (committeeIndex < 0 || committeeIndex >= committeeCount) {
      throw new IllegalArgumentException(
          "Invalid committee index "
              + committeeIndex
              + " - expected between 0 and "
              + (committeeCount - 1));
    }
    final UInt64 committeeIndexUnsigned = UInt64.valueOf(committeeIndex);
    final AttestationData attestationData =
        AttestationUtil.getGenericAttestationData(slot, state, block, committeeIndexUnsigned);
    final List<Integer> committee =
        CommitteeUtil.get_beacon_committee(state, slot, committeeIndexUnsigned);

    final Bitlist aggregationBits = new Bitlist(committee.size(), MAX_VALIDATORS_PER_COMMITTEE);
    return new Attestation(aggregationBits, attestationData, BLSSignature.empty());
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    return SafeFuture.completedFuture(
        attestationPool
            .createAggregateFor(attestationHashTreeRoot)
            .filter(attestation -> attestation.getData().getSlot().equals(slot))
            .map(ValidateableAttestation::getAttestation));
  }

  @Override
  public void subscribeToBeaconCommittee(final List<CommitteeSubscriptionRequest> requests) {
    requests.forEach(
        request -> {
          if (request.isAggregator()) {
            attestationTopicSubscriber.subscribeToCommitteeForAggregation(
                request.getCommitteeIndex(), request.getCommitteesAtSlot(), request.getSlot());

            // The old subscription API can't provide the validator ID so until it can be removed,
            // don't track validators from those calls - they should use the old API to subscribe to
            // persistent subnets.
            if (request.getValidatorIndex() != UKNOWN_VALIDATOR_ID) {
              activeValidatorTracker.onCommitteeSubscriptionRequest(
                  request.getValidatorIndex(), request.getSlot());
            }
          }
        });
  }

  @Override
  public void subscribeToPersistentSubnets(Set<SubnetSubscription> subnetSubscriptions) {
    attestationTopicSubscriber.subscribeToPersistentSubnets(subnetSubscriptions);
  }

  @Override
  public void sendSignedAttestation(
      final Attestation attestation, final Optional<Integer> expectedValidatorIndex) {
    attestationManager
        .onAttestation(ValidateableAttestation.fromValidator(attestation))
        .finish(
            result -> {
              result.ifInvalid(
                  reason ->
                      VALIDATOR_LOGGER.producedInvalidAttestation(
                          attestation.getData().getSlot(), reason));
              dutyMetrics.onAttestationPublished(attestation.getData().getSlot());
              performanceTracker.saveProducedAttestation(attestation);
            },
            err ->
                LOG.error(
                    "Failed to send signed attestation for slot {}, block {}",
                    attestation.getData().getSlot(),
                    attestation.getData().getBeacon_block_root()));
  }

  @Override
  public void sendSignedAttestation(final Attestation attestation) {
    sendSignedAttestation(attestation, Optional.empty());
  }

  @Override
  public void sendAggregateAndProof(final SignedAggregateAndProof aggregateAndProof) {
    attestationManager
        .onAttestation(ValidateableAttestation.aggregateFromValidator(aggregateAndProof))
        .finish(
            result ->
                result.ifInvalid(
                    reason ->
                        VALIDATOR_LOGGER.producedInvalidAggregate(
                            aggregateAndProof.getMessage().getAggregate().getData().getSlot(),
                            reason)),
            err ->
                LOG.error(
                    "Failed to send aggregate for slot {}",
                    aggregateAndProof.getMessage().getAggregate().getData().getSlot()));
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(final SignedBeaconBlock block) {
    performanceTracker.saveProducedBlock(block);
    eventBus.post(new ProposedBlockEvent(block));
    return blockImportChannel
        .importBlock(block)
        .thenApply(
            result -> {
              if (result.isSuccessful()) {
                LOG.trace(
                    "Successfully imported proposed block: {}",
                    () -> formatBlock(block.getSlot(), block.getRoot()));
                return SendSignedBlockResult.success(block.getRoot());
              } else if (result.getFailureReason() == FailureReason.BLOCK_IS_FROM_FUTURE) {
                LOG.debug(
                    "Delayed processing proposed block {} because it is from the future",
                    formatBlock(block.getSlot(), block.getRoot()));
                return SendSignedBlockResult.notImported(result.getFailureReason().name());
              } else {
                LOG.error(
                    "Failed to import proposed block due to "
                        + result.getFailureReason()
                        + ": "
                        + formatBlock(block.getSlot(), block.getRoot()),
                    result.getFailureCause().orElse(null));
                return SendSignedBlockResult.notImported(result.getFailureReason().name());
              }
            });
  }

  @VisibleForTesting
  boolean isSyncActive() {
    final SyncState syncState = syncStateProvider.getCurrentSyncState();
    return syncState.isStartingUp() || (syncState.isSyncing() && headBlockIsTooFarBehind());
  }

  private boolean headBlockIsTooFarBehind() {
    final UInt64 currentEpoch = combinedChainDataClient.getCurrentEpoch();
    final UInt64 headEpoch = combinedChainDataClient.getHeadEpoch();
    return headEpoch.plus(1).isLessThan(currentEpoch);
  }

  private ProposerDuties getProposerDutiesFromIndexesAndState(
      final BeaconState state, final UInt64 epoch) {
    final List<ProposerDuty> result = new ArrayList<>();
    getProposalSlotsForEpoch(state, epoch)
        .forEach(
            (slot, publicKey) -> {
              Optional<Integer> maybeIndex = ValidatorsUtil.getValidatorIndex(state, publicKey);
              if (maybeIndex.isEmpty()) {
                throw new IllegalStateException(
                    String.format(
                        "Assigned public key %s could not be found at epoch %s",
                        publicKey.toString(), epoch.toString()));
              }
              result.add(new ProposerDuty(publicKey, maybeIndex.get(), slot));
            });
    return new ProposerDuties(getCurrentTargetRoot(state), result);
  }

  private AttesterDuties getAttesterDutiesFromIndexesAndState(
      final BeaconState state, final UInt64 epoch, final Collection<Integer> validatorIndexes) {
    final List<AttesterDuty> duties =
        validatorIndexes.stream()
            .map(index -> createAttesterDuties(state, epoch, index))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(toList());
    return new AttesterDuties(getPreviousTargetRoot(state), duties);
  }

  private Optional<AttesterDuty> createAttesterDuties(
      final BeaconState state, final UInt64 epoch, final Integer validatorIndex) {
    try {
      final BLSPublicKey pkey = state.getValidators().get(validatorIndex).getPubkey();
      final UInt64 committeeCountPerSlot = get_committee_count_per_slot(state, epoch);
      return CommitteeAssignmentUtil.get_committee_assignment(state, epoch, validatorIndex)
          .map(
              committeeAssignment ->
                  new AttesterDuty(
                      pkey,
                      validatorIndex,
                      committeeAssignment.getCommittee().size(),
                      committeeAssignment.getCommitteeIndex().intValue(),
                      committeeCountPerSlot.intValue(),
                      committeeAssignment.getCommittee().indexOf(validatorIndex),
                      committeeAssignment.getSlot()));
    } catch (IndexOutOfBoundsException ex) {
      LOG.debug(ex);
      return Optional.empty();
    }
  }

  private Map<UInt64, BLSPublicKey> getProposalSlotsForEpoch(
      final BeaconState state, final UInt64 epoch) {
    final UInt64 epochStartSlot = compute_start_slot_at_epoch(epoch);
    final UInt64 startSlot = epochStartSlot.max(UInt64.valueOf(GENESIS_SLOT + 1));
    final UInt64 endSlot = epochStartSlot.plus(Constants.SLOTS_PER_EPOCH);
    final Map<UInt64, BLSPublicKey> proposerSlots = new HashMap<>();
    for (UInt64 slot = startSlot; slot.compareTo(endSlot) < 0; slot = slot.plus(UInt64.ONE)) {
      final int proposerIndex = get_beacon_proposer_index(state, slot);
      final BLSPublicKey publicKey = state.getValidators().get(proposerIndex).getPubkey();
      proposerSlots.put(slot, publicKey);
    }
    return proposerSlots;
  }
}
