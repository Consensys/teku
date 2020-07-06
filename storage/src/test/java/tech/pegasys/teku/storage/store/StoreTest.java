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

package tech.pegasys.teku.storage.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.metrics.StubMetricsSystem;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannel;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannelWithDelays;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.async.SafeFuture;

class StoreTest {
  private final ChainBuilder chainBuilder = ChainBuilder.createDefault();

  @Test
  public void getSignedBlock_withLimitedCache() throws StateTransitionException {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          final SignedBeaconBlock expectedBlock = blockAndState.getBlock();
          final SignedBeaconBlock blockResult = store.getSignedBlock(expectedBlock.getRoot());
          assertThat(blockResult)
              .withFailMessage("Expected block %s to be available", expectedBlock.getSlot())
              .isEqualTo(expectedBlock);
        });
  }

  @Test
  public void getBlockState_withLimitedCache() throws StateTransitionException {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          final BeaconState result = store.getBlockState(blockAndState.getRoot());
          assertThat(result)
              .withFailMessage(
                  "Expected state for block %s to be available", blockAndState.getSlot())
              .isNotNull();
          assertThat(result.hash_tree_root())
              .isEqualTo(blockAndState.getBlock().getMessage().getState_root());
        });
  }

  @Test
  public void shouldApplyChangesWhenTransactionCommits() throws StateTransitionException {
    testApplyChangesWhenTransactionCommits(false);
  }

  @Test
  public void shouldApplyChangesWhenTransactionCommits_withInterleavedTx()
      throws StateTransitionException {
    testApplyChangesWhenTransactionCommits(true);
  }

  @Test
  public void getCheckpointState_shouldGenerateCheckpointStates() {
    final SignedBlockAndState genesisBlockAndState = chainBuilder.generateGenesis();
    final BlockProvider blockProvider = blockProviderFromChainBuilder();

    final UpdatableStore store =
        StoreBuilder.buildForkChoiceStore(
            new StubMetricsSystem(), blockProvider, genesisBlockAndState.getState());
    final Checkpoint checkpoint = new Checkpoint(UnsignedLong.ONE, genesisBlockAndState.getRoot());
    final BeaconState checkpointState = store.getCheckpointState(checkpoint);
    assertThat(checkpointState).isNotNull();
    assertThat(checkpointState.getSlot()).isEqualTo(checkpoint.getEpochStartSlot());
    assertThat(checkpointState.getLatest_block_header().hash_tree_root())
        .isEqualTo(checkpoint.getRoot());
  }

  @Test
  public void getCheckpointState_shouldThrowInvalidCheckpointExceptionWhenEpochBeforeBlockRoot()
      throws Exception {
    final SignedBlockAndState genesisBlockAndState = chainBuilder.generateGenesis();
    final BlockProvider blockProvider = blockProviderFromChainBuilder();
    final Bytes32 futureRoot =
        chainBuilder
            .generateBlockAtSlot(compute_start_slot_at_epoch(UnsignedLong.valueOf(2)))
            .getRoot();

    final UpdatableStore store =
        StoreBuilder.buildForkChoiceStore(
            new StubMetricsSystem(), blockProvider, genesisBlockAndState.getState());
    // Add blocks
    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    chainBuilder.streamBlocksAndStates().forEach(tx::putBlockAndState);
    tx.commit().join();

    final Checkpoint checkpoint = new Checkpoint(UnsignedLong.ONE, futureRoot);
    assertThatThrownBy(() -> store.getCheckpointState(checkpoint))
        .isInstanceOf(InvalidCheckpointException.class);
  }

  public void testApplyChangesWhenTransactionCommits(final boolean withInterleavedTransaction)
      throws StateTransitionException {
    final SignedBlockAndState genesisBlockAndState = chainBuilder.generateGenesis();
    final UnsignedLong epoch3Slot = compute_start_slot_at_epoch(UnsignedLong.valueOf(4));
    chainBuilder.generateBlocksUpToSlot(epoch3Slot);
    final BlockProvider blockProvider = blockProviderFromChainBuilder();

    final UpdatableStore store =
        StoreBuilder.buildForkChoiceStore(
            new StubMetricsSystem(), blockProvider, genesisBlockAndState.getState());
    final Checkpoint genesisCheckpoint = store.getFinalizedCheckpoint();
    final UnsignedLong initialTime = store.getTime();
    final UnsignedLong genesisTime = store.getGenesisTime();

    final Checkpoint checkpoint1 =
        chainBuilder.getCurrentCheckpointForEpoch(UnsignedLong.valueOf(1));
    final Checkpoint checkpoint2 =
        chainBuilder.getCurrentCheckpointForEpoch(UnsignedLong.valueOf(2));
    final Checkpoint checkpoint3 =
        chainBuilder.getCurrentCheckpointForEpoch(UnsignedLong.valueOf(3));

    // Start transaction
    final StubStorageUpdateChannelWithDelays updateChannel =
        new StubStorageUpdateChannelWithDelays();
    final StoreTransaction tx = store.startTransaction(updateChannel);
    // Add blocks
    chainBuilder.streamBlocksAndStates().forEach(tx::putBlockAndState);
    // Update checkpoints
    tx.setFinalizedCheckpoint(checkpoint1);
    tx.setJustifiedCheckpoint(checkpoint2);
    tx.setBestJustifiedCheckpoint(checkpoint3);
    // Update time
    tx.setTime(initialTime.plus(UnsignedLong.ONE));
    tx.setGenesis_time(genesisTime.plus(UnsignedLong.ONE));

    // Check that store is not yet updated
    // Check blocks
    chainBuilder
        .streamBlocksAndStates(1, chainBuilder.getLatestSlot().longValue())
        .forEach(b -> assertThat(store.containsBlock(b.getRoot())).isFalse());
    // Check checkpoints
    assertThat(store.getJustifiedCheckpoint()).isEqualTo(genesisCheckpoint);
    assertThat(store.getBestJustifiedCheckpoint()).isEqualTo(genesisCheckpoint);
    assertThat(store.getFinalizedCheckpoint()).isEqualTo(genesisCheckpoint);
    // Check time
    assertThat(store.getTime()).isEqualTo(initialTime);
    assertThat(store.getGenesisTime()).isEqualTo(genesisTime);

    // Check that transaction is updated
    chainBuilder
        .streamBlocksAndStates(1, chainBuilder.getLatestSlot().longValue())
        .forEach(b -> assertThat(tx.getBlockAndState(b.getRoot())).isEqualTo(Optional.of(b)));
    // Check checkpoints
    assertThat(tx.getFinalizedCheckpoint()).isEqualTo(checkpoint1);
    assertThat(tx.getJustifiedCheckpoint()).isEqualTo(checkpoint2);
    assertThat(tx.getBestJustifiedCheckpoint()).isEqualTo(checkpoint3);
    // Check time
    assertThat(tx.getTime()).isEqualTo(initialTime.plus(UnsignedLong.ONE));
    assertThat(tx.getGenesisTime()).isEqualTo(genesisTime.plus(UnsignedLong.ONE));

    // Commit transaction
    final SafeFuture<Void> txResult = tx.commit();

    final SafeFuture<Void> txResult2;
    if (withInterleavedTransaction) {
      UnsignedLong time = store.getTime().plus(UnsignedLong.ONE);
      StoreTransaction tx2 = store.startTransaction(updateChannel);
      tx2.setTime(time);
      txResult2 = tx2.commit();
    } else {
      txResult2 = SafeFuture.COMPLETE;
    }

    // Complete transactions
    assertThat(updateChannel.getAsyncRunner().countDelayedActions()).isLessThanOrEqualTo(2);
    updateChannel.getAsyncRunner().executeUntilDone();
    assertThat(txResult).isCompleted();
    assertThat(txResult2).isCompleted();

    // Check store is updated
    chainBuilder
        .streamBlocksAndStates(checkpoint3.getEpochStartSlot(), chainBuilder.getLatestSlot())
        .forEach(b -> assertThat(store.getBlockAndState(b.getRoot())).isEqualTo(Optional.of(b)));
    // Check checkpoints
    assertThat(store.getFinalizedCheckpoint()).isEqualTo(checkpoint1);
    assertThat(store.getJustifiedCheckpoint()).isEqualTo(checkpoint2);
    assertThat(store.getBestJustifiedCheckpoint()).isEqualTo(checkpoint3);
    // Check time
    assertThat(store.getTime()).isEqualTo(initialTime.plus(UnsignedLong.ONE));
    assertThat(store.getGenesisTime()).isEqualTo(genesisTime.plus(UnsignedLong.ONE));
  }

  void processChainWithLimitedCache(BiConsumer<UpdatableStore, SignedBlockAndState> chainProcessor)
      throws StateTransitionException {
    final int cacheSize = 10;
    final int cacheMultiplier = 3;

    // Create a new store with a small state cache
    final StorePruningOptions pruningOptions =
        StorePruningOptions.create(cacheSize, cacheSize, cacheSize);

    final Store store = createGenesisStore(pruningOptions);
    final List<SignedBlockAndState> blocks =
        chainBuilder.generateBlocksUpToSlot(cacheMultiplier * cacheSize);

    // Generate enough blocks to exceed our cache limit
    addBlocks(store, blocks);

    // Process chain in order
    blocks.forEach(b -> chainProcessor.accept(store, b));

    // Request states in reverse order
    Collections.reverse(blocks);
    blocks.forEach(b -> chainProcessor.accept(store, b));
  }

  private void addBlocks(final Store store, final List<SignedBlockAndState> blocks) {
    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    blocks.forEach(tx::putBlockAndState);
    assertThat(tx.commit()).isCompletedWithValue(null);
  }

  private Store createGenesisStore(final StorePruningOptions pruningOptions) {
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final Checkpoint genesisCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(0);
    return new Store(
        new StubMetricsSystem(),
        blockProviderFromChainBuilder(),
        genesis.getState().getGenesis_time(),
        genesis.getState().getGenesis_time(),
        genesisCheckpoint,
        genesisCheckpoint,
        genesisCheckpoint,
        Map.of(genesis.getRoot(), genesis.getParentRoot()),
        genesis,
        Collections.emptyMap(),
        pruningOptions);
  }

  private BlockProvider blockProviderFromChainBuilder() {
    return (roots) ->
        SafeFuture.completedFuture(
            roots.stream()
                .map(chainBuilder::getBlock)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity())));
  }
}
