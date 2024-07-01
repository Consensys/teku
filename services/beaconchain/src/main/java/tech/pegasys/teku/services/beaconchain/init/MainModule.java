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

import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import java.util.Optional;
import java.util.Set;
import javax.inject.Singleton;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beacon.sync.gossip.blobs.RecentBlobSidecarsFetcher;
import tech.pegasys.teku.beacon.sync.gossip.blocks.RecentBlocksFetcher;
import tech.pegasys.teku.beaconrestapi.BeaconRestApi;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.services.beaconchain.BeaconChainControllerFacade;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.ForkChoiceExecutor;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.ForkChoiceNotifierExecutor;
import tech.pegasys.teku.services.beaconchain.init.LoggingModule.InitLogger;
import tech.pegasys.teku.services.powchain.PowchainConfiguration;
import tech.pegasys.teku.services.timer.TimerService;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.OperationsReOrgManager;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.block.FailedExecutionPool;
import tech.pegasys.teku.statetransition.forkchoice.TerminalPowBlockMonitor;
import tech.pegasys.teku.statetransition.genesis.GenesisHandler;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.coordinator.ValidatorIndexCacheTracker;

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

  @Binds
  @Singleton
  BeaconChainControllerFacade beaconChainController(SimpleBeaconChainController impl);

  @Provides
  @IntoSet
  static VoidInitializer initSlashingEventsSubscriptions(
      final ValidatorConfig validatorConfig,
      final ValidatorTimingChannel validatorTimingChannel,
      final OperationPool<AttesterSlashing> attesterSlashingPool,
      final OperationPool<ProposerSlashing> proposerSlashingPool) {
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
      final RecentChainData recentChainData,
      final Lazy<GenesisHandler> genesisHandler,
      final PowchainConfiguration powchainConfig,
      final StatusLogger statusLogger) {
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
  @SuppressWarnings("UnusedVariable")
  static VoidInitializer initOperationsReOrgManager(
      final OperationsReOrgManager operationsReOrgManager) {
    return new VoidInitializer();
  }

  @Provides
  @IntoSet
  @SuppressWarnings("UnusedVariable")
  static VoidInitializer initValidatorIndexCacheTracker(
      final ValidatorIndexCacheTracker validatorIndexCacheTracker) {
    return new VoidInitializer();
  }

  @Provides
  @IntoSet
  @SuppressWarnings("UnusedVariable")
  static VoidInitializer initRecentBlocksFetcher(final RecentBlocksFetcher recentBlocksFetcher) {
    return new VoidInitializer();
  }

  @Provides
  @IntoSet
  @SuppressWarnings("UnusedVariable")
  static VoidInitializer initRecentBlobSidecarsFetcher(
      final RecentBlobSidecarsFetcher recentBlobSidecarsFetcher) {
    return new VoidInitializer();
  }

  @Provides
  @IntoSet
  static VoidInitializer subscribeFailedPayloadExecution(
      final Spec spec,
      final BlockManager blockManager,
      final FailedExecutionPool failedExecutionPool) {
    if (spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      blockManager.subscribeFailedPayloadExecution(failedExecutionPool::addFailedBlock);
    }
    return new VoidInitializer();
  }

  @Provides
  @IntoSet
  static VoidInitializer subscribeOnStoreInitialized(
      final RecentChainData recentChainData,
      final StorageModule.OnStoreInitializedHandler onStoreInitializedHandler) {

    recentChainData.subscribeStoreInitialized(onStoreInitializedHandler::handle);
    return new VoidInitializer();
  }

  @Provides
  @Singleton
  @SuppressWarnings("UnusedVariable")
  static ServiceStarter serviceStarter(
      final Set<VoidInitializer> allInitializers,
      final Optional<BeaconRestApi> beaconRestApi,
      final SyncService syncService,
      final BlockManager blockManager,
      final AttestationManager attestationManager,
      final Eth2P2PNetwork p2pNetwork,
      final TimerService timerService,
      final Optional<TerminalPowBlockMonitor> terminalPowBlockMonitor,
      final InitLogger initLogger) {
    return () ->
        SafeFuture.fromRunnable(() -> initLogger.logger().info("Starting BeaconChain services"))
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
                        .thenApply(___ -> null))
            .thenRun(() -> initLogger.logger().info("BeaconChain services started"));
  }

  @Provides
  @Singleton
  static ServiceStopper serviceStopper(
      final Optional<BeaconRestApi> beaconRestApi,
      final SyncService syncService,
      final BlockManager blockManager,
      final AttestationManager attestationManager,
      final Eth2P2PNetwork p2pNetwork,
      final TimerService timerService,
      final Optional<TerminalPowBlockMonitor> terminalPowBlockMonitor,
      @ForkChoiceExecutor final AsyncRunnerEventThread forkChoiceExecutor,
      @ForkChoiceNotifierExecutor final AsyncRunnerEventThread forkChoiceNotifierExecutor) {
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
