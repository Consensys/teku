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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.Store.Transaction;
import tech.pegasys.artemis.storage.events.FinalizedCheckpointEvent;
import tech.pegasys.artemis.storage.events.GetStoreRequest;
import tech.pegasys.artemis.storage.events.GetStoreResponse;
import tech.pegasys.artemis.storage.events.StoreInitializedFromStorageEvent;
import tech.pegasys.artemis.util.EventSink;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

class ChainStorageClientTest {

  private static final BeaconState INITIAL_STATE =
      DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, 1);
  private static final Bytes32 BEST_BLOCK_ROOT = Bytes32.fromHexString("0x8462485245");
  private static final BeaconBlock GENESIS_BLOCK = DataStructureUtil.randomBeaconBlock(0, 1);
  private static final Bytes32 GENESIS_BLOCK_ROOT = GENESIS_BLOCK.hash_tree_root();
  private final EventBus eventBus = mock(EventBus.class);
  private final Store store = mock(Store.class);
  private final ChainStorageClient storageClient =
      ChainStorageClient.memoryOnlyClientWithStore(eventBus, store);
  private final ChainStorageClient preGenesisStorageClient =
      ChainStorageClient.memoryOnlyClient(eventBus);
  private int seed = 428942;

  @Test
  public void storageBackedClient_storeInitializeViaGetStoreRequest()
      throws ExecutionException, InterruptedException {
    final EventBus eventBus = new EventBus();
    final List<GetStoreRequest> getStoreRequests =
        EventSink.capture(eventBus, GetStoreRequest.class);
    final SafeFuture<ChainStorageClient> client = ChainStorageClient.storageBackedClient(eventBus);

    // We should have posted a request to get the store from storage
    assertThat(getStoreRequests.size()).isEqualTo(1);
    // Client shouldn't be initialized yet
    assertThat(client).isNotDone();

    // Post unrelated event
    final Store genesisStore = Store.get_genesis_store(INITIAL_STATE);
    eventBus.post(
        new GetStoreResponse(getStoreRequests.get(0).getId() + 1, Optional.of(genesisStore)));
    assertThat(client).isNotDone();

    // Post a store response to complete initialization
    eventBus.post(new GetStoreResponse(getStoreRequests.get(0).getId(), Optional.of(genesisStore)));
    assertThat(client).isCompleted();
    assertStoreInitialized(client.get());
    assertStoreIsSet(client.get());
    assertThat(client.get().getStore()).isEqualTo(genesisStore);

    // Post store initialization event which should be ignored because we're already initialized
    final BeaconState otherState = DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, seed++);
    assertThat(otherState).isNotEqualTo(INITIAL_STATE);
    final Store otherStore = Store.get_genesis_store(otherState);
    eventBus.post(new StoreInitializedFromStorageEvent(Optional.of(otherStore)));
    assertStoreIsSet(client.get());
    assertThat(client.get().getStore()).isEqualTo(genesisStore);
  }

  @Test
  public void storageBackedClient_storeInitializeViaNewGenesisState()
      throws ExecutionException, InterruptedException {
    final EventBus eventBus = new EventBus();
    final List<GetStoreRequest> getStoreRequests =
        EventSink.capture(eventBus, GetStoreRequest.class);
    final SafeFuture<ChainStorageClient> client = ChainStorageClient.storageBackedClient(eventBus);

    // We should have posted a request to get the store from storage
    assertThat(getStoreRequests.size()).isEqualTo(1);
    // Client shouldn't be initialized yet
    assertThat(client).isNotDone();

    // Post a store event to complete initialization
    eventBus.post(new GetStoreResponse(getStoreRequests.get(0).getId(), Optional.empty()));
    assertThat(client).isCompleted();
    assertStoreNotInitialized(client.get());
    assertThat(client.get().getStore()).isNull();

    // Now set the genesis state
    final Store genesisStore = Store.get_genesis_store(INITIAL_STATE);
    client.get().initializeFromGenesis(INITIAL_STATE);
    assertStoreInitialized(client.get());
    assertStoreIsSet(client.get());
    assertThat(client.get().getStore()).isEqualTo(genesisStore);
  }

  @Test
  public void storageBackedClient_storeInitializeViaStoreInitializedEvent()
      throws ExecutionException, InterruptedException {
    final EventBus eventBus = new EventBus();
    final List<GetStoreRequest> getStoreRequests =
        EventSink.capture(eventBus, GetStoreRequest.class);
    final SafeFuture<ChainStorageClient> client = ChainStorageClient.storageBackedClient(eventBus);

    // We should have posted a request to get the store from storage
    assertThat(getStoreRequests.size()).isEqualTo(1);
    // Client shouldn't be initialized yet
    assertThat(client).isNotDone();

    // Post initialization event
    final Store genesisStore = Store.get_genesis_store(INITIAL_STATE);
    eventBus.post(new StoreInitializedFromStorageEvent(Optional.of(genesisStore)));
    assertThat(client).isCompleted();
    assertStoreInitialized(client.get());
    assertStoreIsSet(client.get());
    assertThat(client.get().getStore()).isEqualTo(genesisStore);

    // Post getStore response - which shouldn't change the store
    final BeaconState otherState = DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, seed++);
    assertThat(otherState).isNotEqualTo(INITIAL_STATE);
    final Store otherStore = Store.get_genesis_store(otherState);
    eventBus.post(new GetStoreResponse(getStoreRequests.get(0).getId(), Optional.of(otherStore)));
    assertStoreIsSet(client.get());
    assertThat(client.get().getStore()).isEqualTo(genesisStore);
  }

  @Test
  public void storageBackedClient_storeInitializeViaStoreInitializedEvent_emptyStore()
      throws ExecutionException, InterruptedException {
    final EventBus eventBus = new EventBus();
    final List<GetStoreRequest> getStoreRequests =
        EventSink.capture(eventBus, GetStoreRequest.class);
    final SafeFuture<ChainStorageClient> client = ChainStorageClient.storageBackedClient(eventBus);

    // We should have posted a request to get the store from storage
    assertThat(getStoreRequests.size()).isEqualTo(1);
    // Client shouldn't be initialized yet
    assertThat(client).isNotDone();

    // Post initialization event
    eventBus.post(new StoreInitializedFromStorageEvent(Optional.empty()));
    assertThat(client).isCompleted();
    assertStoreNotInitialized(client.get());
    assertThat(client.get().getStore()).isNull();

    // Now set the genesis state
    final Store genesisStore = Store.get_genesis_store(INITIAL_STATE);
    client.get().initializeFromGenesis(INITIAL_STATE);
    assertStoreInitialized(client.get());
    assertStoreIsSet(client.get());
    assertThat(client.get().getStore()).isEqualTo(genesisStore);
  }

  @Test
  public void initialize_setupInitialState() {
    preGenesisStorageClient.initializeFromGenesis(INITIAL_STATE);
    assertThat(preGenesisStorageClient.getGenesisTime()).isEqualTo(INITIAL_STATE.getGenesis_time());
    assertThat(preGenesisStorageClient.getBestSlot())
        .isEqualTo(UnsignedLong.valueOf(Constants.GENESIS_SLOT));
    assertThat(preGenesisStorageClient.getBestBlockRootState()).hasValue(INITIAL_STATE);
    assertThat(preGenesisStorageClient.getStore()).isNotNull();
  }

  @Test
  public void getBlockBySlot_returnEmptyWhenStoreNotSet() {
    assertThat(preGenesisStorageClient.getBlockBySlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getBlockBySlot_returnEmptyWhenBestBlockNotSet() {
    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getBlockBySlot_returnGenesisBlockWhenItIsTheBestState() {
    storageClient.updateBestBlock(GENESIS_BLOCK_ROOT, UnsignedLong.ZERO);

    final MutableBeaconState bestState =
        DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, seed++).createWritableCopy();
    // At the start of the chain, the slot number is the index into historical roots
    bestState.getBlock_roots().set(0, GENESIS_BLOCK_ROOT);
    when(store.getBlockState(GENESIS_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(GENESIS_BLOCK_ROOT)).thenReturn(GENESIS_BLOCK);

    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).contains(GENESIS_BLOCK);
  }

  @Test
  public void getBlockBySlot_returnGenesisBlockWhenItIsNotTheBestState() {
    final UnsignedLong bestSlot = UnsignedLong.ONE;
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, bestSlot);

    final MutableBeaconState bestState =
        DataStructureUtil.randomBeaconState(bestSlot, seed++).createWritableCopy();
    final BeaconBlock genesisBlock = GENESIS_BLOCK;
    final Bytes32 genesisBlockHash = genesisBlock.hash_tree_root();
    // At the start of the chain, the slot number is the index into historical roots
    bestState.getBlock_roots().set(0, genesisBlockHash);
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(genesisBlockHash)).thenReturn(genesisBlock);

    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).contains(genesisBlock);
  }

  @Test
  public void getBlockBySlot_returnEmptyWhenSlotBeforeHistoricalRootWindow() {
    // First slot where the block roots start overwriting themselves and dropping history
    final UnsignedLong bestSlot =
        UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT).plus(UnsignedLong.ONE);
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, bestSlot);

    final MutableBeaconState bestState =
        DataStructureUtil.randomBeaconState(bestSlot, seed++).createWritableCopy();
    // Overwrite the genesis hash with a newer block.
    final BeaconBlock newerBlock =
        DataStructureUtil.randomBeaconBlock(bestSlot.longValue() - 1, seed++);
    final Bytes32 newerBlockRoot = newerBlock.hash_tree_root();
    bestState.getBlock_roots().set(0, newerBlockRoot);
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(GENESIS_BLOCK_ROOT)).thenReturn(GENESIS_BLOCK);
    when(store.getBlock(newerBlockRoot)).thenReturn(newerBlock);

    // Slot 0 has now been overwritten by our current best slot so we can't get that block anymore.
    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getBlockBySlot_returnCorrectBlockFromHistoricalWindow() {
    // We've wrapped around a lot of times and are 5 slots into the next "loop"
    final int historicalIndex = 5;
    final UnsignedLong requestedSlot =
        UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT)
            .times(UnsignedLong.valueOf(900000000))
            .plus(UnsignedLong.valueOf(historicalIndex));
    // Avoid the simple case where the requested slot is the best slot so we have to go to the
    // historic blocks
    final UnsignedLong bestSlot = requestedSlot.plus(UnsignedLong.ONE);

    storageClient.updateBestBlock(BEST_BLOCK_ROOT, bestSlot);

    final MutableBeaconState bestState =
        DataStructureUtil.randomBeaconState(bestSlot, seed++).createWritableCopy();
    final BeaconBlock bestBlock =
        DataStructureUtil.randomBeaconBlock(requestedSlot.longValue(), seed++);
    final Bytes32 bestBlockHash = bestBlock.hash_tree_root();
    // Overwrite the genesis hash.
    bestState.getBlock_roots().set(historicalIndex, bestBlockHash);
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(bestBlockHash)).thenReturn(bestBlock);

    assertThat(storageClient.getBlockBySlot(requestedSlot)).contains(bestBlock);
  }

  @Test
  public void getBlockBySlot_returnEmptyWhenSlotWasEmpty() {
    // Requesting block for an empty slot immediately after BLOCK1
    final UnsignedLong reqeustedSlot = GENESIS_BLOCK.getSlot().plus(UnsignedLong.ONE);
    final UnsignedLong bestSlot = reqeustedSlot.plus(UnsignedLong.ONE);

    storageClient.updateBestBlock(BEST_BLOCK_ROOT, bestSlot);

    final MutableBeaconState bestState =
        DataStructureUtil.randomBeaconState(bestSlot, seed++).createWritableCopy();
    final Bytes32 block1Hash = GENESIS_BLOCK.hash_tree_root();
    // The root for BLOCK1 is copied over from it's slot to be the best block at the requested slot
    bestState.getBlock_roots().set(GENESIS_BLOCK.getSlot().intValue(), block1Hash);
    bestState.getBlock_roots().set(reqeustedSlot.intValue(), block1Hash);
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(block1Hash)).thenReturn(GENESIS_BLOCK);

    assertThat(storageClient.getBlockBySlot(reqeustedSlot)).isEmpty();
  }

  @Test
  void getStateBySlot_returnEmptyWhenStoreNotSet() {
    assertThat(preGenesisStorageClient.getStateBySlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getStateBySlot_returnGenesisBlockWhenItIsTheBestState() {
    storageClient.updateBestBlock(GENESIS_BLOCK_ROOT, UnsignedLong.ZERO);

    final MutableBeaconState bestState =
        DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, seed++).createWritableCopy();
    bestState.getBlock_roots().set(0, GENESIS_BLOCK_ROOT);
    when(store.getBlockState(GENESIS_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(GENESIS_BLOCK_ROOT)).thenReturn(GENESIS_BLOCK);

    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).contains(GENESIS_BLOCK);
  }

  @Test
  public void isIncludedInBestState_falseWhenNoStoreSet() {
    assertThat(preGenesisStorageClient.isIncludedInBestState(GENESIS_BLOCK_ROOT)).isFalse();
  }

  @Test
  public void isIncludedInBestState_falseWhenBestBlockNotSet() {
    assertThat(storageClient.isIncludedInBestState(GENESIS_BLOCK_ROOT)).isFalse();
  }

  @Test
  public void isIncludedInBestState_falseWhenNoBlockAtSlot() {
    final UnsignedLong bestSlot = UnsignedLong.ONE;
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, bestSlot);

    // bestState has no historical block roots so definitely nothing canonical at the block's slot
    final MutableBeaconState bestState =
        DataStructureUtil.randomBeaconState(bestSlot, seed++).createWritableCopy();
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(GENESIS_BLOCK_ROOT)).thenReturn(GENESIS_BLOCK);

    assertThat(storageClient.isIncludedInBestState(GENESIS_BLOCK_ROOT)).isFalse();
  }

  @Test
  public void isIncludedInBestState_falseWhenBlockAtSlotDoesNotMatch() {
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, UnsignedLong.ONE);

    final MutableBeaconState bestState =
        DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, seed++).createWritableCopy();
    final BeaconBlock canonicalBlock =
        DataStructureUtil.randomBeaconBlock(GENESIS_BLOCK.getSlot().longValue(), seed++);
    bestState
        .getBlock_roots()
        .set(GENESIS_BLOCK.getSlot().intValue(), canonicalBlock.hash_tree_root());
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);

    assertThat(storageClient.isIncludedInBestState(GENESIS_BLOCK_ROOT)).isFalse();
  }

  @Test
  public void isIncludedInBestState_trueWhenBlockAtSlotDoesMatch() {
    final UnsignedLong bestSlot = UnsignedLong.ONE;
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, bestSlot);

    final MutableBeaconState bestState =
        DataStructureUtil.randomBeaconState(bestSlot, seed++).createWritableCopy();
    bestState
        .getBlock_roots()
        .set(GENESIS_BLOCK.getSlot().intValue(), GENESIS_BLOCK.hash_tree_root());
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(GENESIS_BLOCK_ROOT)).thenReturn(GENESIS_BLOCK);

    assertThat(storageClient.isIncludedInBestState(GENESIS_BLOCK_ROOT)).isTrue();
  }

  @Test
  public void startStoreTransaction_mutateFinalizedCheckpoint() {
    preGenesisStorageClient.initializeFromGenesis(DataStructureUtil.randomBeaconState(1));

    final Checkpoint originalCheckpoint =
        preGenesisStorageClient.getStore().getFinalizedCheckpoint();
    final Checkpoint newCheckpoint = DataStructureUtil.randomCheckpoint(1);
    assertThat(originalCheckpoint).isNotEqualTo(newCheckpoint); // Sanity check

    final Transaction tx = preGenesisStorageClient.startStoreTransaction();
    tx.setFinalizedCheckpoint(newCheckpoint);
    verify(eventBus, never()).post(new FinalizedCheckpointEvent(newCheckpoint));

    tx.commit().reportExceptions();
    verify(eventBus).post(new FinalizedCheckpointEvent(newCheckpoint));

    // Check that store was updated
    final Checkpoint currentCheckpoint =
        preGenesisStorageClient.getStore().getFinalizedCheckpoint();
    assertThat(currentCheckpoint).isEqualTo(newCheckpoint);
  }

  @Test
  public void startStoreTransaction_doNotMutateFinalizedCheckpoint() {
    preGenesisStorageClient.initializeFromGenesis(DataStructureUtil.randomBeaconState(1));
    final Checkpoint originalCheckpoint =
        preGenesisStorageClient.getStore().getFinalizedCheckpoint();

    final Transaction tx = preGenesisStorageClient.startStoreTransaction();
    tx.setTime(UnsignedLong.valueOf(11L));
    tx.commit().reportExceptions();
    verify(eventBus, never()).post(argThat(this::isFinalizedCheckpointEvent));

    final Checkpoint currentCheckpoint =
        preGenesisStorageClient.getStore().getFinalizedCheckpoint();
    assertThat(currentCheckpoint).isEqualTo(originalCheckpoint);
  }

  private void assertStoreInitialized(final ChainStorageClient client) {
    final AtomicBoolean initialized = new AtomicBoolean(false);
    client.subscribeStoreInitialized(() -> initialized.set(true));
    assertThat(initialized).isTrue();
  }

  private void assertStoreNotInitialized(final ChainStorageClient client) {
    final AtomicBoolean initialized = new AtomicBoolean(false);
    client.subscribeStoreInitialized(() -> initialized.set(true));
    assertThat(initialized).isFalse();
  }

  private void assertStoreIsSet(final ChainStorageClient client) {
    assertThat(client.getStore()).isNotNull();

    // With a store set, we shouldn't be allowed to overwrite the store by setting the genesis state
    assertThatThrownBy(() -> client.initializeFromGenesis(INITIAL_STATE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to set genesis state: store has already been initialized");
  }

  private boolean isFinalizedCheckpointEvent(final Object obj) {
    return obj instanceof FinalizedCheckpointEvent;
  }
}
