package tech.pegasys.teku.services.beaconchain.init;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

import dagger.multibindings.IntoSet;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beacon.sync.gossip.blobs.RecentBlobSidecarsFetcher;
import tech.pegasys.teku.beacon.sync.gossip.blocks.RecentBlocksFetcher;
import tech.pegasys.teku.beaconrestapi.BeaconRestApi;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.ForkChoiceExecutor;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.ForkChoiceNotifierExecutor;
import tech.pegasys.teku.services.powchain.PowchainConfiguration;
import tech.pegasys.teku.services.timer.TimerService;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.OperationsReOrgManager;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.forkchoice.TerminalPowBlockMonitor;
import tech.pegasys.teku.statetransition.genesis.GenesisHandler;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.coordinator.ValidatorIndexCacheTracker;

import java.util.Optional;
import java.util.Set;

@Module
public interface MainModule {

  /** Dummy class returned by the dependency which requires just initialization */
  class VoidInitializer {}

  interface ServiceStarter {
    SafeFuture<Void> start();
  }

  interface ServiceStopper {
    SafeFuture<Void> stop();
  }

  @Provides
  @IntoSet
  static VoidInitializer initSlashingEventsSubscriptions(
      ValidatorConfig validatorConfig,
      ValidatorTimingChannel validatorTimingChannel,
      OperationPool<AttesterSlashing> attesterSlashingPool,
      OperationPool<ProposerSlashing> proposerSlashingPool) {
    if (validatorConfig.isShutdownWhenValidatorSlashedEnabled()) {
      attesterSlashingPool.subscribeOperationAdded(
          (operation, validationStatus, fromNetwork) ->
              validatorTimingChannel.onAttesterSlashing(operation));
      proposerSlashingPool.subscribeOperationAdded(
          (operation, validationStatus, fromNetwork) ->
              validatorTimingChannel.onProposerSlashing(operation));
    }
    return new VoidInitializer();
  }

  @Provides
  @IntoSet
  static VoidInitializer initGenesisHandler(
      RecentChainData recentChainData,
      Lazy<GenesisHandler> genesisHandler,
      PowchainConfiguration powchainConfig,
      StatusLogger statusLogger) {
    if (!recentChainData.isPreGenesis()) {
      // We already have a genesis block - no need for a genesis handler
    } else if (!powchainConfig.isEnabled()) {
      // We're pre-genesis but no eth1 endpoint is set
      throw new IllegalStateException("ETH1 is disabled, but no initial state is set.");
    } else {
      statusLogger.loadingGenesisFromEth1Chain();
      genesisHandler.get();
    }
    return new VoidInitializer();
  }

  @Provides
  @IntoSet
  static VoidInitializer initOperationsReOrgManager(OperationsReOrgManager operationsReOrgManager) {
    return new VoidInitializer();
  }

  @Provides
  @IntoSet
  static VoidInitializer initValidatorIndexCacheTracker(
      ValidatorIndexCacheTracker validatorIndexCacheTracker) {
    return new VoidInitializer();
  }

  @Provides
  @IntoSet
  static VoidInitializer initRecentBlocksFetcher(RecentBlocksFetcher recentBlocksFetcher) {
    return new VoidInitializer();
  }

  @Provides
  @IntoSet
  static VoidInitializer initRecentBlobSidecarsFetcher(
      RecentBlobSidecarsFetcher recentBlocksFetcher) {
    return new VoidInitializer();
  }

  @Provides
  @Singleton
  static ServiceStarter serviceStarter(
      Set<VoidInitializer> allInitializers,
      Optional<BeaconRestApi> beaconRestApi,
      SyncService syncService,
      BlockManager blockManager,
      AttestationManager attestationManager,
      Eth2P2PNetwork p2pNetwork,
      TimerService timerService,
      Optional<TerminalPowBlockMonitor> terminalPowBlockMonitor,
      @ForkChoiceExecutor AsyncRunnerEventThread forkChoiceExecutor,
      @ForkChoiceNotifierExecutor AsyncRunnerEventThread forkChoiceNotifierExecutor) {
    return () ->
        SafeFuture.fromRunnable(
                () -> {
                  forkChoiceExecutor.start();
                  forkChoiceNotifierExecutor.start();
                })
            .thenCompose(
                __ ->
                    SafeFuture.allOf(
                        syncService.start(),
                        blockManager.start(),
                        attestationManager.start(),
                        p2pNetwork.start(),
                        SafeFuture.fromRunnable(
                            () ->
                                terminalPowBlockMonitor.ifPresent(TerminalPowBlockMonitor::start))))
            .thenCompose(__ -> timerService.start().thenApply(___ -> null))
            .thenCompose(
                __ ->
                    beaconRestApi
                        .map(BeaconRestApi::start)
                        .orElse(SafeFuture.completedFuture(null))
                        .thenApply(___ -> null));
  }

  @Provides
  @Singleton
  static ServiceStarter serviceStopper(
      Optional<BeaconRestApi> beaconRestApi,
      SyncService syncService,
      BlockManager blockManager,
      AttestationManager attestationManager,
      Eth2P2PNetwork p2pNetwork,
      TimerService timerService,
      Optional<TerminalPowBlockMonitor> terminalPowBlockMonitor,
      @ForkChoiceExecutor AsyncRunnerEventThread forkChoiceExecutor,
      @ForkChoiceNotifierExecutor AsyncRunnerEventThread forkChoiceNotifierExecutor) {
    return () ->
        SafeFuture.allOf(
                beaconRestApi.map(BeaconRestApi::stop).orElse(SafeFuture.completedFuture(null)),
                syncService.stop(),
                blockManager.stop(),
                attestationManager.stop(),
                p2pNetwork.stop(),
                timerService.stop(),
                SafeFuture.fromRunnable(
                    () -> terminalPowBlockMonitor.ifPresent(TerminalPowBlockMonitor::stop)))
            .thenRun(
                () -> {
                  forkChoiceExecutor.stop();
                  forkChoiceNotifierExecutor.stop();
                });
  }
}
