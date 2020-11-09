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

import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.multipeer.MultipeerSyncService;
import tech.pegasys.teku.sync.noop.NoopSyncService;
import tech.pegasys.teku.sync.singlepeer.SinglePeerSyncServiceFactory;

public class SyncServiceFactory {

  public static SyncService create(
      final P2PConfig p2pConfig,
      final MetricsSystem metrics,
      final AsyncRunnerFactory asyncRunnerFactory,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final Eth2Network p2pNetwork,
      final BlockImporter blockImporter) {
    if (!p2pConfig.isP2pEnabled()) {
      return new NoopSyncService();
    }

    final SyncService forwardSync;
    if (p2pConfig.isMultiPeerSyncEnabled()) {
      forwardSync =
          MultipeerSyncService.create(
              asyncRunnerFactory,
              asyncRunner,
              timeProvider,
              recentChainData,
              p2pNetwork,
              blockImporter);
    } else {
      forwardSync =
          SinglePeerSyncServiceFactory.create(
              metrics, asyncRunner, p2pNetwork, recentChainData, blockImporter);
    }

    return forwardSync;
  }
}
