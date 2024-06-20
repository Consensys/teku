package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.events.EventChannelSubscriber;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.services.beaconchain.SlotProcessor;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.BeaconAsyncRunner;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.ForkChoiceNotifierExecutor;
import tech.pegasys.teku.services.beaconchain.init.MetricsModule.TickProcessingPerformanceRecordFactory;
import tech.pegasys.teku.services.beaconchain.init.PoolAndCachesModule.InvalidBlockRoots;
import tech.pegasys.teku.services.beaconchain.init.PowModule.ProposerDefaultFeeRecipient;
import tech.pegasys.teku.services.timer.TimerService;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.EpochCachePrimer;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.OperationsReOrgManager;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportMetrics;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.block.FailedExecutionPool;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessingPerformance;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessor;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;

import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.millisToSeconds;

@Module
public interface BeaconModule {

  @FunctionalInterface
  interface TickHandler {
    void onTick();
  }

  @FunctionalInterface
  interface GenesisTimeTracker {
    void update();
  }

  @Provides
  @Singleton
  static EpochCachePrimer epochCachePrimer(
      Spec spec,
      RecentChainData recentChainData,
      @BeaconAsyncRunner AsyncRunner beaconAsyncRunner) {
    return new EpochCachePrimer(spec, recentChainData, beaconAsyncRunner);
  }

  @Provides
  @Singleton
  static SlotProcessor slotProcessor(
      Spec spec,
      RecentChainData recentChainData,
      SyncService syncService,
      ForkChoiceTrigger forkChoiceTrigger,
      ForkChoiceNotifier forkChoiceNotifier,
      Eth2P2PNetwork p2pNetwork,
      SlotEventsChannel slotEventsChannelPublisher,
      EpochCachePrimer epochCachePrimer) {
    return new SlotProcessor(
        spec,
        recentChainData,
        syncService,
        forkChoiceTrigger,
        forkChoiceNotifier,
        p2pNetwork,
        slotEventsChannelPublisher,
        epochCachePrimer);
  }

  @Provides
  @Singleton
  static BlockImporter blockImporter(
      Spec spec,
      RecentChainData recentChainData,
      ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher,
      ForkChoice forkChoice,
      WeakSubjectivityValidator weakSubjectivityValidator,
      ExecutionLayerChannel executionLayer) {
    return new BlockImporter(
        spec,
        receivedBlockEventsChannelPublisher,
        recentChainData,
        forkChoice,
        weakSubjectivityValidator,
        executionLayer);
  }

  @Provides
  @Singleton
  static BlockManager blockManager(
      Spec spec,
      EventLogger eventLogger,
      TimeProvider timeProvider,
      RecentChainData recentChainData,
      BlockValidator blockValidator,
      BlockImporter blockImporter,
      BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      PendingPool<SignedBeaconBlock> pendingBlocks,
      @InvalidBlockRoots Map<Bytes32, BlockImportResult> invalidBlockRoots,
      Optional<BlockImportMetrics> blockImportMetrics,
      FutureItems<SignedBeaconBlock> futureBlocks,
      FailedExecutionPool failedExecutionPool,
      EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber,
      EventChannelSubscriber<BlockImportChannel> blockImportChannelSubscriber,
      EventChannelSubscriber<ReceivedBlockEventsChannel> receivedBlockEventsChannelSubscriber) {

    BlockManager blockManager =
        new BlockManager(
            recentChainData,
            blockImporter,
            blockBlobSidecarsTrackersPool,
            pendingBlocks,
            futureBlocks,
            invalidBlockRoots,
            blockValidator,
            timeProvider,
            eventLogger,
            blockImportMetrics);

    if (spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      blockManager.subscribeFailedPayloadExecution(failedExecutionPool::addFailedBlock);
    }
    slotEventsChannelSubscriber.subscribe(blockManager);
    blockImportChannelSubscriber.subscribe(blockManager);
    receivedBlockEventsChannelSubscriber.subscribe(blockManager);

    return blockManager;
  }

  @Provides
  @Singleton
  static ProposersDataManager proposersDataManager(
      Eth2NetworkConfiguration eth2NetworkConfig,
      Spec spec,
      MetricsSystem metricsSystem,
      @ForkChoiceNotifierExecutor AsyncRunnerEventThread forkChoiceNotifierExecutor,
      ExecutionLayerChannel executionLayer,
      RecentChainData recentChainData,
      EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber,
      @ProposerDefaultFeeRecipient Optional<Eth1Address> proposerDefaultFeeRecipient) {

    ProposersDataManager proposersDataManager =
        new ProposersDataManager(
            forkChoiceNotifierExecutor,
            spec,
            metricsSystem,
            executionLayer,
            recentChainData,
            proposerDefaultFeeRecipient,
            eth2NetworkConfig.isForkChoiceUpdatedAlwaysSendPayloadAttributes());
    slotEventsChannelSubscriber.subscribe(proposersDataManager);
    return proposersDataManager;
  }

  @Provides
  @Singleton
  static OperationsReOrgManager operationsReOrgManager(
      OperationPool<AttesterSlashing> attesterSlashingPool,
      OperationPool<ProposerSlashing> proposerSlashingPool,
      OperationPool<SignedVoluntaryExit> voluntaryExitPool,
      AggregatingAttestationPool attestationPool,
      AttestationManager attestationManager,
      OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool,
      RecentChainData recentChainData,
      EventChannelSubscriber<ChainHeadChannel> chainHeadChannelSubscriber) {

    OperationsReOrgManager operationsReOrgManager =
        new OperationsReOrgManager(
            proposerSlashingPool,
            attesterSlashingPool,
            voluntaryExitPool,
            attestationPool,
            attestationManager,
            blsToExecutionChangePool,
            recentChainData);
    chainHeadChannelSubscriber.subscribe(operationsReOrgManager);
    return operationsReOrgManager;
  }

  @Provides
  @Singleton
  static TickHandler tickHandler(
      TimeProvider timeProvider,
      RecentChainData recentChainData,
      ForkChoice forkChoice,
      SlotProcessor slotProcessor,
      TickProcessingPerformanceRecordFactory tickProcessingPerformanceRecordFactory,
      GenesisTimeTracker genesisTimeTracker) {
    return () -> {
      if (recentChainData.isPreGenesis()) {
        return;
      }

      final UInt64 currentTimeMillis = timeProvider.getTimeInMillis();
      final Optional<TickProcessingPerformance> performanceRecord =
          tickProcessingPerformanceRecordFactory.create();

      forkChoice.onTick(currentTimeMillis, performanceRecord);

      genesisTimeTracker.update();

      slotProcessor.onTick(currentTimeMillis, performanceRecord);
      performanceRecord.ifPresent(TickProcessingPerformance::complete);
    };
  }

  @Provides
  @Singleton
  static TimerService timerService(TickHandler tickHandler) {
    return new TimerService(tickHandler::onTick);
  }

  @Provides
  @Singleton
  static GenesisTimeTracker genesisTimeTracker(
      TimeProvider timeProvider,
      RecentChainData recentChainData,
      Eth2P2PNetwork p2pNetwork,
      StatusLogger statusLogger) {
    return new GenesisTimeTracker() {
      private UInt64 lastUpdateTime = UInt64.ZERO;

      @Override
      public void update() {
        final UInt64 genesisTime = recentChainData.getGenesisTime();
        final UInt64 currentTimeMillis = timeProvider.getTimeInMillis();
        final UInt64 currentTimeSeconds = millisToSeconds(currentTimeMillis);
        if (genesisTime.isGreaterThan(currentTimeSeconds)) {
          // notify every 10 minutes
          if (lastUpdateTime.plus(600L).isLessThanOrEqualTo(currentTimeSeconds)) {
            lastUpdateTime = currentTimeSeconds;
            statusLogger.timeUntilGenesis(
                genesisTime.minus(currentTimeSeconds).longValue(), p2pNetwork.getPeerCount());
          }
        }
      }
    };
  }

  @Provides
  @Singleton
  static TickProcessor tickProcessor(Spec spec, RecentChainData recentChainData) {
    return new TickProcessor(spec, recentChainData);
  }
}
