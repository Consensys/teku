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

package tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.artemis.networking.eth2.rpc.core.RpcException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;

class BeaconBlocksByRangeMessageHandlerTest {
  private final Eth2Peer peer = mock(Eth2Peer.class);

  private static final List<BeaconBlock> BLOCKS =
      IntStream.rangeClosed(0, 10)
          .mapToObj(slot -> DataStructureUtil.randomBeaconBlock(slot, slot))
          .collect(Collectors.toList());

  @SuppressWarnings("unchecked")
  private final ResponseCallback<BeaconBlock> listener = mock(ResponseCallback.class);

  private final ChainStorageClient storageClient = mock(ChainStorageClient.class);
  private final Store store = mock(Store.class);

  private final BeaconBlocksByRangeMessageHandler handler =
      new BeaconBlocksByRangeMessageHandler(storageClient);

  @Test
  public void shouldReturnNoBlocksWhenStoreIsNotPresent() {
    when(storageClient.getStore()).thenReturn(null);

    handler.onIncomingMessage(
        peer,
        new BeaconBlocksByRangeRequestMessage(Bytes32.ZERO, UnsignedLong.ZERO, ONE, ONE),
        listener);

    verifyNoBlocksReturned();
  }

  @Test
  public void shouldReturnNoBlocksWhenHeadBlockIsNotInStore() {
    final Bytes32 headBlockRoot = Bytes32.fromHexStringLenient("0x123456");
    when(storageClient.getStore()).thenReturn(store);
    when(store.getBlock(headBlockRoot)).thenReturn(null);

    handler.onIncomingMessage(
        peer,
        new BeaconBlocksByRangeRequestMessage(headBlockRoot, UnsignedLong.ZERO, ONE, ONE),
        listener);

    verify(storageClient, never()).isIncludedInBestState(null);
    verifyNoBlocksReturned();
  }

  @Test
  public void shouldReturnNoBlocksWhenHeadIsNotCanonical() {
    final BeaconBlock headBlock = BLOCKS.get(1);
    final Bytes32 headBlockRoot = headBlock.hash_tree_root();
    when(storageClient.getStore()).thenReturn(store);
    when(store.getBlock(headBlockRoot)).thenReturn(headBlock);
    when(storageClient.isIncludedInBestState(headBlockRoot)).thenReturn(false);

    handler.onIncomingMessage(
        peer,
        new BeaconBlocksByRangeRequestMessage(headBlockRoot, UnsignedLong.ZERO, ONE, ONE),
        listener);

    verifyNoBlocksReturned();
    verify(storageClient, never()).getBlockBySlot(any());
  }

  @Test
  public void shouldReturnNoBlocksWhenThereAreNoBlocksAtOrAfterStartSlot() {
    final int startBlock = 10;
    final int count = 1;
    final int skip = 1;
    final BeaconBlock headBlock = BLOCKS.get(1);
    // Series of empty blocks leading up to our best slot.
    withCanonicalHeadBlock(headBlock, UnsignedLong.valueOf(20));

    when(storageClient.getBlockBySlot(any())).thenReturn(Optional.empty());

    handler.onIncomingMessage(
        peer,
        new BeaconBlocksByRangeRequestMessage(
            headBlock.hash_tree_root(),
            UnsignedLong.valueOf(startBlock),
            UnsignedLong.valueOf(count),
            UnsignedLong.valueOf(skip)),
        listener);

    verifyNoBlocksReturned();
  }

  @Test
  public void shouldReturnRequestedNumberOfBlocksWhenFullySequential() {
    final int startBlock = 3;
    final int count = 5;
    final int skip = 1;
    final BeaconBlock headBlock = BLOCKS.get(10);

    withCanonicalHeadBlock(headBlock);
    when(storageClient.getBestSlot()).thenReturn(headBlock.getSlot());

    BLOCKS.forEach(
        block ->
            when(storageClient.getBlockBySlot(block.getSlot())).thenReturn(Optional.of(block)));

    handler.onIncomingMessage(
        peer,
        new BeaconBlocksByRangeRequestMessage(
            headBlock.hash_tree_root(),
            UnsignedLong.valueOf(startBlock),
            UnsignedLong.valueOf(count),
            UnsignedLong.valueOf(skip)),
        listener);

    verifyBlocksReturned(3, 4, 5, 6, 7);
  }

  @Test
  public void shouldReturnRequestedNumberOfBlocksWhenStepIsGreaterThanOne() {
    // Asking for every second block from 2 onwards, up to 5 blocks.
    final int startBlock = 2;
    final int count = 5;
    final int skip = 2;
    final BeaconBlock headBlock = BLOCKS.get(10);
    withCanonicalHeadBlock(headBlock);

    BLOCKS.forEach(
        block ->
            when(storageClient.getBlockBySlot(block.getSlot())).thenReturn(Optional.of(block)));

    handler.onIncomingMessage(
        peer,
        new BeaconBlocksByRangeRequestMessage(
            headBlock.hash_tree_root(),
            UnsignedLong.valueOf(startBlock),
            UnsignedLong.valueOf(count),
            UnsignedLong.valueOf(skip)),
        listener);

    verifyBlocksReturned(2, 4, 6, 8, 10);
  }

  @Test
  public void shouldReturnRequestedNumberOfBlocksWhenSomeSlotsAreEmpty() {
    // Asking for every block from 2 onwards, up to 5 blocks.
    final int startBlock = 2;
    final int count = 5;
    final int skip = 1;
    final BeaconBlock headBlock = BLOCKS.get(10);

    withCanonicalHeadBlock(headBlock);

    withBlockAtSlot(2);
    withBlockAtSlot(3);
    withEmptySlot(4);
    withBlockAtSlot(5);
    withBlockAtSlot(6);
    withBlockAtSlot(7);

    handler.onIncomingMessage(
        peer,
        new BeaconBlocksByRangeRequestMessage(
            headBlock.hash_tree_root(),
            UnsignedLong.valueOf(startBlock),
            UnsignedLong.valueOf(count),
            UnsignedLong.valueOf(skip)),
        listener);

    // Slot 4 is empty but we still respond with 5 blocks.
    verifyBlocksReturned(2, 3, 5, 6, 7);
  }

  @Test
  public void shouldReturnRequestedNumberOfBlocksWhenStepIsGreaterThanOneAndSomeSlotsAreEmpty() {
    final int startBlock = 2;
    final int count = 4;
    final int skip = 2;
    final BeaconBlock headBlock = BLOCKS.get(10);

    withCanonicalHeadBlock(headBlock);

    withBlockAtSlot(2);
    withBlockAtSlot(3);
    withEmptySlot(4);
    withBlockAtSlot(5);
    withBlockAtSlot(6);
    withEmptySlot(7);
    withBlockAtSlot(8);
    withBlockAtSlot(10);

    handler.onIncomingMessage(
        peer,
        new BeaconBlocksByRangeRequestMessage(
            headBlock.hash_tree_root(),
            UnsignedLong.valueOf(startBlock),
            UnsignedLong.valueOf(count),
            UnsignedLong.valueOf(skip)),
        listener);

    // Respond with the requested number of blocks despite the empty slot
    verifyBlocksReturned(2, 6, 8, 10);
  }

  @Test
  public void shouldStopAtBestSlot() {
    final int startBlock = 15;
    final UnsignedLong count = UnsignedLong.MAX_VALUE;
    final int skip = 5;

    final BeaconBlock headBlock = BLOCKS.get(5);

    final UnsignedLong bestSlot = UnsignedLong.valueOf(20);
    withCanonicalHeadBlock(headBlock, bestSlot);

    handler.onIncomingMessage(
        peer,
        new BeaconBlocksByRangeRequestMessage(
            headBlock.hash_tree_root(),
            UnsignedLong.valueOf(startBlock),
            count,
            UnsignedLong.valueOf(skip)),
        listener);

    verifyNoBlocksReturned();
    verify(storageClient).getBlockBySlot(UnsignedLong.valueOf(15));
    verify(storageClient).getBlockBySlot(UnsignedLong.valueOf(20));
    verify(storageClient, never()).getBlockBySlot(greaterThan(bestSlot));
  }

  @Test
  public void shouldRejectRequestWhenStepIsZero() {
    final int startBlock = 15;
    final UnsignedLong count = UnsignedLong.MAX_VALUE;
    final int skip = 0;

    final BeaconBlock headBlock = BLOCKS.get(5);

    final UnsignedLong bestSlot = UnsignedLong.valueOf(20);
    withCanonicalHeadBlock(headBlock, bestSlot);

    handler.onIncomingMessage(
        peer,
        new BeaconBlocksByRangeRequestMessage(
            headBlock.hash_tree_root(),
            UnsignedLong.valueOf(startBlock),
            count,
            UnsignedLong.valueOf(skip)),
        listener);

    verify(listener).completeWithError(RpcException.INVALID_STEP);
    verifyNoMoreInteractions(listener);
    verifyNoMoreInteractions(storageClient);
  }

  private void withCanonicalHeadBlock(final BeaconBlock headBlock) {
    withCanonicalHeadBlock(headBlock, headBlock.getSlot());
  }

  private void verifyNoBlocksReturned() {
    verifyBlocksReturned();
  }

  private void verifyBlocksReturned(final int... slots) {
    final InOrder inOrder = Mockito.inOrder(listener);
    for (int slot : slots) {
      inOrder.verify(listener).respond(BLOCKS.get(slot));
    }
    inOrder.verify(listener).completeSuccessfully();
    verifyNoMoreInteractions(listener);
  }

  private void withCanonicalHeadBlock(final BeaconBlock headBlock, final UnsignedLong bestSlot) {
    when(storageClient.getStore()).thenReturn(store);
    when(storageClient.isIncludedInBestState(headBlock.hash_tree_root())).thenReturn(true);
    when(storageClient.getBestSlot()).thenReturn(bestSlot);
  }

  private void withBlockAtSlot(final int slot) {
    when(storageClient.getBlockBySlot(UnsignedLong.valueOf(slot)))
        .thenReturn(Optional.of(BLOCKS.get(slot)));
  }

  private void withEmptySlot(final int slot) {
    when(storageClient.getBlockBySlot(UnsignedLong.valueOf(slot))).thenReturn(Optional.empty());
  }

  private UnsignedLong greaterThan(final UnsignedLong bestSlot) {
    return argThat(argument -> argument.compareTo(bestSlot) > 0);
  }
}
