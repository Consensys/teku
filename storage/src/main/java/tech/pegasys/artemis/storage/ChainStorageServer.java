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

package tech.pegasys.artemis.storage;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Optional;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.storage.events.GetFinalizedBlockAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetFinalizedBlockAtSlotResponse;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateEvent;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class ChainStorageServer {
  private final Database database;
  private final EventBus eventBus;

  public ChainStorageServer(EventBus eventBus, ArtemisConfiguration config) {
    this.eventBus = eventBus;
    this.database = V1MapDatabase.createForFile("artemis.db", config.startFromDisk());
    eventBus.register(this);
    if (config.startFromDisk()) {
      Store memoryStore = database.createMemoryStore();
      eventBus.post(memoryStore);
    }
  }

  @Subscribe
  public void onStoreDiskUpdate(StoreDiskUpdateEvent storeDiskUpdateEvent) {
    Store.Transaction transaction = storeDiskUpdateEvent.getTransaction();
    database.insert(transaction);
  }

  @Subscribe
  public void onGetBlockBySlotRequest(final GetFinalizedBlockAtSlotRequest request) {
    final Optional<BeaconBlock> block =
        database.getFinalizedRootAtSlot(request.getSlot()).flatMap(database::getBlock);
    eventBus.post(new GetFinalizedBlockAtSlotResponse(request.getSlot(), block));
  }
}
