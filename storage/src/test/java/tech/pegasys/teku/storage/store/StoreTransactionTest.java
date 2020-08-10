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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.config.Constants;

public class StoreTransactionTest extends AbstractStoreTest {

  @Test
  public void getLatestFinalizedBlockAndState_fromUnderlyingStore() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState expected = store.getLatestFinalizedBlockAndState();

    final StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    assertThat(tx.getLatestFinalizedBlockAndState()).isEqualTo(expected);
  }

  @Test
  public void getLatestFinalizedBlockAndState_withNewFinalizedCheckpoint_blockInUnderlyingStore() {
    final UpdatableStore store = createGenesisStore();

    // Create some blocks that we can finalize
    final UInt64 epoch = UInt64.ONE;
    final SignedBlockAndState finalizedBlock =
        chainBuilder.generateBlockAtSlot(compute_start_slot_at_epoch(epoch));
    final Checkpoint finalizedCheckpoint = new Checkpoint(epoch, finalizedBlock.getRoot());

    // Save the finalized block
    addBlock(store, finalizedBlock);

    final StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.setFinalizedCheckpoint(finalizedCheckpoint);
    assertThat(tx.getLatestFinalizedBlockAndState()).isEqualTo(finalizedBlock);
  }

  @Test
  public void getLatestFinalizedBlockAndState_withNewFinalizedCheckpoint_blockAddedToTx() {
    final UpdatableStore store = createGenesisStore();

    // Create some blocks that we can finalize
    final UInt64 epoch = UInt64.ONE;
    final SignedBlockAndState finalizedBlock =
        chainBuilder.generateBlockAtSlot(compute_start_slot_at_epoch(epoch));
    final Checkpoint finalizedCheckpoint = new Checkpoint(epoch, finalizedBlock.getRoot());

    final StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(finalizedBlock);
    tx.setFinalizedCheckpoint(finalizedCheckpoint);
    assertThat(tx.getLatestFinalizedBlockAndState()).isEqualTo(finalizedBlock);
  }

  @Test
  public void retrieveSignedBlock_fromUnderlyingStore_withLimitedCache() throws Exception {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
          final Bytes32 root = blockAndState.getRoot();
          final SignedBeaconBlock expectedBlock = blockAndState.getBlock();
          SafeFuture<Optional<SignedBeaconBlock>> result = tx.retrieveSignedBlock(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .withFailMessage("Expected block %s to be available", expectedBlock.getSlot())
              .isCompletedWithValue(Optional.of(expectedBlock));
        });
  }

  @Test
  public void retrieveSignedBlock_fromTx() throws Exception {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState);

    SafeFuture<Optional<SignedBeaconBlock>> result =
        tx.retrieveSignedBlock(blockAndState.getRoot());
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState.getBlock()));
  }

  @Test
  public void retrieveBlock_fromUnderlyingStore_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
          final Bytes32 root = blockAndState.getRoot();
          final BeaconBlock expectedBlock = blockAndState.getBlock().getMessage();
          SafeFuture<Optional<BeaconBlock>> result = tx.retrieveBlock(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .withFailMessage("Expected block %s to be available", expectedBlock.getSlot())
              .isCompletedWithValue(Optional.of(expectedBlock));
        });
  }

  @Test
  public void retrieveBlock_fromTx() throws Exception {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState);

    SafeFuture<Optional<BeaconBlock>> result = tx.retrieveBlock(blockAndState.getRoot());
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState.getBlock().getMessage()));
  }

  @Test
  public void retrieveBlockAndState_fromUnderlyingStore_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
          final Bytes32 root = blockAndState.getRoot();
          SafeFuture<Optional<SignedBlockAndState>> result = tx.retrieveBlockAndState(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .withFailMessage(
                  "Expected block and state at %s to be available", blockAndState.getSlot())
              .isCompletedWithValue(Optional.of(blockAndState));
        });
  }

  @Test
  public void retrieveBlockAndState_fromTx() throws Exception {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState);

    SafeFuture<Optional<SignedBlockAndState>> result =
        tx.retrieveBlockAndState(blockAndState.getRoot());
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState));
  }

  @Test
  public void retrieveBlockState_fromUnderlyingStore_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
          final Bytes32 root = blockAndState.getRoot();
          SafeFuture<Optional<BeaconState>> result = tx.retrieveBlockState(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .withFailMessage("Expected state at %s to be available", blockAndState.getSlot())
              .isCompletedWithValue(Optional.of(blockAndState.getState()));
        });
  }

  @Test
  public void retrieveBlockState_fromTx() throws Exception {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState);

    SafeFuture<Optional<BeaconState>> result = tx.retrieveBlockState(blockAndState.getRoot());
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState.getState()));
  }

  @Test
  public void retrieveCheckpointState_fromUnderlyingStore_withLimitedCache() {
    processCheckpointsWithLimitedCache(
        (store, checkpointState) -> {
          UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
          SafeFuture<Optional<BeaconState>> result =
              tx.retrieveCheckpointState(checkpointState.getCheckpoint());
          assertThat(result)
              .withFailMessage(
                  "Expected checkpoint state for checkpoint %s", checkpointState.getCheckpoint())
              .isCompletedWithValue(Optional.of(checkpointState.getState()));
        });
  }

  @Test
  public void getCheckpointState_fromBlockInTx() throws Exception {
    final UpdatableStore store = createGenesisStore();
    final UInt64 epoch = UInt64.ONE;
    final UInt64 epochStartSlot = compute_start_slot_at_epoch(epoch);
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(epochStartSlot);
    final Checkpoint checkpoint = new Checkpoint(epoch, blockAndState.getRoot());

    UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState);

    SafeFuture<Optional<BeaconState>> result = tx.retrieveCheckpointState(checkpoint);
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState.getState()));
  }

  @Test
  public void getOrderedBlockRoots_withNewBlocks() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState genesis = chainBuilder.getBlockAndStateAtSlot(Constants.GENESIS_SLOT);

    final ChainBuilder fork = chainBuilder.fork();
    final SignedBlockAndState forkBlock2 = fork.generateBlockAtSlot(2);

    final SignedBlockAndState mainChainBlock1 = chainBuilder.generateBlockAtSlot(1);
    final SignedBlockAndState mainChainBlock3 = chainBuilder.generateBlockAtSlot(3);
    final SignedBlockAndState mainChainBlock4 = chainBuilder.generateBlockAtSlot(4);

    addBlocks(store, List.of(mainChainBlock1, mainChainBlock3, mainChainBlock4));
    final StoreTransaction tx = store.startTransaction(storageUpdateChannel);

    // Initially we should get existing block hashes
    assertThat(tx.getOrderedBlockRoots())
        .containsExactly(
            genesis.getRoot(),
            mainChainBlock1.getRoot(),
            mainChainBlock3.getRoot(),
            mainChainBlock4.getRoot());

    // Added block should be included
    tx.putBlockAndState(forkBlock2);

    // Children are ordered based on hash - so check ordering depending on specific hashes
    if (mainChainBlock1.getRoot().compareTo(forkBlock2.getRoot()) < 0) {
      assertThat(tx.getOrderedBlockRoots())
          .containsExactly(
              genesis.getRoot(),
              mainChainBlock1.getRoot(),
              forkBlock2.getRoot(),
              mainChainBlock3.getRoot(),
              mainChainBlock4.getRoot());
    } else {
      assertThat(tx.getOrderedBlockRoots())
          .containsExactly(
              genesis.getRoot(),
              forkBlock2.getRoot(),
              mainChainBlock1.getRoot(),
              mainChainBlock3.getRoot(),
              mainChainBlock4.getRoot());
    }
  }
}
