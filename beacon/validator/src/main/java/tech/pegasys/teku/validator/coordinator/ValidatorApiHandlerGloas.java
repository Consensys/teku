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

import java.util.Optional;
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
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
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
      final ExecutionProofManager executionProofManager) {
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
  }

  @Override
  protected SafeFuture<Optional<BeaconState>> getStateForBlockProduction(
      final UInt64 slot, final BlockProductionPerformance productionPerformance) {
    // TODO-GLOAS: https://github.com/Consensys/teku/issues/10352 this is naive state selection
    // (good enough for devnet-0) and needs to be revisited
    final Optional<BeaconState> executionPayloadState =
        combinedChainDataClient
            .getRecentChainData()
            .getBlockRootInEffectBySlot(slot)
            .flatMap(
                blockRoot ->
                    combinedChainDataClient
                        .getStore()
                        // no state will be present for slots before the Gloas fork
                        .getExecutionPayloadStateIfAvailable(blockRoot));
    if (executionPayloadState.isPresent()) {
      return SafeFuture.completedFuture(executionPayloadState);
    }
    return combinedChainDataClient.getStateForBlockProduction(
        slot,
        forkChoiceTrigger.isForkChoiceOverrideLateBlockEnabled(),
        productionPerformance::lateBlockReorgPreparationCompleted);
  }
}
