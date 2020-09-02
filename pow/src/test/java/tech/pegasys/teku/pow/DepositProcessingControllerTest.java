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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.Eth1HeadTracker.HeadUpdatedSubscriber;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.util.config.Constants;

public class DepositProcessingControllerTest {

  private final Eth1Provider eth1Provider = mock(Eth1Provider.class);
  private final Eth1EventsChannel eth1EventsChannel = mock(Eth1EventsChannel.class);
  private final DepositFetcher depositFetcher = mock(DepositFetcher.class);
  private final Eth1BlockFetcher eth1BlockFetcher = mock(Eth1BlockFetcher.class);
  private final Eth1HeadTracker headTracker = Mockito.mock(Eth1HeadTracker.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final DepositProcessingController depositProcessingController =
      new DepositProcessingController(
          eth1Provider,
          eth1EventsChannel,
          asyncRunner,
          depositFetcher,
          eth1BlockFetcher,
          headTracker);

  @AfterEach
  void tearDown() {
    Constants.setConstants("minimal");
  }

  @Test
  void doesAnotherRequestWhenTheLatestCanonicalBlockGetsUpdatedDuringCurrentRequest() {

    SafeFuture<Void> future = new SafeFuture<>();
    when(depositFetcher.fetchDepositsInRange(BigInteger.ONE, BigInteger.valueOf(10)))
        .thenReturn(future);

    depositProcessingController.startSubscription(BigInteger.ONE);

    pushLatestCanonicalBlockWithNumber(10);

    verify(depositFetcher).fetchDepositsInRange(BigInteger.ONE, BigInteger.valueOf(10));

    pushLatestCanonicalBlockWithNumber(22);

    future.complete(null);

    verify(depositFetcher).fetchDepositsInRange(BigInteger.valueOf(11), BigInteger.valueOf(22));

    verifyNoMoreInteractions(eth1EventsChannel);
  }

  @Test
  void runSecondAttemptWhenFirstAttemptFails() {

    SafeFuture<Void> firstFuture = new SafeFuture<>();

    when(depositFetcher.fetchDepositsInRange(BigInteger.ONE, BigInteger.valueOf(10)))
        .thenReturn(firstFuture);

    depositProcessingController.startSubscription(BigInteger.ONE);

    pushLatestCanonicalBlockWithNumber(10);

    firstFuture.completeExceptionally(new RuntimeException("Nope"));

    asyncRunner.executeQueuedActions();

    verify(depositFetcher, times(2)).fetchDepositsInRange(BigInteger.ONE, BigInteger.valueOf(10));

    verifyNoMoreInteractions(depositFetcher);
  }

  @Test
  void fetchDepositsBlockOneBlockAtATime() {

    Constants.GENESIS_DELAY = UInt64.valueOf(2);
    // calculateCandidateGenesisTimestamp will return
    // blockTime + 2

    Constants.MIN_GENESIS_TIME = UInt64.valueOf(100);

    depositProcessingController.switchToBlockByBlockMode();
    depositProcessingController.startSubscription(BigInteger.ONE);

    SafeFuture<Void> future1 = new SafeFuture<>();
    when(depositFetcher.fetchDepositsInRange(BigInteger.ONE, BigInteger.ONE)).thenReturn(future1);

    mockBlockForEth1Provider("0xab", 1, 10);

    SafeFuture<Void> future2 = new SafeFuture<>();
    when(depositFetcher.fetchDepositsInRange(BigInteger.valueOf(2), BigInteger.valueOf(2)))
        .thenReturn(future2);

    mockBlockForEth1Provider("0xbb", 2, 15);

    SafeFuture<Void> future3 = new SafeFuture<>();
    when(depositFetcher.fetchDepositsInRange(BigInteger.valueOf(3), BigInteger.valueOf(3)))
        .thenReturn(future3);

    mockBlockForEth1Provider("0xbc", 3, 98);

    pushLatestCanonicalBlockWithNumber(3);

    verify(depositFetcher).fetchDepositsInRange(BigInteger.valueOf(1), BigInteger.valueOf(1));

    future1.complete(null);

    verify(depositFetcher).fetchDepositsInRange(BigInteger.valueOf(2), BigInteger.valueOf(2));

    future2.complete(null);

    verify(depositFetcher).fetchDepositsInRange(BigInteger.valueOf(3), BigInteger.valueOf(3));

    future3.complete(null);

    verify(eth1EventsChannel).onMinGenesisTimeBlock(argThat(isEvent("0xbc", 3, 98)));
  }

  @Test
  void shouldNotifyEth1BlockFetcherWhenLatestCanonicalBlockIsReached() {
    depositProcessingController.startSubscription(BigInteger.ZERO);
    final SafeFuture<Void> firstDepositsRequest = new SafeFuture<>();
    final SafeFuture<Void> secondDepositsRequest = new SafeFuture<>();
    when(depositFetcher.fetchDepositsInRange(BigInteger.ZERO, BigInteger.valueOf(1000)))
        .thenReturn(firstDepositsRequest);
    when(depositFetcher.fetchDepositsInRange(BigInteger.valueOf(1001), BigInteger.valueOf(1010)))
        .thenReturn(secondDepositsRequest);

    pushLatestCanonicalBlockWithNumber(1000);
    pushLatestCanonicalBlockWithNumber(1010);

    // Completing the first request doesn't notify because a second request is still pending
    firstDepositsRequest.complete(null);
    verifyNoInteractions(eth1BlockFetcher);

    // Second request brings us up to date with the latest block so we notify the block fetcher
    secondDepositsRequest.complete(null);
    verify(eth1BlockFetcher).onInSync(UInt64.valueOf(1010));
  }

  private void mockBlockForEth1Provider(String blockHash, long blockNumber, long timestamp) {
    EthBlock.Block block = mock(EthBlock.Block.class);
    when(block.getTimestamp()).thenReturn(BigInteger.valueOf(timestamp));
    when(block.getNumber()).thenReturn(BigInteger.valueOf(blockNumber));
    when(block.getHash()).thenReturn(blockHash);
    when(eth1Provider.getGuaranteedEth1Block(UInt64.valueOf(blockNumber)))
        .thenReturn(SafeFuture.completedFuture(block));
  }

  private void pushLatestCanonicalBlockWithNumber(long latestBlockNumber) {
    final ArgumentCaptor<HeadUpdatedSubscriber> captor =
        ArgumentCaptor.forClass(HeadUpdatedSubscriber.class);
    verify(headTracker).subscribe(captor.capture());
    final HeadUpdatedSubscriber subscriber = captor.getValue();
    subscriber.onHeadUpdated(UInt64.valueOf(latestBlockNumber));
  }

  private ArgumentMatcher<MinGenesisTimeBlockEvent> isEvent(
      final String expectedBlockHash,
      final long expectedBlockNumber,
      final long expectedTimestamp) {
    return argument ->
        argument.getTimestamp().longValue() == expectedTimestamp
            && argument.getBlockNumber().longValue() == expectedBlockNumber
            && argument.getBlockHash().equals(Bytes32.fromHexString(expectedBlockHash));
  }
}
