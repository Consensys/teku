/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beacon.pow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.ethereum.pow.merkletree.DepositTree.DEPOSIT_TREE_SNAPSHOT_SCHEMA;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.COMPLETE;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.Eth1SnapshotLoaderChannel;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.schema.LoadDepositSnapshotResult;
import tech.pegasys.teku.ethereum.pow.api.schema.ReplayDepositsResult;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.TrackingUncaughtExceptionHandler;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.subscribers.ValueObserver;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;

class Eth1DepositManagerTest {

  private static final SafeFuture<ReplayDepositsResult> NOTHING_REPLAYED =
      SafeFuture.completedFuture(ReplayDepositsResult.empty());
  private static final int MIN_GENESIS_BLOCK_TIMESTAMP = 10_000;
  private final SpecConfig config =
      SpecConfigLoader.loadConfig(
          "minimal",
          builder ->
              builder.minGenesisTime(UInt64.valueOf(10_300)).genesisDelay(UInt64.valueOf(300)));
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final Eth1Provider eth1Provider = mock(Eth1Provider.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final ValidatingEth1EventsPublisher eth1EventsChannel =
      mock(ValidatingEth1EventsPublisher.class);
  private final Eth1SnapshotLoaderChannel eth1SnapshotLoaderChannel =
      mock(Eth1SnapshotLoaderChannel.class);
  private final Eth1DepositStorageChannel eth1DepositStorageChannel =
      mock(Eth1DepositStorageChannel.class);
  private final DepositProcessingController depositProcessingController =
      mock(DepositProcessingController.class);
  private final MinimumGenesisTimeBlockFinder minimumGenesisTimeBlockFinder =
      mock(MinimumGenesisTimeBlockFinder.class);
  private final Eth1HeadTracker eth1HeadTracker = mock(Eth1HeadTracker.class);
  private final TrackingUncaughtExceptionHandler exceptionHandler =
      new TrackingUncaughtExceptionHandler();

  private final InOrder inOrder =
      inOrder(
          eth1DepositStorageChannel,
          depositProcessingController,
          eth1EventsChannel,
          minimumGenesisTimeBlockFinder);

  private final Eth1DepositManager manager =
      new Eth1DepositManager(
          config,
          eth1Provider,
          asyncRunner,
          eth1EventsChannel,
          eth1DepositStorageChannel,
          eth1SnapshotLoaderChannel,
          depositProcessingController,
          minimumGenesisTimeBlockFinder,
          Optional.empty(),
          eth1HeadTracker);

  @BeforeEach
  public void setup() {
    when(eth1SnapshotLoaderChannel.loadDepositSnapshot())
        .thenReturn(SafeFuture.completedFuture(LoadDepositSnapshotResult.EMPTY));
    Thread.setDefaultUncaughtExceptionHandler(exceptionHandler);
  }

  @Test
  void shouldStartWithNoStoredDepositsAndHeadBeforeMinGenesisTime() {
    final BigInteger headBlockNumber = BigInteger.valueOf(100);
    when(eth1DepositStorageChannel.replayDepositEvents()).thenReturn(NOTHING_REPLAYED);
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();

    notifyHeadBlock(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP - 1);

    verify(eth1EventsChannel, never()).setLatestPublishedDeposit(any());
    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Process blocks up to the current chain head
    inOrder
        .verify(depositProcessingController)
        .fetchDepositsInRange(BigInteger.ZERO, headBlockNumber);

    // Then start the subscription from the block after head
    inOrder.verify(depositProcessingController).switchToBlockByBlockMode();
    inOrder.verify(depositProcessingController).startSubscription(BigInteger.valueOf(101));
    inOrder.verifyNoMoreInteractions();
    assertNoUncaughtExceptions();
  }

  @Test
  void shouldWaitUntilEth1ProviderSucceeds() {
    final BigInteger headBlockNumber = BigInteger.valueOf(100);
    when(eth1DepositStorageChannel.replayDepositEvents()).thenReturn(NOTHING_REPLAYED);
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();

    // Notify head block but retrieving it will not succeed for a while
    final SafeFuture<Block> blockFuture = notifyHeadBlockAndPauseToRetrieve(headBlockNumber);
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(0);

    // Succeed request
    final Block latestBlock = block(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP - 1);
    blockFuture.complete(latestBlock);

    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Process blocks up to the current chain head
    inOrder
        .verify(depositProcessingController)
        .fetchDepositsInRange(BigInteger.ZERO, headBlockNumber);

    // Then start the subscription from the block after head
    inOrder.verify(depositProcessingController).switchToBlockByBlockMode();
    inOrder.verify(depositProcessingController).startSubscription(BigInteger.valueOf(101));
    inOrder.verifyNoMoreInteractions();
    assertNoUncaughtExceptions();
  }

  @SuppressWarnings("unchecked")
  private SafeFuture<EthBlock.Block> notifyHeadBlockAndPauseToRetrieve(
      final BigInteger headBlockNumber) {
    final SafeFuture<EthBlock.Block> blockFuture = new SafeFuture<>();
    when(eth1Provider.getGuaranteedEth1Block(any(UInt64.class))).thenReturn(blockFuture);

    final ArgumentCaptor<ValueObserver<UInt64>> captor =
        ArgumentCaptor.forClass(ValueObserver.class);
    verify(eth1HeadTracker, atLeastOnce()).subscribe(captor.capture());
    captor.getValue().onValueChanged(UInt64.valueOf(headBlockNumber));
    return blockFuture;
  }

  @Test
  void shouldFailIfStorageReplayFails() {
    when(eth1DepositStorageChannel.replayDepositEvents())
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("Fail")));
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();

    verify(eth1HeadTracker, never()).subscribe(any());
    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    inOrder.verifyNoMoreInteractions();
    assertThat(exceptionHandler.getUncaughtExceptions()).hasSize(1);
    assertThat(exceptionHandler.getUncaughtExceptions().get(0))
        .hasCauseInstanceOf(FatalServiceFailureException.class);
  }

  @Test
  void shouldStartWithStoredDepositsAndHeadBeforeMinGenesisTime() {
    final BigInteger headBlockNumber = BigInteger.valueOf(100);
    final BigInteger lastReplayedBlock = BigInteger.valueOf(10);
    final BigInteger lastReplayedDepositIndex = BigInteger.valueOf(11);
    when(eth1DepositStorageChannel.replayDepositEvents())
        .thenReturn(
            SafeFuture.completedFuture(
                ReplayDepositsResult.create(lastReplayedBlock, lastReplayedDepositIndex, false)));
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();
    notifyHeadBlock(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP - 1);

    verify(eth1EventsChannel).setLatestPublishedDeposit(UInt64.valueOf(lastReplayedDepositIndex));
    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Process blocks up to the current chain head
    inOrder
        .verify(depositProcessingController)
        .fetchDepositsInRange(lastReplayedBlock.add(BigInteger.ONE), headBlockNumber);

    // Then start the subscription from the block after head

    inOrder.verify(depositProcessingController).switchToBlockByBlockMode();

    inOrder.verify(depositProcessingController).startSubscription(BigInteger.valueOf(101));
    inOrder.verifyNoMoreInteractions();
    assertNoUncaughtExceptions();
  }

  @Test
  void shouldStartWithStoredDepositsAndHeadAfterMinGenesisTime() {
    final BigInteger headBlockNumber = BigInteger.valueOf(100);
    final BigInteger minGenesisBlockNumber = BigInteger.valueOf(60);
    final BigInteger lastReplayedBlock = BigInteger.valueOf(10);
    final BigInteger lastReplayedDepositIndex = BigInteger.valueOf(11);
    when(eth1DepositStorageChannel.replayDepositEvents())
        .thenReturn(
            SafeFuture.completedFuture(
                ReplayDepositsResult.create(lastReplayedBlock, lastReplayedDepositIndex, false)));
    withMinGenesisBlock(headBlockNumber, minGenesisBlockNumber);
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();
    notifyHeadBlock(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP + 1000);

    verify(eth1EventsChannel).setLatestPublishedDeposit(UInt64.valueOf(lastReplayedDepositIndex));
    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Find min genesis block
    inOrder
        .verify(minimumGenesisTimeBlockFinder)
        .findMinGenesisTimeBlockInHistory(headBlockNumber, asyncRunner);
    // Process blocks from after the last stored block to min genesis
    inOrder
        .verify(depositProcessingController)
        .fetchDepositsInRange(lastReplayedBlock.add(BigInteger.ONE), minGenesisBlockNumber);

    // Send min genesis event
    inOrder
        .verify(eth1EventsChannel)
        .onMinGenesisTimeBlock(
            new MinGenesisTimeBlockEvent(
                UInt64.valueOf(MIN_GENESIS_BLOCK_TIMESTAMP),
                UInt64.valueOf(minGenesisBlockNumber),
                Bytes32.ZERO));

    // Then start the subscription to process any blocks after min genesis
    // Adding one to ensure we don't process the min genesis block a second time
    inOrder
        .verify(depositProcessingController)
        .startSubscription(minGenesisBlockNumber.add(BigInteger.ONE));
    inOrder.verifyNoMoreInteractions();
    assertNoUncaughtExceptions();
  }

  @Test
  void shouldStartWithNoStoredDepositsAndHeadAfterMinGenesisTime() {
    final BigInteger headBlockNumber = BigInteger.valueOf(100);
    final BigInteger minGenesisBlockNumber = BigInteger.valueOf(60);
    when(eth1DepositStorageChannel.replayDepositEvents()).thenReturn(NOTHING_REPLAYED);
    withMinGenesisBlock(headBlockNumber, minGenesisBlockNumber);
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();
    notifyHeadBlock(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP + 1000);

    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Find min genesis block
    inOrder
        .verify(minimumGenesisTimeBlockFinder)
        .findMinGenesisTimeBlockInHistory(headBlockNumber, asyncRunner);
    // Process blocks from genesis to min genesis block
    inOrder
        .verify(depositProcessingController)
        .fetchDepositsInRange(BigInteger.ZERO, minGenesisBlockNumber);

    // Send min genesis event
    inOrder
        .verify(eth1EventsChannel)
        .onMinGenesisTimeBlock(
            new MinGenesisTimeBlockEvent(
                UInt64.valueOf(MIN_GENESIS_BLOCK_TIMESTAMP),
                UInt64.valueOf(minGenesisBlockNumber),
                Bytes32.ZERO));

    // Then start the subscription to process any blocks after min genesis
    // Adding one to ensure we don't process the min genesis block a second time
    inOrder
        .verify(depositProcessingController)
        .startSubscription(minGenesisBlockNumber.add(BigInteger.ONE));
    inOrder.verifyNoMoreInteractions();
    assertNoUncaughtExceptions();
  }

  @Test
  void shouldStartWithStoredDepositsAndMinGenesisReachedLongerChain() {
    final BigInteger headBlockNumber = BigInteger.valueOf(100);
    final BigInteger lastReplayedBlock = BigInteger.valueOf(70);
    final BigInteger lastReplayedDepositIndex = BigInteger.valueOf(11);
    when(eth1DepositStorageChannel.replayDepositEvents())
        .thenReturn(
            SafeFuture.completedFuture(
                ReplayDepositsResult.create(lastReplayedBlock, lastReplayedDepositIndex, true)));
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();
    notifyHeadBlock(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP + 1000);

    verify(eth1EventsChannel).setLatestPublishedDeposit(UInt64.valueOf(lastReplayedDepositIndex));
    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Just start processing blocks from after the last replayed block.
    inOrder
        .verify(depositProcessingController)
        .startSubscription(lastReplayedBlock.add(BigInteger.ONE));
    inOrder.verifyNoMoreInteractions();
    assertNoUncaughtExceptions();
  }

  @Test
  void shouldStartWithStoredDepositsAndMinGenesisReachedChainReorgedToBeShorter() {
    // Head block number has wound up being before the last block we already processed
    final BigInteger headBlockNumber = BigInteger.valueOf(60);
    final BigInteger lastReplayedBlock = BigInteger.valueOf(70);
    final BigInteger lastReplayedDepositIndex = BigInteger.valueOf(71);
    when(eth1DepositStorageChannel.replayDepositEvents())
        .thenReturn(
            SafeFuture.completedFuture(
                ReplayDepositsResult.create(lastReplayedBlock, lastReplayedDepositIndex, true)));
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();
    notifyHeadBlock(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP + 1000);

    verify(eth1EventsChannel).setLatestPublishedDeposit(UInt64.valueOf(lastReplayedDepositIndex));
    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Just start processing blocks from after the last replayed block.
    inOrder
        .verify(depositProcessingController)
        .startSubscription(lastReplayedBlock.add(BigInteger.ONE));
    inOrder.verifyNoMoreInteractions();
    assertNoUncaughtExceptions();
  }

  @Test
  void shouldStartWithStoredDepositsChainReorgedToBeShorter() {
    // Head block number has wound up being before the last block we already processed
    final BigInteger headBlockNumber = BigInteger.valueOf(60);
    final BigInteger lastReplayedBlock = BigInteger.valueOf(70);
    final BigInteger lastReplayedDepositIndex = BigInteger.valueOf(71);
    when(eth1DepositStorageChannel.replayDepositEvents())
        .thenReturn(
            SafeFuture.completedFuture(
                ReplayDepositsResult.create(lastReplayedBlock, lastReplayedDepositIndex, false)));
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();
    notifyHeadBlock(headBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP + 1000);

    verify(eth1EventsChannel).setLatestPublishedDeposit(UInt64.valueOf(lastReplayedDepositIndex));
    inOrder.verify(eth1DepositStorageChannel).replayDepositEvents();
    // Min genesis not reached so process block by block after the last replayed block
    inOrder.verify(depositProcessingController).switchToBlockByBlockMode();
    inOrder
        .verify(depositProcessingController)
        .startSubscription(lastReplayedBlock.add(BigInteger.ONE));
    inOrder.verifyNoMoreInteractions();
    assertNoUncaughtExceptions();
  }

  @Test
  void shouldStartFromSnapshotWhenLoaded() {
    final UInt64 deposits = UInt64.valueOf(100);
    final BigInteger lastBlockNumber = BigInteger.valueOf(1000);
    final DepositTreeSnapshot depositTreeSnapshot =
        new DepositTreeSnapshot(
            DEPOSIT_TREE_SNAPSHOT_SCHEMA,
            List.of(dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32()),
            dataStructureUtil.randomBytes32(),
            deposits.longValue(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64());
    final ReplayDepositsResult replayDepositsResult =
        ReplayDepositsResult.create(lastBlockNumber, Optional.of(BigInteger.valueOf(99)), true);

    when(eth1SnapshotLoaderChannel.loadDepositSnapshot())
        .thenReturn(
            SafeFuture.completedFuture(
                new LoadDepositSnapshotResult(
                    Optional.of(depositTreeSnapshot), replayDepositsResult)));
    when(depositProcessingController.fetchDepositsInRange(any(), any())).thenReturn(COMPLETE);

    manager.start();
    notifyHeadBlock(lastBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP + 1000);
    verifyNoInteractions(eth1DepositStorageChannel);
    verify(eth1EventsChannel).setLatestPublishedDeposit(deposits.decrement());
    inOrder
        .verify(depositProcessingController)
        .startSubscription(lastBlockNumber.add(BigInteger.ONE));
    inOrder.verifyNoMoreInteractions();
    assertNoUncaughtExceptions();
  }

  private void withMinGenesisBlock(
      final BigInteger headBlockNumber, final BigInteger minGenesisBlockNumber) {
    final Block minGenesisBlock = block(minGenesisBlockNumber, MIN_GENESIS_BLOCK_TIMESTAMP);
    when(minimumGenesisTimeBlockFinder.findMinGenesisTimeBlockInHistory(
            headBlockNumber, asyncRunner))
        .thenReturn(SafeFuture.completedFuture(minGenesisBlock));
  }

  private Block block(final BigInteger number, final long timestamp) {
    final Block block = mock(Block.class);
    when(block.getNumber()).thenReturn(number);
    when(block.getTimestamp()).thenReturn(BigInteger.valueOf(timestamp));
    when(block.getHash()).thenReturn(Bytes32.ZERO.toHexString());
    return block;
  }

  @SuppressWarnings("unchecked")
  private void notifyHeadBlock(final BigInteger blockNumber, final long timestamp) {
    final Block latestBlock = block(blockNumber, timestamp);
    when(eth1Provider.getGuaranteedEth1Block(UInt64.valueOf(blockNumber)))
        .thenReturn(SafeFuture.completedFuture(latestBlock));

    final ArgumentCaptor<ValueObserver<UInt64>> captor =
        ArgumentCaptor.forClass(ValueObserver.class);
    verify(eth1HeadTracker, atLeastOnce()).subscribe(captor.capture());
    captor.getValue().onValueChanged(UInt64.valueOf(blockNumber));
  }

  private void assertNoUncaughtExceptions() {
    assertThat(exceptionHandler.getUncaughtExceptions()).isEmpty();
  }
}
