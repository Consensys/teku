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

package tech.pegasys.artemis.sync;

import com.google.common.base.MoreObjects;

public class SyncingStatus {
  private final boolean syncing;
  private final SyncStatus syncStatus;

  public SyncingStatus(final boolean syncing, final SyncStatus syncStatus) {
    this.syncing = syncing;
    this.syncStatus = syncStatus;
  }

  public boolean isSyncing() {
    return syncing;
  }

  public SyncStatus getSyncStatus() {
    return syncStatus;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("syncing", syncing)
        .add("syncStatus", syncStatus)
        .toString();
  }
}
