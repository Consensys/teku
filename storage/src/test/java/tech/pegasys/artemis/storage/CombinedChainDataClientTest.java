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
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.config.Constants;

class CombinedChainDataClientTest {

  private final ChainStorageClient recentChainData = mock(ChainStorageClient.class);
  private final HistoricalChainData historicalChainData = mock(HistoricalChainData.class);
  private final Store store = mock(Store.class);
  private final CombinedChainDataClient client =
      new CombinedChainDataClient(recentChainData, historicalChainData);

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
    when(historicalChainData.getFinalizedBlockAtSlot(slot))
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
    when(historicalChainData.getFinalizedBlockAtSlot(slot))
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
    when(historicalChainData.getFinalizedBlockAtSlot(slot))
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
    final MutableBeaconState bestState = beaconState(headSlot).createWritableCopy();
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
    final MutableBeaconState headState = beaconState(headSlot).createWritableCopy();
    final Bytes32 olderBlockRoot = Bytes32.fromHexString("0x8976");
    headState.getBlock_roots().set(historicalIndex + 1, olderBlockRoot);
    assertThat(BeaconStateUtil.get_block_root_at_slot(headState, lastSlotInHistoricalWindow))
        .isEqualTo(olderBlockRoot);

    final MutableBeaconState olderState = beaconState(lastSlotInHistoricalWindow).createWritableCopy();
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
    final MutableBeaconState bestState = beaconState(headSlot).createWritableCopy();
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
    final MutableBeaconState bestState = beaconState(headSlot).createWritableCopy();
    // At the start of the chain, the slot number is the index into historical roots
    bestState.getBlock_roots().set(historicalIndex, blockRoot);
    when(store.getBlockState(headBlockRoot)).thenReturn(bestState);
    when(store.getSignedBlock(blockRoot)).thenReturn(block);

    assertThat(client.getBlockInEffectAtSlot(requestedSlot, headBlockRoot))
        .isCompletedWithValue(Optional.of(block));
  }

  private SignedBeaconBlock block(final UnsignedLong slot) {
    return DataStructureUtil.randomSignedBeaconBlock(slot.longValue(), seed++);
  }

  private BeaconState beaconState(final UnsignedLong slot) {
    return DataStructureUtil.randomBeaconState(slot, seed++);
  }
}
