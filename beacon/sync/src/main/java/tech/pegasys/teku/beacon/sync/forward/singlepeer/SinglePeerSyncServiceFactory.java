/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beacon.sync.forward.singlepeer;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.beacon.sync.forward.ForwardSyncService;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarPool;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SinglePeerSyncServiceFactory {
  public static ForwardSyncService create(
      final MetricsSystem metricsSystem,
      final AsyncRunner asyncRunner,
      final P2PNetwork<Eth2Peer> p2pNetwork,
      final RecentChainData recentChainData,
      final BlockImporter blockImporter,
      final BlobSidecarManager blobSidecarManager,
      final BlobSidecarPool blobSidecarPool,
      final int batchSize,
      final Spec spec) {
    final SyncManager syncManager =
        SyncManager.create(
            asyncRunner,
            p2pNetwork,
            recentChainData,
            blockImporter,
            blobSidecarManager,
            blobSidecarPool,
            metricsSystem,
            batchSize,
            spec);
    return new SinglePeerSyncService(syncManager, recentChainData);
  }
}
