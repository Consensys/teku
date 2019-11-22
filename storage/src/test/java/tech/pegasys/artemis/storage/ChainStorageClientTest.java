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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.config.Constants;

class ChainStorageClientTest {

  private static final Bytes32 BEST_BLOCK_ROOT = Bytes32.fromHexString("0x8462485245");
  private static final BeaconBlock BLOCK1 = DataStructureUtil.randomBeaconBlock(0, 1);
  private static final BeaconBlock BLOCK2 = DataStructureUtil.randomBeaconBlock(0, 2);
  private static final Bytes32 BLOCK1_ROOT = BLOCK1.hash_tree_root();
  private final EventBus eventBus = mock(EventBus.class);
  private final Store store = mock(Store.class);
  private final ChainStorageClient storageClient = new ChainStorageClient(eventBus);
  private int seed = 428942;

  @BeforeAll
  public static void beforeAll() {
    Constants.setConstants("minimal");
  }

  @Test
  public void getBlockBySlot_returnEmptyWhenStoreNotSet() {
    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getBlockBySlot_returnEmptyWhenBestBlockNotSet() {
    storageClient.setStore(store);
    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getBlockBySlot_returnGenesisBlockWhenItIsTheBestState() {
    storageClient.setStore(store);
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, UnsignedLong.ZERO);

    final BeaconState bestState = DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, seed++);
    // At the start of the chain, the slot number is the index into historical roots
    bestState.getBlock_roots().set(0, BEST_BLOCK_ROOT);
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(BEST_BLOCK_ROOT)).thenReturn(BLOCK1);

    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).contains(BLOCK1);
  }

  @Test
  public void getBlockBySlot_returnGenesisBlockWhenItIsNotTheBestState() {
    storageClient.setStore(store);
    final UnsignedLong bestSlot = UnsignedLong.ONE;
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, bestSlot);

    final BeaconState bestState = DataStructureUtil.randomBeaconState(bestSlot, seed++);
    final BeaconBlock genesisBlock = BLOCK1;
    final Bytes32 genesisBlockHash = genesisBlock.hash_tree_root();
    // At the start of the chain, the slot number is the index into historical roots
    bestState.getBlock_roots().set(0, genesisBlockHash);
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(genesisBlockHash)).thenReturn(genesisBlock);

    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).contains(genesisBlock);
  }

  @Test
  public void getBlockBySlot_returnEmptyWhenSlotBeforeHistoricalRootWindow() {
    storageClient.setStore(store);
    // First slot where the block roots start overwriting themselves and dropping history
    final UnsignedLong bestSlot =
        UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT).plus(UnsignedLong.ONE);
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, bestSlot);

    final BeaconState bestState = DataStructureUtil.randomBeaconState(bestSlot, seed++);
    final BeaconBlock bestBlock = BLOCK1;
    final Bytes32 bestBlockHash = bestBlock.hash_tree_root();
    // Overwrite the genesis hash.
    bestState.getBlock_roots().set(0, bestBlockHash);
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(bestBlockHash)).thenReturn(bestBlock);

    // Slot 0 has now been overwritten by our current best slot so we can't get that block anymore.
    assertThat(storageClient.getBlockBySlot(UnsignedLong.ZERO)).isEmpty();
  }

  @Test
  public void getBlockBySlot_returnCorrectBlockFromHistoricalWindow() {
    storageClient.setStore(store);
    // We've wrapped around a lot of times and are 5 slots into the next "loop"
    final int historicalIndex = 5;
    final UnsignedLong reqeustedSlot =
        UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT)
            .times(UnsignedLong.valueOf(900000000))
            .plus(UnsignedLong.valueOf(historicalIndex));
    // Avoid the simple case where the requested slot is the best slot so we have to go to the
    // historic blocks
    final UnsignedLong bestSlot = reqeustedSlot.plus(UnsignedLong.ONE);

    storageClient.updateBestBlock(BEST_BLOCK_ROOT, bestSlot);

    final BeaconState bestState = DataStructureUtil.randomBeaconState(bestSlot, seed++);
    final BeaconBlock bestBlock = BLOCK1;
    final Bytes32 bestBlockHash = bestBlock.hash_tree_root();
    // Overwrite the genesis hash.
    bestState.getBlock_roots().set(historicalIndex, bestBlockHash);
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(bestBlockHash)).thenReturn(bestBlock);

    assertThat(storageClient.getBlockBySlot(reqeustedSlot)).contains(bestBlock);
  }

  @Test
  public void isIncludedInBestState_falseWhenNoStoreSet() {
    assertThat(storageClient.isIncludedInBestState(BLOCK1_ROOT)).isFalse();
  }

  @Test
  public void isIncludedInBestState_falseWhenBestBlockNotSet() {
    storageClient.setStore(store);
    assertThat(storageClient.isIncludedInBestState(BLOCK1_ROOT)).isFalse();
  }

  @Test
  public void isIncludedInBestState_falseWhenNoBlockAtSlot() {
    storageClient.setStore(store);
    final UnsignedLong bestSlot = UnsignedLong.ONE;
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, bestSlot);

    // bestState has no historical block roots so definitely nothing canonical at the block's slot
    final BeaconState bestState = DataStructureUtil.randomBeaconState(bestSlot, seed++);
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(BLOCK1_ROOT)).thenReturn(BLOCK1);

    assertThat(storageClient.isIncludedInBestState(BLOCK1_ROOT)).isFalse();
  }

  @Test
  public void isIncludedInBestState_falseWhenBlockAtSlotDoesNotMatch() {
    storageClient.setStore(store);
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, UnsignedLong.ONE);

    final BeaconState bestState = DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, seed++);
    bestState.getBlock_roots().set(BLOCK1.getSlot().intValue(), BLOCK2.hash_tree_root());
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);

    assertThat(storageClient.isIncludedInBestState(BLOCK1_ROOT)).isFalse();
  }

  @Test
  public void isIncludedInBestState_trueWhenBlockAtSlotDoesMatch() {
    storageClient.setStore(store);
    final UnsignedLong bestSlot = UnsignedLong.ONE;
    storageClient.updateBestBlock(BEST_BLOCK_ROOT, bestSlot);

    final BeaconState bestState = DataStructureUtil.randomBeaconState(bestSlot, seed++);
    bestState.getBlock_roots().set(BLOCK1.getSlot().intValue(), BLOCK1.hash_tree_root());
    when(store.getBlockState(BEST_BLOCK_ROOT)).thenReturn(bestState);
    when(store.getBlock(BLOCK1_ROOT)).thenReturn(BLOCK1);

    assertThat(storageClient.isIncludedInBestState(BLOCK1_ROOT)).isTrue();
  }
}
