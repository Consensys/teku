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

package tech.pegasys.teku.pow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.util.async.SafeFuture.COMPLETE;

import com.google.common.primitives.UnsignedLong;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.schema.ReplayDepositsResult;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.async.StubAsyncRunner;
import tech.pegasys.teku.util.config.Constants;

class Eth1DepositManagerTest {

  private static final BigInteger NEGATIVE_ONE = BigInteger.valueOf(-1);
  private static final SafeFuture<ReplayDepositsResult> NOTHING_REPLAYED =
      SafeFuture.completedFuture(new ReplayDepositsResult(NEGATIVE_ONE, false));
  private static final int MIN_GENESIS_BLOCK_TIMESTAMP = 10_000;
  private final Eth1Provider eth1Provider = mock(Eth1Provider.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final Eth1EventsChannel eth1EventsChannel = mock(Eth1EventsChannel.class);
  private final Eth1DepositStorageChannel eth1DepositStorageChannel =
      mock(Eth1DepositStorageChannel.class);
  private final DepositProcessingController depositProcessingController =
      mock(DepositProcessingController.class);
  private final MinimumGenesisTimeBlockFinder minimumGenesisTimeBlockFinder =
      mock(MinimumGenesisTimeBlockFinder.class);

  private final InOrder inOrder =
      inOrder(
          eth1DepositStorageChannel,
          depositProcessingController,
          eth1EventsChannel,
          minimumGenesisTimeBlockFinder);

  private final Eth1DepositManager manager =
      new Eth1DepositManager(
          eth1Provider,
          asyncRunner,
          eth1EventsChannel,
          eth1DepositStorageChannel,
          depositProcessingController,
          minimumGenesisTimeBlockFinder);

  @BeforeAll
  static void setConstants() {
    Constants.MIN_GENESIS_TIME = UnsignedLong.valueOf(10_000).plus(Constants.GENESIS_DELAY);
  }

  @AfterAll
  static void resetConstants() {
    Constants.setConstants("minimal");
  }

  @Test
  void shouldStartWithNoStoredDepositsAndHeadBeforeMinGenesisTime() {
    final BigInteger headBlockNumber = BigInteger.valueOf(100);
    when(eth1DepositStorageChannel.replayDepositEvents()).thenReturn(NOTHING_REPLAYED);
    withFollowDistanceHead(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP - 1);
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();

    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Process blocks up to the current chain head
    inOrder
        .verify(depositProcessingController)
        .fetchDepositsInRange(BigInteger.ZERO, headBlockNumber);

    // Then start the subscription from the block after head
    inOrder.verify(depositProcessingController).switchToBlockByBlockMode();
    inOrder.verify(depositProcessingController).startSubscription(BigInteger.valueOf(101));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldStartWithStoredDepositsAndHeadBeforeMinGenesisTime() {
    final BigInteger headBlockNumber = BigInteger.valueOf(100);
    final BigInteger lastReplayedBlock = BigInteger.valueOf(10);
    when(eth1DepositStorageChannel.replayDepositEvents())
        .thenReturn(SafeFuture.completedFuture(new ReplayDepositsResult(lastReplayedBlock, false)));
    withFollowDistanceHead(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP - 1);
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();

    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Process blocks up to the current chain head
    inOrder
        .verify(depositProcessingController)
        .fetchDepositsInRange(lastReplayedBlock.add(BigInteger.ONE), headBlockNumber);

    // Then start the subscription from the block after head
    inOrder.verify(depositProcessingController).switchToBlockByBlockMode();
    inOrder.verify(depositProcessingController).startSubscription(BigInteger.valueOf(101));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldStartWithStoredDepositsAndHeadAfterMinGenesisTime() {
    final BigInteger headBlockNumber = BigInteger.valueOf(100);
    final BigInteger minGenesisBlockNumber = BigInteger.valueOf(60);
    final BigInteger lastReplayedBlock = BigInteger.valueOf(10);
    when(eth1DepositStorageChannel.replayDepositEvents())
        .thenReturn(SafeFuture.completedFuture(new ReplayDepositsResult(lastReplayedBlock, false)));
    withFollowDistanceHead(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP + 1000);
    withMinGenesisBlock(headBlockNumber, minGenesisBlockNumber);
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();

    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Find min genesis block
    inOrder.verify(minimumGenesisTimeBlockFinder).findMinGenesisTimeBlockInHistory(headBlockNumber);
    // Process blocks from after the last stored block to min genesis
    inOrder
        .verify(depositProcessingController)
        .fetchDepositsInRange(lastReplayedBlock.add(BigInteger.ONE), minGenesisBlockNumber);

    // Send min genesis event
    inOrder
        .verify(eth1EventsChannel)
        .onMinGenesisTimeBlock(
            new MinGenesisTimeBlockEvent(
                UnsignedLong.valueOf(MIN_GENESIS_BLOCK_TIMESTAMP),
                UnsignedLong.valueOf(minGenesisBlockNumber),
                Bytes32.ZERO));

    // Then start the subscription to process any blocks after min genesis
    // Adding one to ensure we don't process the min genesis block a second time
    inOrder
        .verify(depositProcessingController)
        .startSubscription(minGenesisBlockNumber.add(BigInteger.ONE));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldStartWithNoStoredDepositsAndHeadAfterMinGenesisTime() {
    final BigInteger headBlockNumber = BigInteger.valueOf(100);
    final BigInteger minGenesisBlockNumber = BigInteger.valueOf(60);
    when(eth1DepositStorageChannel.replayDepositEvents()).thenReturn(NOTHING_REPLAYED);
    withFollowDistanceHead(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP + 1000);
    withMinGenesisBlock(headBlockNumber, minGenesisBlockNumber);
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();

    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Find min genesis block
    inOrder.verify(minimumGenesisTimeBlockFinder).findMinGenesisTimeBlockInHistory(headBlockNumber);
    // Process blocks from genesis to min genesis block
    inOrder
        .verify(depositProcessingController)
        .fetchDepositsInRange(BigInteger.ZERO, minGenesisBlockNumber);

    // Send min genesis event
    inOrder
        .verify(eth1EventsChannel)
        .onMinGenesisTimeBlock(
            new MinGenesisTimeBlockEvent(
                UnsignedLong.valueOf(MIN_GENESIS_BLOCK_TIMESTAMP),
                UnsignedLong.valueOf(minGenesisBlockNumber),
                Bytes32.ZERO));

    // Then start the subscription to process any blocks after min genesis
    // Adding one to ensure we don't process the min genesis block a second time
    inOrder
        .verify(depositProcessingController)
        .startSubscription(minGenesisBlockNumber.add(BigInteger.ONE));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldStartWithStoredDepositsAndMinGenesisReachedLongerChain() {
    final BigInteger headBlockNumber = BigInteger.valueOf(100);
    final BigInteger lastReplayedBlock = BigInteger.valueOf(70);
    when(eth1DepositStorageChannel.replayDepositEvents())
        .thenReturn(SafeFuture.completedFuture(new ReplayDepositsResult(lastReplayedBlock, true)));
    withFollowDistanceHead(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP + 1000);
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();

    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Just start processing blocks from after the last replayed block.
    inOrder
        .verify(depositProcessingController)
        .startSubscription(lastReplayedBlock.add(BigInteger.ONE));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldStartWithStoredDepositsAndMinGenesisReachedChainReorgedToBeShorter() {
    // Head block number has wound up being before the last block we already processed
    final BigInteger headBlockNumber = BigInteger.valueOf(60);
    final BigInteger lastReplayedBlock = BigInteger.valueOf(70);
    when(eth1DepositStorageChannel.replayDepositEvents())
        .thenReturn(SafeFuture.completedFuture(new ReplayDepositsResult(lastReplayedBlock, true)));
    withFollowDistanceHead(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP + 1000);
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();

    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Just start processing blocks from after the last replayed block.
    inOrder
        .verify(depositProcessingController)
        .startSubscription(lastReplayedBlock.add(BigInteger.ONE));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldStartWithStoredDepositsChainReorgedToBeShorter() {
    // Head block number has wound up being before the last block we already processed
    final BigInteger headBlockNumber = BigInteger.valueOf(60);
    final BigInteger lastReplayedBlock = BigInteger.valueOf(70);
    when(eth1DepositStorageChannel.replayDepositEvents())
        .thenReturn(SafeFuture.completedFuture(new ReplayDepositsResult(lastReplayedBlock, false)));
    withFollowDistanceHead(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP + 1000);
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();

    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Min genesis not reached so process block by block after the last replayed block
    inOrder.verify(depositProcessingController).switchToBlockByBlockMode();
    inOrder
        .verify(depositProcessingController)
        .startSubscription(lastReplayedBlock.add(BigInteger.ONE));
    inOrder.verifyNoMoreInteractions();
  }

  private void withMinGenesisBlock(
      final BigInteger headBlockNumber, final BigInteger minGenesisBlockNumber) {
    final Block minGenesisBlock = block(minGenesisBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP);
    when(minimumGenesisTimeBlockFinder.findMinGenesisTimeBlockInHistory(headBlockNumber))
        .thenReturn(SafeFuture.completedFuture(minGenesisBlock));
  }

  private void withFollowDistanceHead(final BigInteger number, final long timestamp) {
    final Block latestBlock =
        block(number.add(Constants.ETH1_FOLLOW_DISTANCE.bigIntegerValue()), timestamp + 100000);
    final Block followDistanceHead = block(number, timestamp);
    when(eth1Provider.getLatestEth1Block()).thenReturn(SafeFuture.completedFuture(latestBlock));
    when(eth1Provider.getGuaranteedEth1Block(UnsignedLong.valueOf(number)))
        .thenReturn(SafeFuture.completedFuture(followDistanceHead));
  }

  private Block block(final BigInteger number, final long timestamp) {
    final Block block = mock(Block.class);
    when(block.getNumber()).thenReturn(number);
    when(block.getTimestamp()).thenReturn(BigInteger.valueOf(timestamp));
    when(block.getHash()).thenReturn(Bytes32.ZERO.toHexString());
    return block;
  }
}
