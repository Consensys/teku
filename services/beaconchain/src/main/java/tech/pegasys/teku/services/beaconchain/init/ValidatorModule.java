/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.services.beaconchain.init;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.RewardCalculator;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.ExecutionClientVersionChannel;
import tech.pegasys.teku.ethereum.executionclient.ExecutionClientVersionProvider;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionAndPublishingPerformanceFactory;
import tech.pegasys.teku.infrastructure.events.EventChannelSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.BlobSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubscriptionManager;
import tech.pegasys.teku.services.beaconchain.ValidatorIsConnectedProviderImpl;
import tech.pegasys.teku.services.executionlayer.ExecutionLayerBlockManagerFactory;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessagePool;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.AggregateAttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.client.ValidatorIsConnectedProvider;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.coordinator.ActiveValidatorTracker;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.BlockOperationSelectorFactory;
import tech.pegasys.teku.validator.coordinator.DepositProvider;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;
import tech.pegasys.teku.validator.coordinator.GraffitiBuilder;
import tech.pegasys.teku.validator.coordinator.MilestoneBasedBlockFactory;
import tech.pegasys.teku.validator.coordinator.ValidatorApiHandler;
import tech.pegasys.teku.validator.coordinator.ValidatorIndexCacheTracker;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;

@Module
public interface ValidatorModule {

  @Provides
  @Singleton
  static ActiveValidatorTracker activeValidatorTracker(
      Spec spec, EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber) {
    ActiveValidatorTracker activeValidatorTracker = new ActiveValidatorTracker(spec);
    slotEventsChannelSubscriber.subscribe(activeValidatorTracker);
    return activeValidatorTracker;
  }

  @Provides
  @Singleton
  static ExecutionLayerBlockProductionManager executionLayerBlockProductionManager(
      ExecutionLayerChannel executionLayer,
      EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber) {

    return ExecutionLayerBlockManagerFactory.create(executionLayer, slotEventsChannelSubscriber);
  }

  @Provides
  @Singleton
  static GraffitiBuilder graffitiBuilder(
      ValidatorConfig validatorConfig,
      EventChannelSubscriber<ExecutionClientVersionChannel>
          executionClientVersionChannelSubscriber) {
    GraffitiBuilder graffitiBuilder =
        new GraffitiBuilder(
            validatorConfig.getClientGraffitiAppendFormat());
    executionClientVersionChannelSubscriber.subscribe(graffitiBuilder);
    return graffitiBuilder;
  }

  @Provides
  @Singleton
  static ExecutionClientVersionProvider executionClientVersionProvider(
      GraffitiBuilder graffitiBuilder,
      ExecutionLayerChannel executionLayer,
      ExecutionClientVersionChannel executionClientVersionChannelPublisher,
      EventChannelSubscriber<ExecutionClientEventsChannel> executionClientEventsChannelSubscriber) {

    final ExecutionClientVersionProvider executionClientVersionProvider =
        new ExecutionClientVersionProvider(
            executionLayer,
            executionClientVersionChannelPublisher,
            graffitiBuilder.getConsensusClientVersion());
    executionClientEventsChannelSubscriber.subscribe(executionClientVersionProvider);
    return executionClientVersionProvider;
  }

  @Provides
  @Singleton
  static BlockOperationSelectorFactory blockOperationSelectorFactory(
      Spec spec,
      AggregatingAttestationPool attestationPool,
      OperationPool<AttesterSlashing> attesterSlashingPool,
      OperationPool<ProposerSlashing> proposerSlashingPool,
      OperationPool<SignedVoluntaryExit> voluntaryExitPool,
      OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool,
      SyncCommitteeContributionPool syncCommitteeContributionPool,
      DepositProvider depositProvider,
      Eth1DataCache eth1DataCache,
      ForkChoiceNotifier forkChoiceNotifier,
      ExecutionLayerBlockProductionManager executionLayerBlockProductionManager,
      GraffitiBuilder graffitiBuilder) {

    return new BlockOperationSelectorFactory(
        spec,
        attestationPool,
        attesterSlashingPool,
        proposerSlashingPool,
        voluntaryExitPool,
        blsToExecutionChangePool,
        syncCommitteeContributionPool,
        depositProvider,
        eth1DataCache,
        graffitiBuilder,
        forkChoiceNotifier,
        executionLayerBlockProductionManager);
  }

  @Provides
  @Singleton
  static BlockFactory blockFactory(
      Spec spec, BlockOperationSelectorFactory blockOperationSelectorFactory) {
    return new MilestoneBasedBlockFactory(spec, blockOperationSelectorFactory);
  }

  @Provides
  @Singleton
  static ChainDataProvider chainDataProvider(
      Spec spec,
      RecentChainData recentChainData,
      CombinedChainDataClient combinedChainDataClient,
      RewardCalculator rewardCalculator) {
    return new ChainDataProvider(spec, recentChainData, combinedChainDataClient, rewardCalculator);
  }

  @Provides
  @Singleton
  static ValidatorApiHandler validatorApiHandler(
      Spec spec,
      ChainDataProvider chainDataProvider,
      DataProvider dataProvider,
      CombinedChainDataClient combinedChainDataClient,
      SyncService syncService,
      BlockFactory blockFactory,
      BlockImportChannel blockImportChannel,
      BlockGossipChannel blockGossipChannel,
      BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      BlobSidecarGossipChannel blobSidecarGossipChannel,
      AggregatingAttestationPool attestationPool,
      AttestationManager attestationManager,
      AttestationTopicSubscriber attestationTopicSubscriber,
      ActiveValidatorTracker activeValidatorTracker,
      DutyMetrics dutyMetrics,
      PerformanceTracker performanceTracker,
      ForkChoiceTrigger forkChoiceTrigger,
      ProposersDataManager proposersDataManager,
      SyncCommitteeMessagePool syncCommitteeMessagePool,
      SyncCommitteeContributionPool syncCommitteeContributionPool,
      SyncCommitteeSubscriptionManager syncCommitteeSubscriptionManager,
      BlockProductionAndPublishingPerformanceFactory blockProductionPerformanceFactory,
      EventChannelSubscriber<ValidatorApiChannel> validatorApiChannelSubscriber) {
    ValidatorApiHandler validatorApiHandler =
        new ValidatorApiHandler(
            chainDataProvider,
            dataProvider.getNodeDataProvider(),
            combinedChainDataClient,
            syncService,
            blockFactory,
            blockImportChannel,
            blockGossipChannel,
            blockBlobSidecarsTrackersPool,
            blobSidecarGossipChannel,
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
            blockProductionPerformanceFactory);

    validatorApiChannelSubscriber.subscribe(validatorApiHandler);

    return validatorApiHandler;
  }

  @Provides
  @Singleton
  static AttestationManager attestationManager(
      Spec spec,
      BlockImporter blockImporter,
      AggregateAttestationValidator aggregateValidator,
      PendingPool<ValidatableAttestation> pendingAttestations,
      FutureItems<ValidatableAttestation> futureAttestations,
      ForkChoice forkChoice,
      AggregatingAttestationPool attestationPool,
      AttestationValidator attestationValidator,
      SignatureVerificationService signatureVerificationService,
      ActiveValidatorChannel activeValidatorChannel,
      EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber,
      EventChannelSubscriber<ReceivedBlockEventsChannel> receivedBlockEventsChannelSubscriber) {

    // TODO
    blockImporter.subscribeToVerifiedBlockAttestations(
        (slot, attestations) ->
            attestations.forEach(
                attestation ->
                    aggregateValidator.addSeenAggregate(
                        ValidatableAttestation.from(spec, attestation))));

    AttestationManager attestationManager =
        AttestationManager.create(
            pendingAttestations,
            futureAttestations,
            forkChoice,
            attestationPool,
            attestationValidator,
            aggregateValidator,
            signatureVerificationService,
            activeValidatorChannel);

    slotEventsChannelSubscriber.subscribe(attestationManager);
    receivedBlockEventsChannelSubscriber.subscribe(attestationManager);

    return attestationManager;
  }

  @Provides
  @Singleton
  static ValidatorIsConnectedProvider validatorIsConnectedProvider(
      Lazy<ForkChoiceNotifier> forkChoiceNotifier) {
    return new ValidatorIsConnectedProviderImpl(() -> forkChoiceNotifier.get());
  }

  @Provides
  @Singleton
  static ValidatorIndexCacheTracker validatorIndexCacheTracker(
      RecentChainData recentChainData,
      EventChannelSubscriber<FinalizedCheckpointChannel> finalizedCheckpointChannelSubscriber) {
    final ValidatorIndexCacheTracker validatorIndexCacheTracker =
        new ValidatorIndexCacheTracker(recentChainData);
    finalizedCheckpointChannelSubscriber.subscribe(validatorIndexCacheTracker);
    return validatorIndexCacheTracker;
  }
}
