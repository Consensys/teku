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

import static com.google.common.primitives.UnsignedLong.ZERO;

import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.sync.BlockPropagationManager;
import tech.pegasys.artemis.sync.SyncManager;
import tech.pegasys.artemis.sync.SyncService;
import tech.pegasys.artemis.sync.SyncStatus;
import tech.pegasys.artemis.sync.SyncingStatus;
import tech.pegasys.artemis.util.async.SafeFuture;

public class NoopSyncService extends SyncService {

  public NoopSyncService(
      final BlockPropagationManager blockPropagationManager,
      final SyncManager syncManager,
      final ChainStorageClient storageClient) {
    super(blockPropagationManager, syncManager, storageClient);
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
  public SyncingStatus getSyncStatus() {
    return new SyncingStatus(false, new SyncStatus(ZERO, ZERO, ZERO));
  }
}
