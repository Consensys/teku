/*
 * Copyright Consensys Software Inc., 2026
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

import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.beacon.sync.events.SyncStateProvider;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionAndPublishingPerformanceFactory;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubscriptionManager;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.payloadattestation.PayloadAttestationPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessagePool;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;
import tech.pegasys.teku.validator.coordinator.publisher.BlockPublisher;
import tech.pegasys.teku.validator.coordinator.publisher.ExecutionPayloadPublisher;

public class ValidatorApiHandlerGloas extends ValidatorApiHandler {

  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionPayloadBidManager executionPayloadBidManager;
  private final ProposerPreferencesManager proposerPreferencesManager;

  public ValidatorApiHandlerGloas(
      final ChainDataProvider chainDataProvider,
      final NodeDataProvider nodeDataProvider,
      final NetworkDataProvider networkDataProvider,
      final CombinedChainDataClient combinedChainDataClient,
      final SyncStateProvider syncStateProvider,
      final BlockFactory blockFactory,
      final AggregatingAttestationPool attestationPool,
      final AttestationManager attestationManager,
      final AttestationTopicSubscriber attestationTopicSubscriber,
      final ActiveValidatorTracker activeValidatorTracker,
      final DutyMetrics dutyMetrics,
      final PerformanceTracker performanceTracker,
      final Spec spec,
      final ForkChoiceTrigger forkChoiceTrigger,
      final ProposersDataManager proposersDataManager,
      final SyncCommitteeMessagePool syncCommitteeMessagePool,
      final SyncCommitteeContributionPool syncCommitteeContributionPool,
      final SyncCommitteeSubscriptionManager syncCommitteeSubscriptionManager,
      final BlockProductionAndPublishingPerformanceFactory
          blockProductionAndPublishingPerformanceFactory,
      final BlockPublisher blockPublisher,
      final PayloadAttestationPool payloadAttestationPool,
      final ExecutionPayloadManager executionPayloadManager,
      final ExecutionPayloadFactory executionPayloadFactory,
      final ExecutionPayloadPublisher executionPayloadPublisher,
      final ExecutionPayloadBidManager executionPayloadBidManager,
      final ExecutionProofManager executionProofManager,
      final ProposerPreferencesManager proposerPreferencesManager) {
    super(
        chainDataProvider,
        nodeDataProvider,
        networkDataProvider,
        combinedChainDataClient,
        syncStateProvider,
        blockFactory,
        attestationPool,
        attestationManager,
        attestationTopicSubscriber,
        activeValidatorTracker,
        dutyMetrics,
        performanceTracker,
        spec,
        forkChoiceTrigger,
        proposersDataManager,
        syncCommitteeMessagePool,
        syncCommitteeContributionPool,
        syncCommitteeSubscriptionManager,
        blockProductionAndPublishingPerformanceFactory,
        blockPublisher,
        payloadAttestationPool,
        executionPayloadManager,
        executionPayloadFactory,
        executionPayloadPublisher,
        executionProofManager);
    this.executionPayloadBidManager = executionPayloadBidManager;
    this.proposerPreferencesManager = proposerPreferencesManager;
  }

  @Override
  public SafeFuture<Void> sendSignedProposerPreferences(
      final List<SignedProposerPreferences> signedProposerPreferences) {
    return SafeFuture.collectAll(
            signedProposerPreferences.stream().map(proposerPreferencesManager::addLocal))
        .thenAccept(
            results -> {
              final List<String> errorMessages =
                  results.stream()
                      .filter(result -> result.isReject())
                      .flatMap(result -> result.getDescription().stream())
                      .toList();
              if (!errorMessages.isEmpty()) {
                LOG.warn(
                    "Some proposer preferences were rejected: {}",
                    String.join("; ", errorMessages));
              }
            });
  }

  @Override
  public SafeFuture<Void> publishSignedExecutionPayloadBid(
      final SignedExecutionPayloadBid signedExecutionPayloadBid) {
    return executionPayloadBidManager
        .validateAndAddBid(
            signedExecutionPayloadBid, ExecutionPayloadBidManager.RemoteBidOrigin.BUILDER)
        .thenAccept(
            result -> {
              if (!result.isAccept()) {
                throw new IllegalArgumentException(
                    "Invalid execution payload bid: "
                        + result.getDescription().orElse("unknown reason"));
              }
            });
  }

  @Override
  protected SafeFuture<Optional<BeaconState>> getStateForBlockProduction(
      final UInt64 slot, final BlockProductionPerformance productionPerformance) {
    // TODO-GLOAS: https://github.com/Consensys/teku/issues/10352 this is very simple and naïve
    // state selection (possibly good enough for devnet-0) that needs to be revisited
    return getExecutionPayloadStateForBlockProduction(slot)
        .map(
            executionPayloadState -> SafeFuture.completedFuture(Optional.of(executionPayloadState)))
        .orElseGet(
            () ->
                combinedChainDataClient.getStateForBlockProduction(
                    slot,
                    forkChoiceTrigger.isForkChoiceOverrideLateBlockEnabled(),
                    productionPerformance::lateBlockReorgPreparationCompleted));
  }

  @Override
  protected int computeCommitteeIndexForAttestation(
      final UInt64 slot, final BeaconBlock block, final int committeeIndex) {
    if (slot.equals(block.getSlot())) {
      return 0;
    }
    return spec.atSlot(slot)
        .getForkChoiceUtil()
        .toVersionGloas()
        .map(
            forkChoiceUtil ->
                forkChoiceUtil.isBlockStatusFull(combinedChainDataClient.getStore(), block) ? 1 : 0)
        .orElse(0);
  }

  private Optional<BeaconState> getExecutionPayloadStateForBlockProduction(final UInt64 slot) {
    if (!combinedChainDataClient.isStoreAvailable()) {
      return Optional.empty();
    }
    return combinedChainDataClient
        .getRecentChainData()
        .getBlockRootInEffectBySlot(slot)
        .flatMap(
            blockRoot ->
                combinedChainDataClient
                    .getStore()
                    // no state will be present for slots before the Gloas fork (or if empty
                    // after the Gloas fork)
                    .getExecutionPayloadStateIfAvailable(blockRoot)
                    .flatMap(state -> combinedChainDataClient.regenerateBeaconState(state, slot)));
  }
}
