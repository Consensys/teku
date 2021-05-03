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
import static tech.pegasys.teku.infrastructure.logging.LogFormatter.formatBlock;
import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.ssz.collections.SszBitlist;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeSignaturePool;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.sync.events.SyncStateProvider;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.AttesterDuty;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.ProposerDuty;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitCommitteeSignatureError;
import tech.pegasys.teku.validator.api.SubmitCommitteeSignaturesResult;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeDuty;
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
  private final BlockGossipChannel blockGossipChannel;
  private final AggregatingAttestationPool attestationPool;
  private final AttestationManager attestationManager;
  private final AttestationTopicSubscriber attestationTopicSubscriber;
  private final ActiveValidatorTracker activeValidatorTracker;
  private final DutyMetrics dutyMetrics;
  private final PerformanceTracker performanceTracker;
  private final Spec spec;
  private final ForkChoiceTrigger forkChoiceTrigger;
  private final SyncCommitteeSignaturePool syncCommitteeSignaturePool;

  public ValidatorApiHandler(
      final ChainDataProvider chainDataProvider,
      final CombinedChainDataClient combinedChainDataClient,
      final SyncStateProvider syncStateProvider,
      final BlockFactory blockFactory,
      final BlockImportChannel blockImportChannel,
      final BlockGossipChannel blockGossipChannel,
      final AggregatingAttestationPool attestationPool,
      final AttestationManager attestationManager,
      final AttestationTopicSubscriber attestationTopicSubscriber,
      final ActiveValidatorTracker activeValidatorTracker,
      final DutyMetrics dutyMetrics,
      final PerformanceTracker performanceTracker,
      final Spec spec,
      final ForkChoiceTrigger forkChoiceTrigger,
      final SyncCommitteeSignaturePool syncCommitteeSignaturePool) {
    this.chainDataProvider = chainDataProvider;
    this.combinedChainDataClient = combinedChainDataClient;
    this.syncStateProvider = syncStateProvider;
    this.blockFactory = blockFactory;
    this.blockImportChannel = blockImportChannel;
    this.blockGossipChannel = blockGossipChannel;
    this.attestationPool = attestationPool;
    this.attestationManager = attestationManager;
    this.attestationTopicSubscriber = attestationTopicSubscriber;
    this.activeValidatorTracker = activeValidatorTracker;
    this.dutyMetrics = dutyMetrics;
    this.performanceTracker = performanceTracker;
    this.spec = spec;
    this.forkChoiceTrigger = forkChoiceTrigger;
    this.syncCommitteeSignaturePool = syncCommitteeSignaturePool;
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
      final Collection<BLSPublicKey> publicKeys) {
    return SafeFuture.of(
        () ->
            combinedChainDataClient
                .getBestState()
                .map(
                    state -> {
                      final Map<BLSPublicKey, Integer> results = new HashMap<>();
                      publicKeys.forEach(
                          publicKey ->
                              spec.getValidatorIndex(state, publicKey)
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
            .plus(spec.getSpecConfig(epoch).getMinSeedLookahead() + DUTY_EPOCH_TOLERANCE))) {
      return SafeFuture.failedFuture(
          new IllegalArgumentException(
              String.format(
                  "Attestation duties were requested %s epochs ahead, only 1 epoch in future is supported.",
                  epoch.minus(combinedChainDataClient.getCurrentEpoch()).toString())));
    }
    final UInt64 slot =
        spec.atEpoch(epoch).getBeaconStateUtil().getEarliestQueryableSlotForTargetEpoch(epoch);
    LOG.trace("Retrieving attestation duties from epoch {} using state at slot {}", epoch, slot);
    return combinedChainDataClient
        .getStateAtSlotExact(slot)
        .thenApply(
            optionalState ->
                optionalState.map(
                    state -> getAttesterDutiesFromIndexesAndState(state, epoch, validatorIndexes)));
  }

  @Override
  public SafeFuture<Optional<SyncCommitteeDuties>> getSyncCommitteeDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    final SpecVersion specVersion = spec.atEpoch(epoch);

    return getStateForCommitteeDuties(specVersion, epoch)
        .thenApply(
            maybeState ->
                Optional.of(
                    getSyncCommitteeDutiesFromIndexesAndState(
                        maybeState, epoch, validatorIndices)));
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
        .getStateAtSlotExact(spec.computeStartSlotAtEpoch(epoch))
        .thenApply(
            optionalState ->
                optionalState.map(state -> getProposerDutiesFromIndexesAndState(state, epoch)));
  }

  @Override
  public SafeFuture<Optional<Map<BLSPublicKey, ValidatorStatus>>> getValidatorStatuses(
      Collection<BLSPublicKey> validatorIdentifiers) {
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
    performanceTracker.reportBlockProductionAttempt(spec.computeEpochAtSlot(slot));
    if (isSyncActive()) {
      return NodeSyncingException.failedFuture();
    }
    return forkChoiceTrigger
        .prepareForBlockProduction(slot)
        .thenCompose(
            __ -> {
              final SafeFuture<Optional<BeaconState>> preStateFuture =
                  combinedChainDataClient.getStateAtSlotExact(slot.decrement());
              final SafeFuture<Optional<BeaconState>> blockSlotStateFuture =
                  combinedChainDataClient.getStateAtSlotExact(slot);
              return preStateFuture.thenCompose(
                  preState ->
                      blockSlotStateFuture.thenApplyChecked(
                          blockSlotState ->
                              createBlock(slot, randaoReveal, graffiti, preState, blockSlotState)));
            });
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

    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    final UInt64 minQuerySlot = spec.computeStartSlotAtEpoch(epoch);

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
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    final int committeeCount = spec.getCommitteeCountPerSlot(state, epoch).intValue();

    if (committeeIndex < 0 || committeeIndex >= committeeCount) {
      throw new IllegalArgumentException(
          "Invalid committee index "
              + committeeIndex
              + " - expected between 0 and "
              + (committeeCount - 1));
    }
    final UInt64 committeeIndexUnsigned = UInt64.valueOf(committeeIndex);
    final AttestationData attestationData =
        spec.getGenericAttestationData(slot, state, block, committeeIndexUnsigned);
    final List<Integer> committee =
        spec.atSlot(slot)
            .getBeaconStateUtil()
            .getBeaconCommittee(state, slot, committeeIndexUnsigned);

    SszBitlist aggregationBits =
        Attestation.SSZ_SCHEMA.getAggregationBitsSchema().ofBits(committee.size());
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
              if (!result.isInvalid()) {
                dutyMetrics.onAttestationPublished(attestation.getData().getSlot());
                performanceTracker.saveProducedAttestation(attestation);
              } else {
                VALIDATOR_LOGGER.producedInvalidAttestation(
                    attestation.getData().getSlot(), result.getInvalidReason());
              }
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
    blockGossipChannel.publishBlock(block);
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

  @Override
  public SafeFuture<Optional<SubmitCommitteeSignaturesResult>> sendSyncCommitteeSignatures(
      final List<SyncCommitteeSignature> syncCommitteeSignatures) {

    final List<SafeFuture<InternalValidationResult>> addedSignatures =
        syncCommitteeSignatures.stream()
            .map(ValidateableSyncCommitteeSignature::fromValidator)
            .map(syncCommitteeSignaturePool::add)
            .collect(toList());

    return SafeFuture.collectAll(addedSignatures.stream())
        .thenApply(this::getSendSyncCommitteesResultFromFutures);
  }

  private Optional<SubmitCommitteeSignaturesResult> getSendSyncCommitteesResultFromFutures(
      final List<InternalValidationResult> internalValidationResults) {
    final List<SubmitCommitteeSignatureError> errorList = new ArrayList<>();
    for (int index = 0; index < internalValidationResults.size(); index++) {
      final Optional<SubmitCommitteeSignatureError> maybeError =
          fromInternalValidationResult(internalValidationResults.get(index), index);
      maybeError.ifPresent(errorList::add);
    }

    if (errorList.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new SubmitCommitteeSignaturesResult(errorList));
  }

  private Optional<SubmitCommitteeSignatureError> fromInternalValidationResult(
      final InternalValidationResult internalValidationResult, final int resultIndex) {
    if (!internalValidationResult.isReject()) {
      return Optional.empty();
    }
    return Optional.of(
        new SubmitCommitteeSignatureError(
            UInt64.valueOf(resultIndex),
            internalValidationResult.getDescription().orElse("Rejected")));
  }

  @VisibleForTesting
  boolean isSyncActive() {
    return !syncStateProvider.getCurrentSyncState().isInSync();
  }

  private ProposerDuties getProposerDutiesFromIndexesAndState(
      final BeaconState state, final UInt64 epoch) {
    final List<ProposerDuty> result = getProposalSlotsForEpoch(state, epoch);
    return new ProposerDuties(
        spec.atEpoch(epoch).getBeaconStateUtil().getCurrentDutyDependentRoot(state), result);
  }

  private AttesterDuties getAttesterDutiesFromIndexesAndState(
      final BeaconState state, final UInt64 epoch, final Collection<Integer> validatorIndexes) {
    final Bytes32 dependentRoot =
        epoch.isGreaterThan(spec.getCurrentEpoch(state))
            ? spec.atEpoch(epoch).getBeaconStateUtil().getCurrentDutyDependentRoot(state)
            : spec.atEpoch(epoch).getBeaconStateUtil().getPreviousDutyDependentRoot(state);
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
        spec.getValidatorPubKey(state, UInt64.valueOf(validatorIndex)),
        CommitteeAssignmentUtil.get_committee_assignment(state, epoch, validatorIndex),
        (pkey, committeeAssignment) -> {
          final UInt64 committeeCountPerSlot =
              spec.atEpoch(epoch).getBeaconStateUtil().getCommitteeCountPerSlot(state, epoch);
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

  private SafeFuture<Optional<BeaconState>> getStateForCommitteeDuties(
      final SpecVersion specVersion, final UInt64 epoch) {
    final Optional<BeaconState> bestState = combinedChainDataClient.getBestState();
    final Optional<SyncCommitteeUtil> maybeSyncCommitteeUtil = specVersion.getSyncCommitteeUtil();
    if (bestState.isPresent()
        && maybeSyncCommitteeUtil.isPresent()
        && maybeSyncCommitteeUtil
            .get()
            .isStateUsableForCommitteeCalculationAtEpoch(bestState.get(), epoch)) {
      return SafeFuture.completedFuture(bestState);
    }

    return combinedChainDataClient.getStateAtSlotExact(spec.computeStartSlotAtEpoch(epoch));
  }

  private SyncCommitteeDuties getSyncCommitteeDutiesFromIndexesAndState(
      final Optional<BeaconState> maybeState,
      final UInt64 epoch,
      final Collection<Integer> validatorIndices) {
    if (maybeState.isEmpty()) {
      return new SyncCommitteeDuties(List.of());
    }
    final BeaconState state = maybeState.get();
    return new SyncCommitteeDuties(
        validatorIndices.stream()
            .flatMap(validatorIndex -> getSyncCommitteeDuty(state, epoch, validatorIndex).stream())
            .collect(toList()));
  }

  private Optional<SyncCommitteeDuty> getSyncCommitteeDuty(
      final BeaconState state, final UInt64 epoch, final Integer validatorIndex) {
    final Optional<SyncCommitteeUtil> syncCommitteeUtil =
        spec.atEpoch(epoch).getSyncCommitteeUtil();
    final Set<Integer> duties =
        syncCommitteeUtil
            .map(util -> util.getCommitteeIndices(state, epoch, UInt64.valueOf(validatorIndex)))
            .orElse(Collections.emptySet());

    if (duties.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        new SyncCommitteeDuty(
            state.getValidators().get(validatorIndex).getPublicKey(), validatorIndex, duties));
  }

  private static <A, B, R> Optional<R> combine(
      Optional<A> a, Optional<B> b, BiFunction<A, B, R> fun) {
    if (a.isEmpty() || b.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(fun.apply(a.get(), b.get()));
  }

  private List<ProposerDuty> getProposalSlotsForEpoch(final BeaconState state, final UInt64 epoch) {
    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(epoch);
    final UInt64 startSlot = epochStartSlot.max(GENESIS_SLOT.increment());
    final UInt64 endSlot = epochStartSlot.plus(spec.slotsPerEpoch(epoch));
    final List<ProposerDuty> proposerSlots = new ArrayList<>();
    for (UInt64 slot = startSlot; slot.compareTo(endSlot) < 0; slot = slot.plus(UInt64.ONE)) {
      final int proposerIndex = spec.getBeaconProposerIndex(state, slot);
      final BLSPublicKey publicKey =
          spec.getValidatorPubKey(state, UInt64.valueOf(proposerIndex)).orElseThrow();
      proposerSlots.add(new ProposerDuty(publicKey, proposerIndex, slot));
    }
    return proposerSlots;
  }
}
