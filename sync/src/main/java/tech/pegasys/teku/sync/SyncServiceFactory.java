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

package tech.pegasys.teku.sync;

import java.time.Duration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.events.SyncStateProvider;
import tech.pegasys.teku.sync.events.SyncStateTracker;
import tech.pegasys.teku.sync.forward.ForwardSync;
import tech.pegasys.teku.sync.forward.ForwardSyncService;
import tech.pegasys.teku.sync.forward.multipeer.MultipeerSyncService;
import tech.pegasys.teku.sync.forward.singlepeer.SinglePeerSyncServiceFactory;
import tech.pegasys.teku.sync.gossip.FetchRecentBlocksService;
import tech.pegasys.teku.sync.historical.HistoricalBlockSyncService;

public class SyncServiceFactory {
  private final SyncConfig syncConfig;
  private final MetricsSystem metrics;
  private final AsyncRunnerFactory asyncRunnerFactory;
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final RecentChainData recentChainData;
  private final CombinedChainDataClient combinedChainDataClient;
  private final StorageUpdateChannel storageUpdateChannel;
  private final Eth2P2PNetwork p2pNetwork;
  private final BlockImporter blockImporter;
  private final PendingPool<SignedBeaconBlock> pendingBlocks;
  private final int getStartupTargetPeerCount;
  private final AsyncBLSSignatureVerifier signatureVerifier;
  private final Duration startupTimeout;
  private final Spec spec;

  private SyncServiceFactory(
      final SyncConfig syncConfig,
      final MetricsSystem metrics,
      final AsyncRunnerFactory asyncRunnerFactory,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient,
      final StorageUpdateChannel storageUpdateChannel,
      final Eth2P2PNetwork p2pNetwork,
      final BlockImporter blockImporter,
      final PendingPool<SignedBeaconBlock> pendingBlocks,
      final int getStartupTargetPeerCount,
      final SignatureVerificationService signatureVerifier,
      final Duration startupTimeout,
      final Spec spec) {
    this.syncConfig = syncConfig;
    this.metrics = metrics;
    this.asyncRunnerFactory = asyncRunnerFactory;
    this.asyncRunner = asyncRunner;
    this.timeProvider = timeProvider;
    this.recentChainData = recentChainData;
    this.combinedChainDataClient = combinedChainDataClient;
    this.storageUpdateChannel = storageUpdateChannel;
    this.p2pNetwork = p2pNetwork;
    this.blockImporter = blockImporter;
    this.pendingBlocks = pendingBlocks;
    this.getStartupTargetPeerCount = getStartupTargetPeerCount;
    this.signatureVerifier = signatureVerifier;
    this.startupTimeout = startupTimeout;
    this.spec = spec;
  }

  public static SyncService createSyncService(
      final SyncConfig syncConfig,
      final MetricsSystem metrics,
      final AsyncRunnerFactory asyncRunnerFactory,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient,
      final StorageUpdateChannel storageUpdateChannel,
      final Eth2P2PNetwork p2pNetwork,
      final BlockImporter blockImporter,
      final PendingPool<SignedBeaconBlock> pendingBlocks,
      final int getStartupTargetPeerCount,
      final SignatureVerificationService signatureVerificationService,
      final Duration startupTimeout,
      final Spec spec) {
    final SyncServiceFactory factory =
        new SyncServiceFactory(
            syncConfig,
            metrics,
            asyncRunnerFactory,
            asyncRunner,
            timeProvider,
            recentChainData,
            combinedChainDataClient,
            storageUpdateChannel,
            p2pNetwork,
            blockImporter,
            pendingBlocks,
            getStartupTargetPeerCount,
            signatureVerificationService,
            startupTimeout,
            spec);
    return factory.create();
  }

  private SyncService create() {
    if (!syncConfig.isSyncEnabled()) {
      return new NoopSyncService();
    }

    final ForwardSyncService forwardSyncService = createForwardSyncService();
    final FetchRecentBlocksService recentBlockFetcher =
        FetchRecentBlocksService.create(asyncRunner, p2pNetwork, pendingBlocks, forwardSyncService);
    final SyncStateTracker syncStateTracker = createSyncStateTracker(forwardSyncService);
    final HistoricalBlockSyncService historicalBlockSyncService =
        createHistoricalSyncService(syncStateTracker);

    return new DefaultSyncService(
        forwardSyncService, recentBlockFetcher, syncStateTracker, historicalBlockSyncService);
  }

  private HistoricalBlockSyncService createHistoricalSyncService(
      final SyncStateProvider syncStateProvider) {
    final AsyncRunner asyncRunner =
        asyncRunnerFactory.create(HistoricalBlockSyncService.class.getSimpleName(), 1);
    return HistoricalBlockSyncService.create(
        spec,
        metrics,
        storageUpdateChannel,
        asyncRunner,
        p2pNetwork,
        combinedChainDataClient,
        signatureVerifier,
        syncStateProvider);
  }

  private SyncStateTracker createSyncStateTracker(final ForwardSync forwardSync) {
    return new SyncStateTracker(
        asyncRunner, forwardSync, p2pNetwork, getStartupTargetPeerCount, startupTimeout);
  }

  private ForwardSyncService createForwardSyncService() {
    final ForwardSyncService forwardSync;
    if (syncConfig.isMultiPeerSyncEnabled()) {
      forwardSync =
          MultipeerSyncService.create(
              asyncRunnerFactory,
              asyncRunner,
              timeProvider,
              recentChainData,
              pendingBlocks,
              p2pNetwork,
              blockImporter,
              spec);
    } else {
      forwardSync =
          SinglePeerSyncServiceFactory.create(
              metrics, asyncRunner, p2pNetwork, recentChainData, blockImporter, spec);
    }
    return forwardSync;
  }
}
