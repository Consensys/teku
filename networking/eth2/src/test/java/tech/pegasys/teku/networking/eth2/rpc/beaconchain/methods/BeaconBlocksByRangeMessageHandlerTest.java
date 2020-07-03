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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;
import static tech.pegasys.teku.util.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.util.config.Constants.MAX_REQUEST_BLOCKS;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.util.async.SafeFuture;

class BeaconBlocksByRangeMessageHandlerTest {

  private static final UnsignedLong MAX_REQUEST_SIZE = UnsignedLong.valueOf(8);
  private static final List<SignedBeaconBlock> BLOCKS =
      IntStream.rangeClosed(0, 10)
          .mapToObj(slot -> new DataStructureUtil(slot).randomSignedBeaconBlock(slot))
          .collect(Collectors.toList());

  private final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<SignedBeaconBlock> listener = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);

  private final BeaconBlocksByRangeMessageHandler handler =
      new BeaconBlocksByRangeMessageHandler(combinedChainDataClient, MAX_REQUEST_SIZE);

  @Test
  public void shouldReturnNoBlocksWhenThereAreNoBlocksAtOrAfterStartSlot() {
    final int startBlock = 10;
    final int count = 1;
    final int skip = 1;
    final SignedBeaconBlock headBlock = BLOCKS.get(1);
    // Series of empty blocks leading up to our best slot.
    withCanonicalHeadBlock(headBlock);
    withAncestorRoots(startBlock, count, skip, hotBlocks());

    when(combinedChainDataClient.getBlockAtSlotExact(any()))
        .thenReturn(completedFuture(Optional.empty()));

    requestBlocks(startBlock, count, skip);

    verifyNoBlocksReturned();
  }

  @Test
  public void shouldReturnRequestedNumberOfBlocksWhenFullySequential() {
    final int startBlock = 3;
    final int count = 5;
    final int skip = 1;
    final SignedBeaconBlock headBlock = BLOCKS.get(10);

    withCanonicalHeadBlock(headBlock);
    withAncestorRoots(startBlock, count, skip, allBlocks());

    requestBlocks(startBlock, count, skip);

    verifyBlocksReturned(3, 4, 5, 6, 7);
  }

  @Test
  public void shouldOnlyReturnBlockAtStartSlotWhenCountIsOne() {
    final int startBlock = 3;
    final int count = 1;
    final int skip = 1;
    final SignedBeaconBlock headBlock = BLOCKS.get(10);

    withCanonicalHeadBlock(headBlock);
    withAncestorRoots(startBlock, count, skip, allBlocks());

    requestBlocks(startBlock, count, skip);

    verifyBlocksReturned(3);
  }

  @Test
  public void shouldReturnRequestedNumberOfBlocksWhenStepIsGreaterThanOne() {
    // Asking for every second block from 2 onwards, up to 5 blocks.
    final int startBlock = 2;
    final int count = 5;
    final int skip = 2;
    final SignedBeaconBlock headBlock = BLOCKS.get(10);
    withCanonicalHeadBlock(headBlock);

    withAncestorRoots(startBlock, count, skip, allBlocks());

    requestBlocks(startBlock, count, skip);

    verifyBlocksReturned(2, 4, 6, 8, 10);
  }

  @Test
  public void shouldReturnFewerBlocksWhenSomeSlotsAreEmpty() {
    // Asking for every block from 2 onwards, up to 5 blocks.
    final int startBlock = 2;
    final int count = 5;
    final int skip = 1;
    final SignedBeaconBlock headBlock = BLOCKS.get(10);

    withCanonicalHeadBlock(headBlock);
    withAncestorRoots(startBlock, count, skip, hotBlocks(2, 3, 5, 6, 7));

    requestBlocks(startBlock, count, skip);

    // Slot 4 is empty so we only return 4 blocks
    verifyBlocksReturned(2, 3, 5, 6);
  }

  @Test
  public void shouldReturnFewerBlocksWhenStepIsGreaterThanOneAndSomeSlotsAreEmpty() {
    final int startBlock = 2;
    final int count = 4;
    final int skip = 2;
    final SignedBeaconBlock headBlock = BLOCKS.get(10);

    withCanonicalHeadBlock(headBlock);
    withAncestorRoots(startBlock, count, skip, hotBlocks(2, 3, 5, 6, 8, 10));

    requestBlocks(startBlock, count, skip);

    // Slot 4 is empty so we only wind up returning 3 blocks, not 4.
    verifyBlocksReturned(2, 6, 8);
  }

  @Test
  public void shouldStopAtBestSlot() {
    final int startBlock = 15;
    final UnsignedLong count = UnsignedLong.valueOf(MAX_REQUEST_BLOCKS);
    final int skip = 5;

    final SignedBeaconBlock headBlock = BLOCKS.get(5);

    withCanonicalHeadBlock(headBlock);
    withAncestorRoots(startBlock, MAX_REQUEST_SIZE.intValue(), skip, hotBlocks());

    handler.onIncomingMessage(
        peer,
        new BeaconBlocksByRangeRequestMessage(
            UnsignedLong.valueOf(startBlock), count, UnsignedLong.valueOf(skip)),
        listener);

    verifyNoBlocksReturned();
    // The first block is after the best block available so we shouldn't request anything
    verify(combinedChainDataClient, never()).getBlockAtSlotExact(any(), any());
  }

  @Test
  public void shouldRejectRequestWhenStepIsZero() {
    final int startBlock = 15;
    final UnsignedLong count = UnsignedLong.valueOf(MAX_REQUEST_BLOCKS);
    final int skip = 0;

    final SignedBeaconBlock headBlock = BLOCKS.get(5);

    withCanonicalHeadBlock(headBlock);

    handler.onIncomingMessage(
        peer,
        new BeaconBlocksByRangeRequestMessage(
            UnsignedLong.valueOf(startBlock), count, UnsignedLong.valueOf(skip)),
        listener);

    verify(listener)
        .completeWithErrorResponse(
            new RpcException(INVALID_REQUEST_CODE, "Step must be greater than zero"));
    verifyNoMoreInteractions(listener);
    verifyNoMoreInteractions(combinedChainDataClient);
  }

  @Test
  void shouldLimitNumberOfBlocksReturned() {
    final int startBlock = 1;
    final UnsignedLong count = MAX_REQUEST_SIZE.plus(ONE);
    final int skip = 1;

    final SignedBeaconBlock headBlock = BLOCKS.get(10);

    withCanonicalHeadBlock(headBlock);
    withAncestorRoots(
        startBlock, MAX_REQUEST_SIZE.intValue(), skip, hotBlocks(1, 2, 3, 6, 7, 8, 9));

    handler.onIncomingMessage(
        peer,
        new BeaconBlocksByRangeRequestMessage(
            UnsignedLong.valueOf(startBlock), count, UnsignedLong.valueOf(skip)),
        listener);

    verifyBlocksReturned(1, 2, 3, 6, 7, 8);
  }

  @Test
  void shouldReturnBlocksFromFinalizedPeriod() {
    final int startBlock = 1;
    final int count = 5;
    final int skip = 1;
    withCanonicalHeadBlock(BLOCKS.get(8));
    withFinalizedBlocks(0, 1, 2, 3, 4, 5, 6, 7);

    requestBlocks(startBlock, count, skip);

    verifyBlocksReturned(1, 2, 3, 4, 5);
    verify(combinedChainDataClient, never()).getAncestorRoots(any(), any(), any());
  }

  @Test
  void shouldReturnMixOfFinalizedAndHotBlocks() {
    final int startBlock = 1;
    final int count = 5;
    final int skip = 1;
    withCanonicalHeadBlock(BLOCKS.get(8));
    withAncestorRoots(startBlock, count, skip, hotBlocks(4, 5, 6));
    withFinalizedBlocks(0, 1, 2, 3);

    requestBlocks(startBlock, count, skip);

    verifyBlocksReturned(1, 2, 3, 4, 5);
  }

  private void requestBlocks(final int startBlock, final int count, final int skip) {
    handler.onIncomingMessage(
        peer,
        new BeaconBlocksByRangeRequestMessage(
            UnsignedLong.valueOf(startBlock),
            UnsignedLong.valueOf(count),
            UnsignedLong.valueOf(skip)),
        listener);
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

  private void withAncestorRoots(
      final int startBlock,
      final int count,
      final int skip,
      final NavigableMap<UnsignedLong, Bytes32> blockRoots) {
    when(combinedChainDataClient.getAncestorRoots(
            UnsignedLong.valueOf(startBlock),
            UnsignedLong.valueOf(skip),
            UnsignedLong.valueOf(count)))
        .thenReturn(blockRoots);
  }

  private NavigableMap<UnsignedLong, Bytes32> allBlocks() {
    return hotBlocks(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
  }

  private NavigableMap<UnsignedLong, Bytes32> hotBlocks(final int... slots) {
    final NavigableMap<UnsignedLong, Bytes32> blockRoots = new TreeMap<>();
    IntStream.of(slots)
        .forEach(
            slot -> {
              final SignedBeaconBlock block = BLOCKS.get(slot);
              blockRoots.put(UnsignedLong.valueOf(slot), block.getRoot());
              when(combinedChainDataClient.getBlockByBlockRoot(block.getRoot()))
                  .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
            });
    return blockRoots;
  }

  private void withFinalizedBlocks(final int... slots) {
    IntStream.of(slots)
        .forEach(
            slot -> {
              final SignedBeaconBlock block = BLOCKS.get(slot);
              final SafeFuture<Optional<SignedBeaconBlock>> result =
                  completedFuture(Optional.of(block));
              when(combinedChainDataClient.getBlockByBlockRoot(block.getRoot())).thenReturn(result);
              when(combinedChainDataClient.getBlockAtSlotExact(block.getSlot())).thenReturn(result);
              when(combinedChainDataClient.isFinalized(block.getSlot())).thenReturn(true);
            });
  }

  private void withCanonicalHeadBlock(final SignedBeaconBlock headBlock) {
    when(combinedChainDataClient.getBestBlock()).thenReturn(Optional.of(headBlock));
  }
}
