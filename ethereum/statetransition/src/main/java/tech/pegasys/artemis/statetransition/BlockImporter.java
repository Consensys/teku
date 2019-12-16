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

package tech.pegasys.artemis.statetransition;

import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_block;

import com.google.common.eventbus.EventBus;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateEvent;

public class BlockImporter {

  private final ChainStorageClient storageClient;
  private final EventBus eventBus;
  private final StateTransition stateTransition = new StateTransition(false);

  public BlockImporter(ChainStorageClient storageClient, EventBus eventBus) {
    this.storageClient = storageClient;
    this.eventBus = eventBus;
  }

  public void importBlock(BeaconBlock block) throws StateTransitionException {
    Store.Transaction transaction = storageClient.getStore().startTransaction();
    on_block(transaction, block, stateTransition);
    final StoreDiskUpdateEvent storeEvent = transaction.commit();
    eventBus.post(storeEvent);
  }
}
