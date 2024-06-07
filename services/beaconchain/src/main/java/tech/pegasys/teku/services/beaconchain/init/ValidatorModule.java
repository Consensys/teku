package tech.pegasys.teku.services.beaconchain.init;

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
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
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
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;

@Module
public interface ValidatorModule {

  @Provides
  @Singleton
  static ActiveValidatorTracker provideActiveValidatorTracker(
      Spec spec, EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber) {
    ActiveValidatorTracker activeValidatorTracker = new ActiveValidatorTracker(spec);
    slotEventsChannelSubscriber.subscribe(activeValidatorTracker);
    return activeValidatorTracker;
  }

  @Provides
  @Singleton
  static ExecutionLayerBlockProductionManager provideExecutionLayerBlockProductionManager(
      ExecutionLayerChannel executionLayer,
      EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber) {

    return ExecutionLayerBlockManagerFactory.create(executionLayer, slotEventsChannelSubscriber);
  }

  @Provides
  @Singleton
  static GraffitiBuilder provideGraffitiBuilder(
      ValidatorConfig validatorConfig,
      EventChannelSubscriber<ExecutionClientVersionChannel>
          executionClientVersionChannelSubscriber) {
    GraffitiBuilder graffitiBuilder =
        new GraffitiBuilder(
            validatorConfig.getClientGraffitiAppendFormat(),
            validatorConfig.getGraffitiProvider().get());
    executionClientVersionChannelSubscriber.subscribe(graffitiBuilder);
    return graffitiBuilder;
  }

  @Provides
  @Singleton
  static GraffitiBuilder provideGraffitiBuilder(
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
    return graffitiBuilder;
  }

  @Provides
  @Singleton
  static BlockOperationSelectorFactory provideBlockOperationSelectorFactory(
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
      BlockProductionAndPublishingPerformanceFactory blockProductionPerformanceFactory) {
    return new ValidatorApiHandler(
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
}
