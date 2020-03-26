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

package tech.pegasys.artemis.storage.api;

import tech.pegasys.artemis.storage.events.diskupdates.DiskGenesisUpdate;
import tech.pegasys.artemis.storage.events.diskupdates.DiskUpdate;
import tech.pegasys.artemis.storage.events.diskupdates.DiskUpdateResult;
import tech.pegasys.artemis.util.async.SafeFuture;

public interface DiskUpdateChannel {

  SafeFuture<DiskUpdateResult> onDiskUpdate(DiskUpdate event);

  void onDiskGenesisUpdate(DiskGenesisUpdate event);
}
