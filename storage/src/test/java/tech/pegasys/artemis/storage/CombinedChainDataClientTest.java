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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

class CombinedChainDataClientTest {

  private final ChainStorageClient recentChainData = mock(ChainStorageClient.class);
  private final HistoricalChainData historicalChainData = mock(HistoricalChainData.class);
  private final Store store = mock(Store.class);
  private final CombinedChainDataClient client =
      spy(new CombinedChainDataClient(recentChainData, historicalChainData));

  private int seed = 242842;

  @BeforeEach
  public void setUp() {
    when(recentChainData.getStore()).thenReturn(store);
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.ZERO);
  }

  @Test
  public void getBlockAtSlotExact_shouldReturnEmptyWhenRecentDataHasNoStore() {
    when(recentChainData.getStore()).thenReturn(null);
    assertThat(client.getBlockAtSlotExact(UnsignedLong.ONE, Bytes32.ZERO))
        .isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getBlockAtSlotExact_returnEmptyWhenHeadRootUnknown() {
    when(store.getBlockState(Bytes32.ZERO)).thenReturn(null);
    assertThat(client.getBlockAtSlotExact(UnsignedLong.ONE, Bytes32.ZERO))
        .isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getBlockAtSlotExact_returnEmptyWhenHeadRootUnknownAndSlotFinalized() {
    final UnsignedLong slot = UnsignedLong.ONE;
    when(store.getBlockState(Bytes32.ZERO)).thenReturn(null);
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.valueOf(10));

    assertThat(client.getBlockAtSlotExact(slot, Bytes32.ZERO))
        .isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getBlockAtSlotExact_returnBlockFromHistoricalDataWhenHeadRootKnownAndSlotFinalized() {
    final UnsignedLong slot = UnsignedLong.ONE;
    final SignedBeaconBlock block = block(slot);
    when(store.getBlockState(Bytes32.ZERO)).thenReturn(beaconState(UnsignedLong.valueOf(100)));
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.valueOf(10));
    when(historicalChainData.getLatestFinalizedBlockAtSlot(slot))
        .thenReturn(completedFuture(Optional.of(block)));

    assertThat(client.getBlockAtSlotExact(slot, Bytes32.ZERO))
        .isCompletedWithValue(Optional.of(block));
  }

  @Test
  public void getBlockAtSlotExact_returnEmptyWhenFinalizedSlotDidNotHaveABlock() {
    final UnsignedLong slot = UnsignedLong.ONE;
    final SignedBeaconBlock block = block(UnsignedLong.ZERO);
    when(store.getBlockState(Bytes32.ZERO)).thenReturn(beaconState(UnsignedLong.valueOf(100)));
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.valueOf(10));
    when(historicalChainData.getLatestFinalizedBlockAtSlot(slot))
        .thenReturn(completedFuture(Optional.of(block)));

    assertThat(client.getBlockAtSlotExact(slot, Bytes32.ZERO))
        .isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getBlockInEffectAtSlot_returnPrecedingBlockWhenFinalizedSlotDidNotHaveABlock() {
    final UnsignedLong slot = UnsignedLong.ONE;
    final SignedBeaconBlock block = block(UnsignedLong.ZERO);
    when(store.getBlockState(Bytes32.ZERO)).thenReturn(beaconState(UnsignedLong.valueOf(100)));
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.valueOf(10));
    when(historicalChainData.getLatestFinalizedBlockAtSlot(slot))
        .thenReturn(completedFuture(Optional.of(block)));

    assertThat(client.getBlockInEffectAtSlot(slot, Bytes32.ZERO))
        .isCompletedWithValue(Optional.of(block));
  }

  @Test
  public void getBlockAtSlotExact_returnBlockInHeadSlot() {
    final UnsignedLong slot = UnsignedLong.ONE;
    final SignedBeaconBlock block = block(slot);
    final Bytes32 headBlockRoot = Bytes32.ZERO;
    when(store.getBlockState(headBlockRoot)).thenReturn(beaconState(slot));
    when(store.getSignedBlock(headBlockRoot)).thenReturn(block);

    assertThat(client.getBlockAtSlotExact(slot, headBlockRoot))
        .isCompletedWithValue(Optional.of(block));
  }

  @Test
  public void getBlockAtSlotExact_slotAfterHeadRootReturnsEmpty() {
    final UnsignedLong slot = UnsignedLong.ONE;
    final Bytes32 headBlockRoot = Bytes32.ZERO;
    when(store.getBlockState(headBlockRoot)).thenReturn(beaconState(UnsignedLong.ZERO));

    assertThat(client.getBlockAtSlotExact(slot, headBlockRoot))
        .isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getBlockAtSlotExact_returnCorrectBlockFromHistoricalWindow() {
    // We've wrapped around a lot of times and are 5 slots into the next "loop"
    final int historicalIndex = 5;
    final UnsignedLong requestedSlot =
        UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT)
            .times(UnsignedLong.valueOf(900000000))
            .plus(UnsignedLong.valueOf(historicalIndex));
    // Avoid the simple case where the requested slot is the head block slot
    final UnsignedLong headSlot = requestedSlot.plus(UnsignedLong.ONE);

    final SignedBeaconBlock block = block(requestedSlot);
    final Bytes32 blockRoot = block.getMessage().hash_tree_root();
    final Bytes32 headBlockRoot = Bytes32.fromHexString("0x1234");
    final BeaconState bestState = beaconState(headSlot);
    // At the start of the chain, the slot number is the index into historical roots
    bestState.getBlock_roots().set(historicalIndex, blockRoot);
    when(store.getBlockState(headBlockRoot)).thenReturn(bestState);
    when(store.getSignedBlock(blockRoot)).thenReturn(block);

    assertThat(client.getBlockAtSlotExact(requestedSlot, headBlockRoot))
        .isCompletedWithValue(Optional.of(block));
  }

  @Test
  public void getBlockAtSlotExact_returnCorrectBlockFromBeforeBestStateHistoricalWindow() {
    // We've wrapped around a lot of times and are 5 slots into the next "loop"
    final int historicalIndex = 5;
    final UnsignedLong requestedSlot =
        UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT)
            .times(UnsignedLong.valueOf(900000000))
            .plus(UnsignedLong.valueOf(historicalIndex));
    // Avoid the simple case where the requested slot is the head block slot
    final UnsignedLong lastSlotInHistoricalWindow = requestedSlot.plus(UnsignedLong.ONE);
    final UnsignedLong headSlot =
        lastSlotInHistoricalWindow.plus(UnsignedLong.valueOf(Constants.SLOTS_PER_HISTORICAL_ROOT));

    final SignedBeaconBlock block = block(requestedSlot);
    final Bytes32 blockRoot = block.getMessage().hash_tree_root();
    final Bytes32 headBlockRoot = Bytes32.fromHexString("0x1234");
    final BeaconState headState = beaconState(headSlot);
    final Bytes32 olderBlockRoot = Bytes32.fromHexString("0x8976");
    headState.getBlock_roots().set(historicalIndex + 1, olderBlockRoot);
    assertThat(BeaconStateUtil.get_block_root_at_slot(headState, lastSlotInHistoricalWindow))
        .isEqualTo(olderBlockRoot);

    final BeaconState olderState = beaconState(lastSlotInHistoricalWindow);
    olderState.getBlock_roots().set(historicalIndex, blockRoot);

    when(store.getBlockState(headBlockRoot)).thenReturn(headState);
    when(store.getBlockState(olderBlockRoot)).thenReturn(olderState);
    when(store.getSignedBlock(blockRoot)).thenReturn(block);

    assertThat(client.getBlockAtSlotExact(requestedSlot, headBlockRoot))
        .isCompletedWithValue(Optional.of(block));
  }

  @Test
  public void getBlockAtSlotExact_returnPreviousBlockWhenSlotWasEmpty() {
    // We've wrapped around a lot of times and are 5 slots into the next "loop"
    final int historicalIndex = 5;
    final UnsignedLong requestedSlot = UnsignedLong.valueOf(historicalIndex);
    // Avoid the simple case where the requested slot is the head block slot
    final UnsignedLong headSlot = requestedSlot.plus(UnsignedLong.ONE);

    // Block is actually from the block before the requested slot
    final SignedBeaconBlock block = block(requestedSlot.minus(UnsignedLong.ONE));
    final Bytes32 blockRoot = block.getMessage().hash_tree_root();
    final Bytes32 headBlockRoot = Bytes32.fromHexString("0x1234");
    final BeaconState bestState = beaconState(headSlot);
    // At the start of the chain, the slot number is the index into historical roots
    bestState.getBlock_roots().set(historicalIndex, blockRoot);
    when(store.getBlockState(headBlockRoot)).thenReturn(bestState);
    when(store.getSignedBlock(blockRoot)).thenReturn(block);

    assertThat(client.getBlockAtSlotExact(requestedSlot, headBlockRoot))
        .isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getBlockInEffectAtSlot_returnPreviousBlockWhenSlotWasEmpty() {
    // We've wrapped around a lot of times and are 5 slots into the next "loop"
    final int historicalIndex = 5;
    final UnsignedLong requestedSlot = UnsignedLong.valueOf(historicalIndex);
    // Avoid the simple case where the requested slot is the head block slot
    final UnsignedLong headSlot = requestedSlot.plus(UnsignedLong.ONE);

    // Block is actually from the block before the requested slot
    final SignedBeaconBlock block = block(requestedSlot.minus(UnsignedLong.ONE));
    final Bytes32 blockRoot = block.getMessage().hash_tree_root();
    final Bytes32 headBlockRoot = Bytes32.fromHexString("0x1234");
    final BeaconState bestState = beaconState(headSlot);
    // At the start of the chain, the slot number is the index into historical roots
    bestState.getBlock_roots().set(historicalIndex, blockRoot);
    when(store.getBlockState(headBlockRoot)).thenReturn(bestState);
    when(store.getSignedBlock(blockRoot)).thenReturn(block);

    assertThat(client.getBlockInEffectAtSlot(requestedSlot, headBlockRoot))
        .isCompletedWithValue(Optional.of(block));
  }

  @Test
  public void getCommitteesFromStateWithCache_shouldReturnCommitteeAssignments() {
    BeaconStateWithCache stateWithCache =
        BeaconStateWithCache.fromBeaconState(DataStructureUtil.randomBeaconState(11233));
    List<CommitteeAssignment> data =
        client.getCommitteesFromStateWithCache(
            Optional.of(stateWithCache), stateWithCache.getSlot());
    assertThat(data.size()).isEqualTo(SLOTS_PER_EPOCH);
  }

  @Test
  public void getBlockBySlot_blockByBlockRoot() throws ExecutionException, InterruptedException {
    final UnsignedLong slotParam = UnsignedLong.ONE;
    final SignedBeaconBlock signedBeaconBlock =
        DataStructureUtil.randomSignedBeaconBlock(slotParam.longValue(), 7);

    doReturn(Optional.of(signedBeaconBlock.getParent_root()))
        .when(client)
        .getBlockRootBySlot(any());
    doReturn(Optional.empty()).when(client).getBestBlockRoot();
    doReturn(store).when(client).getStore();
    doReturn(signedBeaconBlock).when(store).getSignedBlock(any());

    assertThat(client.getBlockBySlot(slotParam)).isInstanceOf(SafeFuture.class);
    assertThat(client.getBlockBySlot(slotParam).get())
        .isNotNull()
        .isPresent()
        .isEqualTo(Optional.of(signedBeaconBlock));
  }

  @Test
  public void getBlockBySlot_blockByBestBlockRoot()
      throws ExecutionException, InterruptedException {
    final UnsignedLong slotParam = UnsignedLong.ONE;
    final SignedBeaconBlock signedBeaconBlock =
        DataStructureUtil.randomSignedBeaconBlock(slotParam.longValue(), 7);

    doReturn(Optional.empty()).when(client).getBlockRootBySlot(any());
    doReturn(Optional.of(signedBeaconBlock.getParent_root())).when(client).getBestBlockRoot();
    doReturn(SafeFuture.completedFuture(Optional.of(signedBeaconBlock)))
        .when(client)
        .getBlockAtSlotExact(any(), any());

    assertThat(client.getBlockBySlot(slotParam)).isInstanceOf(SafeFuture.class);
    assertThat(client.getBlockBySlot(slotParam).get())
        .isNotNull()
        .isPresent()
        .isEqualTo(Optional.of(signedBeaconBlock));
  }

  @Test
  public void getBlockBySlot_noBlockFound() throws ExecutionException, InterruptedException {
    final UnsignedLong slotParam = UnsignedLong.MAX_VALUE;
    doReturn(Optional.empty()).when(client).getBlockRootBySlot(any());
    doReturn(Optional.empty()).when(client).getBestBlockRoot();

    assertThat(client.getBlockBySlot(slotParam)).isInstanceOf(SafeFuture.class);
    assertThat(client.getBlockBySlot(slotParam).get()).isNotNull().isEmpty();
  }

  private SignedBeaconBlock block(final UnsignedLong slot) {
    return DataStructureUtil.randomSignedBeaconBlock(slot.longValue(), seed++);
  }

  private BeaconState beaconState(final UnsignedLong slot) {
    return DataStructureUtil.randomBeaconState(slot, seed++);
  }
}
