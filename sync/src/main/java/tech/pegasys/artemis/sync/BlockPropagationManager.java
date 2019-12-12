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
import com.google.common.eventbus.Subscribe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.statetransition.BlockImporter;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.events.BlockImportedEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.HistoricalChainData;

public class BlockPropagationManager {
  private static final Logger LOG = LogManager.getLogger();

  private final EventBus eventBus;
  private final ChainStorageClient storageClient;
  private final HistoricalChainData historicalChainData;
  private final BlockImporter blockImporter;

  public BlockPropagationManager(
      final EventBus eventBus,
      final ChainStorageClient storageClient,
      final HistoricalChainData historicalChainData,
      final BlockImporter blockImporter) {
    this.eventBus = eventBus;
    this.storageClient = storageClient;
    this.historicalChainData = historicalChainData;
    this.blockImporter = blockImporter;

    this.eventBus.register(this);
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onBlock(BeaconBlock block) {
    try {
      // TODO - check if block is attached, if so import it, otherwise add it to our pending pool
      blockImporter.importBlock(block);
    } catch (StateTransitionException e) {
      LOG.warn("Unable to import propagated block " + block, e);
    }
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onBlockImported(BlockImportedEvent blockImportedEvent) {
    // TODO - check to see if any pending blocks can now be imported
  }

  public void shutdown() {
    eventBus.unregister(this);
  }
}
