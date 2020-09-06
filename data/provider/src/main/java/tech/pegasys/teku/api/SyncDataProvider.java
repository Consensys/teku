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
    final tech.pegasys.teku.sync.SyncStatus syncStatus = syncingStatus.getSyncStatus();

    final SyncStatus schemaSyncStatus;
    if (syncStatus != null) {
      schemaSyncStatus =
          new SyncStatus(
              syncStatus.getStartingSlot(),
              syncStatus.getCurrentSlot(),
              syncStatus.getHighestSlot());
    } else {
      schemaSyncStatus = new SyncStatus(null, null, null);
    }

    return new SyncingStatus(syncingStatus.isSyncing(), schemaSyncStatus);
  }

  public Syncing getSyncing() {
    tech.pegasys.teku.sync.SyncingStatus syncStatus = syncService.getSyncStatus();
    return new Syncing(syncStatus.getSyncStatus().getCurrentSlot(), getSlotsBehind(syncStatus));
  }

  private UInt64 getSlotsBehind(final tech.pegasys.teku.sync.SyncingStatus syncingStatus) {
    if (syncingStatus.isSyncing()) {
      final UInt64 highestSlot = syncingStatus.getSyncStatus().getHighestSlot();
      final UInt64 currentSlot = syncingStatus.getSyncStatus().getCurrentSlot();
      return highestSlot.minus(currentSlot);
    }
    return UInt64.ZERO;
  }
}
