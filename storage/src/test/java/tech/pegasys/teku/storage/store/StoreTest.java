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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.metrics.StubMetricsSystem;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannel;
import tech.pegasys.teku.storage.store.Store.StateProvider;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.async.SafeFuture;

class StoreTest {
  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);
  private final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);

  @Test
  public void getBlockState_withLimitedStateCache() throws StateTransitionException {
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final Checkpoint genesisCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(0);
    // Create a new store with a small state cache
    final int stateCacheSize = 10;
    final Store store =
        new Store(
            new StubMetricsSystem(),
            genesis.getState().getGenesis_time(),
            genesis.getState().getGenesis_time(),
            genesisCheckpoint,
            genesisCheckpoint,
            genesisCheckpoint,
            Map.of(genesis.getRoot(), genesis.getBlock()),
            StateProvider.NOOP,
            Map.of(genesisCheckpoint, genesis.getState()),
            genesis.getState(),
            Collections.emptyMap(),
            stateCacheSize);

    // Generate enough blocks to exceed our cache limit
    final List<SignedBlockAndState> blocks =
        chainBuilder.generateBlocksUpToSlot(3 * stateCacheSize);
    addBlocks(store, blocks);

    // Request states in order
    for (SignedBlockAndState block : blocks) {
      final BeaconState result = store.getBlockState(block.getRoot());
      assertThat(result).isNotNull();
      assertThat(result.hash_tree_root()).isEqualTo(block.getBlock().getMessage().getState_root());
    }

    // Request states in reverse order
    Collections.reverse(blocks);
    for (SignedBlockAndState block : blocks) {
      final BeaconState result = store.getBlockState(block.getRoot());
      assertThat(result).isNotNull();
      assertThat(result.hash_tree_root()).isEqualTo(block.getBlock().getMessage().getState_root());
    }
  }

  @Test
  public void shouldApplyChangesWhenTransactionCommits() throws StateTransitionException {
    final SignedBlockAndState genesisBlockAndState = chainBuilder.generateGenesis();
    final UpdatableStore store =
        StoreFactory.getForkChoiceStore(new StubMetricsSystem(), genesisBlockAndState.getState());
    final Checkpoint genesisCheckpoint = store.getFinalizedCheckpoint();
    final UnsignedLong initialTime = store.getTime();
    final UnsignedLong genesisTime = store.getGenesisTime();

    final UnsignedLong epoch3Slot = compute_start_slot_at_epoch(UnsignedLong.valueOf(4));
    chainBuilder.generateBlocksUpToSlot(epoch3Slot);
    final Checkpoint checkpoint1 =
        chainBuilder.getCurrentCheckpointForEpoch(UnsignedLong.valueOf(1));
    final Checkpoint checkpoint2 =
        chainBuilder.getCurrentCheckpointForEpoch(UnsignedLong.valueOf(2));
    final Checkpoint checkpoint3 =
        chainBuilder.getCurrentCheckpointForEpoch(UnsignedLong.valueOf(3));

    // Start transaction
    StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);
    when(storageUpdateChannel.onStorageUpdate(any())).thenReturn(SafeFuture.COMPLETE);
    final StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    // Add blocks
    chainBuilder.streamBlocksAndStates().forEach(tx::putBlockAndState);
    // Update checkpoints
    tx.setFinalizedCheckpoint(checkpoint1);
    tx.setJustifiedCheckpoint(checkpoint2);
    tx.setBestJustifiedCheckpoint(checkpoint3);
    // Update checkpoint states
    tx.putCheckpointState(
        checkpoint1, chainBuilder.getStateAtSlot(checkpoint1.getEpochStartSlot()));
    tx.putCheckpointState(
        checkpoint2, chainBuilder.getStateAtSlot(checkpoint2.getEpochStartSlot()));
    tx.putCheckpointState(
        checkpoint3, chainBuilder.getStateAtSlot(checkpoint3.getEpochStartSlot()));
    // Update time
    tx.setTime(initialTime.plus(UnsignedLong.ONE));
    tx.setGenesis_time(genesisTime.plus(UnsignedLong.ONE));

    // Check that store is not yet updated
    // Check blocks
    chainBuilder
        .streamBlocksAndStates(1, chainBuilder.getLatestSlot().longValue())
        .forEach(
            b -> {
              assertThat(store.containsBlock(b.getRoot())).isFalse();
            });
    // Check checkpoints
    assertThat(store.getJustifiedCheckpoint()).isEqualTo(genesisCheckpoint);
    assertThat(store.getBestJustifiedCheckpoint()).isEqualTo(genesisCheckpoint);
    assertThat(store.getFinalizedCheckpoint()).isEqualTo(genesisCheckpoint);
    // Check checkpoint states
    assertThat(store.containsCheckpointState(checkpoint1)).isFalse();
    assertThat(store.containsCheckpointState(checkpoint2)).isFalse();
    assertThat(store.containsCheckpointState(checkpoint3)).isFalse();
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
    // Check checkpoint states
    assertThat(tx.containsCheckpointState(checkpoint1)).isTrue();
    assertThat(tx.containsCheckpointState(checkpoint2)).isTrue();
    assertThat(tx.containsCheckpointState(checkpoint3)).isTrue();
    // Check time
    assertThat(tx.getTime()).isEqualTo(initialTime.plus(UnsignedLong.ONE));
    assertThat(tx.getGenesisTime()).isEqualTo(genesisTime.plus(UnsignedLong.ONE));

    // Commit transaction
    assertThat(tx.commit()).isCompleted();

    // Check store is updated
    chainBuilder
        .streamBlocksAndStates(checkpoint3.getEpochStartSlot(), chainBuilder.getLatestSlot())
        .forEach(b -> assertThat(store.getBlockAndState(b.getRoot())).isEqualTo(Optional.of(b)));
    // Check checkpoints
    assertThat(store.getFinalizedCheckpoint()).isEqualTo(checkpoint1);
    assertThat(store.getJustifiedCheckpoint()).isEqualTo(checkpoint2);
    assertThat(store.getBestJustifiedCheckpoint()).isEqualTo(checkpoint3);
    // Check checkpoint states
    assertThat(store.containsCheckpointState(checkpoint1)).isTrue();
    assertThat(store.containsCheckpointState(checkpoint2)).isTrue();
    assertThat(store.containsCheckpointState(checkpoint3)).isTrue();
    // Check time
    assertThat(store.getTime()).isEqualTo(initialTime.plus(UnsignedLong.ONE));
    assertThat(store.getGenesisTime()).isEqualTo(genesisTime.plus(UnsignedLong.ONE));
  }

  private void addBlocks(final Store store, final List<SignedBlockAndState> blocks) {
    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    blocks.forEach(tx::putBlockAndState);
    assertThat(tx.commit()).isCompletedWithValue(null);
  }
}
