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
import java.util.concurrent.CompletableFuture;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.statetransition.BlockImporter;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class SyncService extends Service {

  private final SyncManager syncManager;
  private final BlockPropagationManager blockPropagationManager;

  public SyncService(
      final EventBus eventBus,
      final Eth2Network network,
      final ChainStorageClient storageClient,
      final BlockImporter blockImporter) {
    this.syncManager = new SyncManager(network, storageClient, blockImporter);
    this.blockPropagationManager =
        BlockPropagationManager.create(eventBus, storageClient, blockImporter);
  }

  @Override
  protected CompletableFuture<?> doStart() {
    final CompletableFuture<?> propagationManagerFuture = blockPropagationManager.start();
    syncManager.sync();
    return propagationManagerFuture;
  }

  @Override
  protected CompletableFuture<?> doStop() {
    return blockPropagationManager.stop();
  }
}
