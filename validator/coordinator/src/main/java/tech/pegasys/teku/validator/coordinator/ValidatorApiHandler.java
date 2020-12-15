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

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.getCurrentDutyDependentRoot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.getPreviousDutyDependentRoot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_committee_count_per_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.infrastructure.logging.LogFormatter.formatBlock;
import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;
import static tech.pegasys.teku.util.config.Constants.GENESIS_SLOT;
import static tech.pegasys.teku.util.config.Constants.MAX_VALIDATORS_PER_COMMITTEE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.CommitteeAssignmentUtil;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;
import tech.pegasys.teku.core.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
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

  private final ChainDataProvider chainDataProvider;
  private final CombinedChainDataClient combinedChainDataClient;
  private final SyncStateProvider syncStateProvider;
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
      final ChainDataProvider chainDataProvider,
      final CombinedChainDataClient combinedChainDataClient,
      final SyncStateProvider syncStateProvider,
      final BlockFactory blockFactory,
      final BlockImportChannel blockImportChannel,
      final AggregatingAttestationPool attestationPool,
      final AttestationManager attestationManager,
      final AttestationTopicSubscriber attestationTopicSubscriber,
      final ActiveValidatorTracker activeValidatorTracker,
      final EventBus eventBus,
      final DutyMetrics dutyMetrics,
      final PerformanceTracker performanceTracker) {
    this.chainDataProvider = chainDataProvider;
    this.combinedChainDataClient = combinedChainDataClient;
    this.syncStateProvider = syncStateProvider;
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
    return SafeFuture.of(
        () ->
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
                .orElseThrow(() -> new IllegalStateException("Head state is not yet available")));
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
        .getStateAtSlotExact(slot)
        .thenApply(
            optionalState ->
                optionalState.map(
                    state -> getAttesterDutiesFromIndexesAndState(state, epoch, validatorIndexes)));
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
    LOG.trace("Retrieving proposer duties from epoch {}", epoch);
    return combinedChainDataClient
        .getStateAtSlotExact(compute_start_slot_at_epoch(epoch))
        .thenApply(
            optionalState ->
                optionalState.map(state -> getProposerDutiesFromIndexesAndState(state, epoch)));
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
      List<BLSPublicKey> validatorIdentifiers) {
    return isSyncActive()
        ? SafeFuture.completedFuture(Optional.empty())
        : chainDataProvider
            .getStateValidators(
                "head",
                validatorIdentifiers.stream().map(BLSPublicKey::toString).collect(toList()),
                new HashSet<>())
            .thenApply(
                (maybeList) ->
                    maybeList.map(
                        list ->
                            list.stream()
                                .collect(
                                    toMap(
                                        ValidatorResponse::getPublicKey,
                                        ValidatorResponse::getStatus))));
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      final UInt64 slot, final BLSSignature randaoReveal, final Optional<Bytes32> graffiti) {
    LOG.trace("Creating unsigned block for slot {}", slot);
    performanceTracker.reportBlockProductionAttempt(compute_epoch_at_slot(slot));
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    return combinedChainDataClient
        .getStateAtSlotExact(slot.minus(UInt64.ONE))
        .thenCompose(
            maybePreState ->
                combinedChainDataClient
                    .getStateAtSlotExact(slot)
                    .thenApplyChecked(
                        maybeBlockSlotState ->
                            createBlock(
                                slot, randaoReveal, graffiti, maybePreState, maybeBlockSlotState)));
  }

  private Optional<BeaconBlock> createBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final Optional<BeaconState> maybePreState,
      final Optional<BeaconState> maybeBlockSlotState)
      throws EpochProcessingException, SlotProcessingException, StateTransitionException {
    if (maybePreState.isEmpty()) {
      return Optional.empty();
    }
    LOG.trace(
        "Delegating to block factory. Has block slot state? {}", maybeBlockSlotState.isPresent());
    return Optional.of(
        blockFactory.createUnsignedBlock(
            maybePreState.get(), maybeBlockSlotState, slot, randaoReveal, graffiti));
  }

  @Override
  public SafeFuture<Optional<Attestation>> createUnsignedAttestation(
      final UInt64 slot, final int committeeIndex) {
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }

    final UInt64 epoch = compute_epoch_at_slot(slot);
    final UInt64 minQuerySlot = compute_start_slot_at_epoch(epoch);

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
                // forward to the current epoch. Ensures we have the latest justified checkpoint
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
          // The old subscription API can't provide the validator ID so until it can be removed,
          // don't track validators from those calls - they should use the old API to subscribe to
          // persistent subnets.
          if (request.getValidatorIndex() != UKNOWN_VALIDATOR_ID) {
            activeValidatorTracker.onCommitteeSubscriptionRequest(
                request.getValidatorIndex(), request.getSlot());
          }

          if (request.isAggregator()) {
            attestationTopicSubscriber.subscribeToCommitteeForAggregation(
                request.getCommitteeIndex(), request.getCommitteesAtSlot(), request.getSlot());
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
            result -> {
              result.ifInvalid(
                  reason ->
                      VALIDATOR_LOGGER.producedInvalidAggregate(
                          aggregateAndProof.getMessage().getAggregate().getData().getSlot(),
                          reason));
            },
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
    return new ProposerDuties(getCurrentDutyDependentRoot(state), result);
  }

  private AttesterDuties getAttesterDutiesFromIndexesAndState(
      final BeaconState state, final UInt64 epoch, final Collection<Integer> validatorIndexes) {
    final Bytes32 dependentRoot =
        epoch.isGreaterThan(get_current_epoch(state))
            ? getCurrentDutyDependentRoot(state)
            : getPreviousDutyDependentRoot(state);
    return new AttesterDuties(
        dependentRoot,
        validatorIndexes.stream()
            .map(index -> createAttesterDuties(state, epoch, index))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(toList()));
  }

  private Optional<AttesterDuty> createAttesterDuties(
      final BeaconState state, final UInt64 epoch, final Integer validatorIndex) {

    return combine(
        ValidatorsUtil.getValidatorPubKey(state, UInt64.valueOf(validatorIndex)),
        CommitteeAssignmentUtil.get_committee_assignment(state, epoch, validatorIndex),
        (pkey, committeeAssignment) -> {
          final UInt64 committeeCountPerSlot = get_committee_count_per_slot(state, epoch);
          return new AttesterDuty(
              pkey,
              validatorIndex,
              committeeAssignment.getCommittee().size(),
              committeeAssignment.getCommitteeIndex().intValue(),
              committeeCountPerSlot.intValue(),
              committeeAssignment.getCommittee().indexOf(validatorIndex),
              committeeAssignment.getSlot());
        });
  }

  private static <A, B, R> Optional<R> combine(
      Optional<A> a, Optional<B> b, BiFunction<A, B, R> fun) {
    if (a.isEmpty() || b.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(fun.apply(a.get(), b.get()));
  }

  private Map<UInt64, BLSPublicKey> getProposalSlotsForEpoch(
      final BeaconState state, final UInt64 epoch) {
    final UInt64 epochStartSlot = compute_start_slot_at_epoch(epoch);
    final UInt64 startSlot = epochStartSlot.max(UInt64.valueOf(GENESIS_SLOT + 1));
    final UInt64 endSlot = epochStartSlot.plus(Constants.SLOTS_PER_EPOCH);
    final Map<UInt64, BLSPublicKey> proposerSlots = new HashMap<>();
    for (UInt64 slot = startSlot; slot.compareTo(endSlot) < 0; slot = slot.plus(UInt64.ONE)) {
      final int proposerIndex = get_beacon_proposer_index(state, slot);
      final BLSPublicKey publicKey =
          ValidatorsUtil.getValidatorPubKey(state, UInt64.valueOf(proposerIndex)).orElseThrow();
      proposerSlots.put(slot, publicKey);
    }
    return proposerSlots;
  }
}
