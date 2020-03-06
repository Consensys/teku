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

package tech.pegasys.artemis.api;

import tech.pegasys.artemis.sync.SyncService;
import tech.pegasys.artemis.sync.SyncingStatus;

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
    return syncService.getSyncStatus();
  }

  SyncService getSyncService() {
    return syncService;
  }
}
