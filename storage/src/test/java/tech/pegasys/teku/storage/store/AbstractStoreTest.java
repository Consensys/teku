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

package tech.pegasys.teku.storage.store;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.lookup.StateAndBlockProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannel;

public abstract class AbstractStoreTest {
  protected final StorageUpdateChannel storageUpdateChannel = new StubStorageUpdateChannel();
  protected final ChainBuilder chainBuilder = ChainBuilder.createDefault();

  protected void processChainWithLimitedCache(
      BiConsumer<UpdatableStore, SignedBlockAndState> chainProcessor) {
    final int cacheSize = 10;
    final int cacheMultiplier = 3;

    // Create a new store with a small cache
    final StoreConfig pruningOptions =
        StoreConfig.builder()
            .checkpointStateCacheSize(cacheSize)
            .blockCacheSize(cacheSize)
            .stateCacheSize(cacheSize)
            .build();

    final UpdatableStore store = createGenesisStore(pruningOptions);
    final List<SignedBlockAndState> blocks =
        chainBuilder.generateBlocksUpToSlot(cacheMultiplier * cacheSize);

    // Generate enough blocks to exceed our cache limit
    addBlocks(store, blocks);

    // Process chain in order
    blocks.forEach(b -> chainProcessor.accept(store, b));
    blocks.forEach(b -> chainProcessor.accept(store, b));

    // Request states in reverse order
    Collections.reverse(blocks);
    blocks.forEach(b -> chainProcessor.accept(store, b));
    blocks.forEach(b -> chainProcessor.accept(store, b));
  }

  protected void processCheckpointsWithLimitedCache(
      BiConsumer<UpdatableStore, CheckpointState> chainProcessor) {
    final int cacheSize = 3;
    final int epochsToProcess = cacheSize * 3;

    // Create a new store with a small cache
    final StoreConfig pruningOptions =
        StoreConfig.builder()
            .checkpointStateCacheSize(cacheSize)
            .blockCacheSize(cacheSize)
            .stateCacheSize(cacheSize)
            .build();

    final UpdatableStore store = createGenesisStore(pruningOptions);
    while (chainBuilder.getLatestEpoch().longValue() < epochsToProcess) {
      SignedBlockAndState block = chainBuilder.generateNextBlock();
      addBlock(store, block);
    }
    // Collect checkpoints for each epoch
    final List<CheckpointState> allCheckpoints = new ArrayList<>();
    for (int i = 0; i <= chainBuilder.getLatestEpoch().intValue(); i++) {
      Checkpoint checkpoint = chainBuilder.getCurrentCheckpointForEpoch(i);
      SignedBlockAndState blockAndState = chainBuilder.getBlockAndState(checkpoint.getRoot()).get();
      allCheckpoints.add(
          new CheckpointState(checkpoint, blockAndState.getBlock(), blockAndState.getState()));
    }
    assertThat(allCheckpoints.size()).isEqualTo(epochsToProcess + 1);

    allCheckpoints.forEach(c -> chainProcessor.accept(store, c));
    allCheckpoints.forEach(c -> chainProcessor.accept(store, c));

    // Process in reverse order
    Collections.reverse(allCheckpoints);
    allCheckpoints.forEach(c -> chainProcessor.accept(store, c));
    allCheckpoints.forEach(c -> chainProcessor.accept(store, c));
  }

  protected void addBlock(final UpdatableStore store, final SignedBlockAndState block) {
    addBlocks(store, List.of(block));
  }

  protected void addBlocks(final UpdatableStore store, final List<SignedBlockAndState> blocks) {
    final UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    blocks.forEach(tx::putBlockAndState);
    assertThat(tx.commit()).isCompletedWithValue(null);
  }

  protected UpdatableStore createGenesisStore() {
    return createGenesisStore(StoreConfig.createDefault());
  }

  protected UpdatableStore createGenesisStore(final StoreConfig pruningOptions) {
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final Checkpoint genesisCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(0);
    final SafeFuture<UpdatableStore> result =
        Store.create(
            SYNC_RUNNER,
            new StubMetricsSystem(),
            blockProviderFromChainBuilder(),
            StateAndBlockProvider.NOOP,
            genesis.getState().getGenesis_time(),
            genesis.getState().getGenesis_time(),
            genesisCheckpoint,
            genesisCheckpoint,
            genesisCheckpoint,
            Map.of(genesis.getRoot(), genesis.getParentRoot()),
            Map.of(genesis.getRoot(), genesis.getSlot()),
            genesis,
            Collections.emptyMap(),
            pruningOptions);

    assertThat(result).isCompleted();
    return result.join();
  }

  protected BlockProvider blockProviderFromChainBuilder() {
    return (roots) ->
        SafeFuture.completedFuture(
            roots.stream()
                .map(chainBuilder::getBlock)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity())));
  }
}
