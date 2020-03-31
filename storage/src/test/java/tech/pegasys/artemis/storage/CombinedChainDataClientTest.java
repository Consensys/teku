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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.api.StorageQueryChannel;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

class CombinedChainDataClientTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
  private final Store store = mock(Store.class);
  private final CombinedChainDataClient client =
      new CombinedChainDataClient(recentChainData, historicalChainData);

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

    final MutableBeaconState olderState =
        beaconState(lastSlotInHistoricalWindow).createWritableCopy();
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

  @Test
  public void getBlockAndStateInEffectAtSlot_returnEmptyWhenBlockIsUnavailable() {
    final UnsignedLong requestedSlot = UnsignedLong.valueOf(100);
    final Bytes32 headBlockRoot = Bytes32.fromHexString("0x1234");

    final SafeFuture<Optional<BeaconBlockAndState>> result =
        client.getBlockAndStateInEffectAtSlot(requestedSlot, headBlockRoot);

    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getBlockAndStateInEffectAtSlot_failWhenBlockRequestFails() {
    final UnsignedLong requestedSlot = UnsignedLong.valueOf(100);
    final Bytes32 headBlockRoot = Bytes32.fromHexString("0x1234");
    final Exception error = new RuntimeException("Nope");

    // Work with finalized data, it's easier to test the logic we're interested in.
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.valueOf(500));
    when(store.getBlockState(headBlockRoot))
        .thenReturn(beaconState(requestedSlot.plus(UnsignedLong.ONE)));
    when(historicalChainData.getLatestFinalizedBlockAtSlot(requestedSlot))
        .thenReturn(SafeFuture.failedFuture(error));
    final SafeFuture<Optional<BeaconBlockAndState>> result =
        client.getBlockAndStateInEffectAtSlot(requestedSlot, headBlockRoot);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCause(error);
  }

  @Test
  public void getBlockAndStateInEffectAtSlot_returnEmptyWhenBlockIsAvailableButStateIsNot() {
    final UnsignedLong requestedSlot = UnsignedLong.valueOf(100);
    final Bytes32 headBlockRoot = Bytes32.fromHexString("0x1234");

    final SignedBeaconBlock block = block(requestedSlot);

    // Work with finalized data, it's easier to test the logic we're interested in.
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.valueOf(500));
    when(store.getBlockState(headBlockRoot))
        .thenReturn(beaconState(requestedSlot.plus(UnsignedLong.ONE)));
    when(historicalChainData.getLatestFinalizedBlockAtSlot(requestedSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(historicalChainData.getFinalizedStateByBlockRoot(block.getMessage().hash_tree_root()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<Optional<BeaconBlockAndState>> result =
        client.getBlockAndStateInEffectAtSlot(requestedSlot, headBlockRoot);

    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getBlockAndStateInEffectAtSlot_failWhenStateRequestFails() {
    final UnsignedLong requestedSlot = UnsignedLong.valueOf(100);
    final Bytes32 headBlockRoot = Bytes32.fromHexString("0x1234");

    final SignedBeaconBlock block = block(requestedSlot);
    final Exception error = new RuntimeException("Nope");

    // Work with finalized data, it's easier to test the logic we're interested in.
    when(recentChainData.getFinalizedEpoch()).thenReturn(UnsignedLong.valueOf(500));
    when(store.getBlockState(headBlockRoot))
        .thenReturn(beaconState(requestedSlot.plus(UnsignedLong.ONE)));
    when(historicalChainData.getLatestFinalizedBlockAtSlot(requestedSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(historicalChainData.getFinalizedStateByBlockRoot(block.getMessage().hash_tree_root()))
        .thenReturn(SafeFuture.failedFuture(error));

    final SafeFuture<Optional<BeaconBlockAndState>> result =
        client.getBlockAndStateInEffectAtSlot(requestedSlot, headBlockRoot);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCause(error);
  }

  @Test
  public void getBlockAndStateInEffectAtSlot_returnBlockAndStateWhenBothAreAvailable() {
    final UnsignedLong requestedSlot = UnsignedLong.valueOf(100);
    final SignedBeaconBlock block = block(requestedSlot);
    final BeaconState state = beaconState(requestedSlot);
    final Bytes32 headBlockRoot = block.getMessage().hash_tree_root();

    when(store.getBlockState(headBlockRoot)).thenReturn(state);
    when(store.getSignedBlock(headBlockRoot)).thenReturn(block);

    final SafeFuture<Optional<BeaconBlockAndState>> result =
        client.getBlockAndStateInEffectAtSlot(requestedSlot, headBlockRoot);

    assertThat(result)
        .isCompletedWithValue(Optional.of(new BeaconBlockAndState(block.getMessage(), state)));
  }

  @Test
  public void getCommitteesFromStateWithCache_shouldReturnCommitteeAssignments() {
    BeaconState state = dataStructureUtil.randomBeaconState();
    List<CommitteeAssignment> data = client.getCommitteesFromState(state, state.getSlot());
    assertThat(data.size()).isEqualTo(SLOTS_PER_EPOCH);
  }

  @Test
  public void getBlockBySlot_blockByBlockRoot() throws ExecutionException, InterruptedException {
    final UnsignedLong slotParam = UnsignedLong.ONE;
    final SignedBeaconBlock signedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlock(slotParam.longValue());

    when(recentChainData.getBlockRootBySlot(any()))
        .thenReturn(Optional.of(signedBeaconBlock.getParent_root()));
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.empty());
    when(store.getSignedBlock(any())).thenReturn(signedBeaconBlock);

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
        dataStructureUtil.randomSignedBeaconBlock(slotParam.longValue());

    when(recentChainData.getBlockRootBySlot(any())).thenReturn(Optional.empty());
    when(recentChainData.getBestBlockRoot())
        .thenReturn(Optional.of(signedBeaconBlock.getParent_root()));
    when(store.getBlockState(any())).thenReturn(beaconState(signedBeaconBlock.getSlot()));
    when(store.getSignedBlock(any())).thenReturn(signedBeaconBlock);

    assertThat(client.getBlockBySlot(slotParam)).isInstanceOf(SafeFuture.class);
    assertThat(client.getBlockBySlot(slotParam).get())
        .isNotNull()
        .isPresent()
        .isEqualTo(Optional.of(signedBeaconBlock));
  }

  @Test
  public void getBlockBySlot_noBlockFound() throws ExecutionException, InterruptedException {
    final UnsignedLong slotParam = UnsignedLong.MAX_VALUE;
    when(recentChainData.getBlockRootBySlot(any())).thenReturn(Optional.empty());
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.empty());

    assertThat(client.getBlockBySlot(slotParam)).isInstanceOf(SafeFuture.class);
    assertThat(client.getBlockBySlot(slotParam).get()).isNotNull().isEmpty();
  }

  @Test
  public void getStateAtSlot_shouldUseRecentStoreForGenesisSlotDuringFirstEpoch() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UnsignedLong.ZERO);
    final Bytes32 headBlockRoot = state.hash_tree_root();
    when(store.getBlockState(headBlockRoot)).thenReturn(state);

    assertThat(client.getStateAtSlot(state.getSlot(), headBlockRoot))
        .isCompletedWithValue(Optional.of(state));
  }

  private SignedBeaconBlock block(final UnsignedLong slot) {
    return dataStructureUtil.randomSignedBeaconBlock(slot.longValue());
  }

  private BeaconState beaconState(final UnsignedLong slot) {
    return dataStructureUtil.randomBeaconState(slot);
  }
}
