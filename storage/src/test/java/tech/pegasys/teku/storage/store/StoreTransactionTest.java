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

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class StoreTransactionTest extends AbstractStoreTest {

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
    final Store store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState);

    SafeFuture<Optional<SignedBeaconBlock>> result =
        tx.retrieveSignedBlock(blockAndState.getRoot());
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState.getBlock()));
  }

  @Test
  public void retrieveBlock_fromUnderlyingStore_withLimitedCache() throws StateTransitionException {
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
    final Store store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState);

    SafeFuture<Optional<BeaconBlock>> result = tx.retrieveBlock(blockAndState.getRoot());
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState.getBlock().getMessage()));
  }

  @Test
  public void retrieveBlockAndState_fromUnderlyingStore_withLimitedCache()
      throws StateTransitionException {
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
    final Store store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState);

    SafeFuture<Optional<SignedBlockAndState>> result =
        tx.retrieveBlockAndState(blockAndState.getRoot());
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState));
  }

  @Test
  public void retrieveBlockState_fromUnderlyingStore_withLimitedCache()
      throws StateTransitionException {
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
    final Store store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState);

    SafeFuture<Optional<BeaconState>> result = tx.retrieveBlockState(blockAndState.getRoot());
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState.getState()));
  }

  @Test
  public void retrieveCheckpointState_fromUnderlyingStore_withLimitedCache()
      throws StateTransitionException {
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
    final Store store = createGenesisStore();
    final UnsignedLong epoch = UnsignedLong.ONE;
    final UnsignedLong epochStartSlot = compute_start_slot_at_epoch(epoch);
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(epochStartSlot);
    final Checkpoint checkpoint = new Checkpoint(epoch, blockAndState.getRoot());

    UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putBlockAndState(blockAndState);

    SafeFuture<Optional<BeaconState>> result = tx.retrieveCheckpointState(checkpoint);
    assertThat(result).isCompletedWithValue(Optional.of(blockAndState.getState()));
  }
}
