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

package tech.pegasys.artemis.sync.util;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.sync.SyncService;
import tech.pegasys.artemis.sync.SyncStatus;
import tech.pegasys.artemis.util.async.SafeFuture;

public class NoopSyncService extends SyncService {

  public NoopSyncService(
      final EventBus eventBus,
      final Eth2Network network,
      final ChainStorageClient storageClient,
      final BlockImporter blockImporter) {
    super(eventBus, network, storageClient, blockImporter);
  }

  @Override
  protected SafeFuture<?> doStart() {
    return SafeFuture.completedFuture(null);
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.completedFuture(null);
  }

  @Override
  public SyncStatus getSyncStatus() {
    return new SyncStatus(false, UnsignedLong.ZERO, UnsignedLong.ZERO, UnsignedLong.ZERO);
  }
}
