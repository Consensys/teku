/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.cli.options;

import static tech.pegasys.teku.storage.store.StoreConfig.DEFAULT_EARLIEST_AVAILABLE_BLOCK_SLOT_QUERY_FREQUENCY;

import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.storage.store.StoreConfig;

public class StoreOptions {
  @Option(
      hidden = true,
      names = {"--Xhot-state-persistence-frequency"},
      paramLabel = "<INTEGER>",
      description =
          "How frequently to persist hot states in epochs.  A value less than or equal to zero disables hot state persistence.",
      arity = "1")
  private int hotStatePersistenceFrequencyInEpochs =
      StoreConfig.DEFAULT_HOT_STATE_PERSISTENCE_FREQUENCY_IN_EPOCHS;

  @Option(
      hidden = true,
      names = {"--Xstore-block-cache-size"},
      paramLabel = "<INTEGER>",
      description = "Number of blocks to cache in memory",
      arity = "1")
  private int blockCacheSize = StoreConfig.DEFAULT_BLOCK_CACHE_SIZE;

  @Option(
      hidden = true,
      names = {"--Xstore-state-cache-size"},
      paramLabel = "<INTEGER>",
      description = "Number of state to cache in memory",
      arity = "1")
  private int stateCacheSize = StoreConfig.DEFAULT_STATE_CACHE_SIZE;

  @Option(
      hidden = true,
      names = {"--Xstore-checkpoint-state-cache-size"},
      paramLabel = "<INTEGER>",
      description = "Number of checkpoint states to cache in memory",
      arity = "1")
  private int checkpointStateCacheSize = StoreConfig.DEFAULT_CHECKPOINT_STATE_CACHE_SIZE;

  @Option(
      names = {"--Xstore-earliest-available-block-slot-cache-seconds"},
      hidden = true,
      paramLabel = "<seconds>",
      description =
          "Only call earliest-available-block-slot every <SECONDS> seconds, which can reduce database contention serving large numbers of peers",
      arity = "1")
  private int earliestAvailableBlockSlotQueryFrequency =
      DEFAULT_EARLIEST_AVAILABLE_BLOCK_SLOT_QUERY_FREQUENCY;

  public void configure(final TekuConfiguration.Builder builder) {
    builder.store(
        b ->
            b.hotStatePersistenceFrequencyInEpochs(hotStatePersistenceFrequencyInEpochs)
                .blockCacheSize(blockCacheSize)
                .stateCacheSize(stateCacheSize)
                .earliestAvailableBlockSlotFrequency(earliestAvailableBlockSlotQueryFrequency)
                .checkpointStateCacheSize(checkpointStateCacheSize));
  }
}
