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

package tech.pegasys.teku.api;

import tech.pegasys.teku.api.response.v1.node.Syncing;
import tech.pegasys.teku.api.schema.SyncStatus;
import tech.pegasys.teku.api.schema.SyncingStatus;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.sync.SyncService;

public class SyncDataProvider {

  private final SyncService syncService;

  public SyncDataProvider(SyncService syncService) {
    this.syncService = syncService;
  }

  /**
   * Get the sync status
   *
   * @return false if not syncing, otherwise true and a sync status object which indicates starting
   *     slot, current slot and highest slot.
   */
  public SyncingStatus getSyncStatus() {
    final tech.pegasys.teku.sync.SyncingStatus syncingStatus = syncService.getSyncStatus();

    final SyncStatus schemaSyncStatus =
        new SyncStatus(
            syncingStatus.getStartingSlot().orElse(null),
            syncingStatus.getCurrentSlot(),
            syncingStatus.getHighestSlot().orElse(null));

    return new SyncingStatus(syncingStatus.isSyncing(), schemaSyncStatus);
  }

  public Syncing getSyncing() {
    tech.pegasys.teku.sync.SyncingStatus syncStatus = syncService.getSyncStatus();
    return new Syncing(syncStatus.getCurrentSlot(), getSlotsBehind(syncStatus));
  }

  public boolean isSyncing() {
    return syncService.isSyncActive();
  }

  private UInt64 getSlotsBehind(final tech.pegasys.teku.sync.SyncingStatus syncingStatus) {
    if (syncingStatus.isSyncing() && syncingStatus.getHighestSlot().isPresent()) {
      final UInt64 highestSlot = syncingStatus.getHighestSlot().get();
      return highestSlot.minus(syncingStatus.getCurrentSlot());
    }
    return UInt64.ZERO;
  }
}
