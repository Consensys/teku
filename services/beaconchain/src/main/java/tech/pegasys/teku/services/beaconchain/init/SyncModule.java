package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.beacon.sync.DefaultSyncServiceFactory;
import tech.pegasys.teku.beacon.sync.SyncConfig;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beacon.sync.SyncServiceFactory;
import tech.pegasys.teku.beacon.sync.events.CoalescingChainHeadChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.services.beaconchain.BeaconChainConfiguration;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.BeaconAsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.TerminalPowBlockMonitor;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.coordinator.DepositProvider;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.Optional;

@Module
public interface SyncModule {

  @Provides
  @Singleton
  static SyncServiceFactory syncServiceFactory(
      Spec spec,
      SyncConfig syncConfig,
      Eth2NetworkConfiguration eth2NetworkConfig,
      MetricsSystem metricsSystem,
      AsyncRunnerFactory asyncRunnerFactory,
      @BeaconAsyncRunner AsyncRunner beaconAsyncRunner,
      TimeProvider timeProvider,
      RecentChainData recentChainData,
      CombinedChainDataClient combinedChainDataClient,
      StorageUpdateChannel storageUpdateChannel,
      Eth2P2PNetwork p2pNetwork,
      BlockImporter blockImporter,
      BlobSidecarManager blobSidecarManager,
      PendingPool<SignedBeaconBlock> pendingBlocksPool,
      BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      SignatureVerificationService signatureVerificationService) {
    return new DefaultSyncServiceFactory(
        syncConfig,
        eth2NetworkConfig.getNetworkBoostrapConfig().getGenesisState(),
        metricsSystem,
        asyncRunnerFactory,
        beaconAsyncRunner,
        timeProvider,
        recentChainData,
        combinedChainDataClient,
        storageUpdateChannel,
        p2pNetwork,
        blockImporter,
        blobSidecarManager,
        pendingBlocksPool,
        blockBlobSidecarsTrackersPool,
        eth2NetworkConfig.getStartupTargetPeerCount(),
        signatureVerificationService,
        Duration.ofSeconds(eth2NetworkConfig.getStartupTimeoutSeconds()),
        spec);
  }

  @Provides
  @Singleton
  static SyncService syncService(
      SyncServiceFactory syncServiceFactory,
      EventChannels eventChannels,
      ForkChoice forkChoice,
      ForkChoiceNotifier forkChoiceNotifier,
      DepositProvider depositProvider,
      Optional<TerminalPowBlockMonitor> terminalPowBlockMonitor,
      Eth2P2PNetwork p2pNetwork,
      ChainHeadChannel chainHeadChannelPublisher,
      EventLogger eventLogger) {
    SyncService syncService = syncServiceFactory.create(eventChannels);

    // chainHeadChannel subscription
    CoalescingChainHeadChannel coalescingChainHeadChannel =
        new CoalescingChainHeadChannel(chainHeadChannelPublisher, eventLogger);
    syncService.getForwardSync().subscribeToSyncChanges(coalescingChainHeadChannel);

    // forkChoiceNotifier subscription
    syncService.subscribeToSyncStateChangesAndUpdate(
        syncState -> forkChoiceNotifier.onSyncingStatusChanged(syncState.isInSync()));

    // depositProvider subscription
    syncService.subscribeToSyncStateChangesAndUpdate(
        syncState -> depositProvider.onSyncingStatusChanged(syncState.isInSync()));

    // forkChoice subscription
    forkChoice.subscribeToOptimisticHeadChangesAndUpdate(syncService.getOptimisticSyncSubscriber());

    // terminalPowBlockMonitor subscription
    terminalPowBlockMonitor.ifPresent(
        monitor ->
            syncService.subscribeToSyncStateChangesAndUpdate(
                syncState -> monitor.onNodeSyncStateChanged(syncState.isInSync())));

    // p2pNetwork subscription so gossip can be enabled and disabled appropriately
    syncService.subscribeToSyncStateChangesAndUpdate(
        state -> p2pNetwork.onSyncStateChanged(state.isInSync(), state.isOptimistic()));

    return syncService;
  }
}
