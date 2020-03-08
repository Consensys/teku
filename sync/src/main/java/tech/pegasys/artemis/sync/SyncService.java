/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.sync;

import com.google.common.eventbus.EventBus;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.async.SafeFuture;

public class SyncService extends Service {

  private final SyncManager syncManager;
  private final BlockPropagationManager blockPropagationManager;

  public SyncService(
      final EventBus eventBus,
      final P2PNetwork<Eth2Peer> network,
      final ChainStorageClient storageClient,
      final BlockImporter blockImporter) {
    this.syncManager = SyncManager.create(network, storageClient, blockImporter);
    this.blockPropagationManager =
        BlockPropagationManager.create(eventBus, network, storageClient, blockImporter);
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.allOf(syncManager.start(), blockPropagationManager.start());
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.allOf(syncManager.stop(), blockPropagationManager.stop());
  }

  public SyncingStatus getSyncStatus() {
    return syncManager.getSyncStatus();
  }
}
