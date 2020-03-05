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

package tech.pegasys.artemis.pow;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;

import com.google.common.primitives.UnsignedLong;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.subjects.PublishSubject;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.artemis.pow.api.Eth1EventsChannel;
import tech.pegasys.artemis.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.async.StubAsyncRunner;
import tech.pegasys.artemis.util.config.Constants;

public class DepositProcessingControllerTest {

  private Eth1Provider eth1Provider;
  private Eth1EventsChannel eth1EventsChannel;
  private DepositFetcher depositFetcher;
  private StubAsyncRunner asyncRunner;

  private DepositProcessingController depositProcessingController;
  private PublishSubject<EthBlock.Block> blockPublisher;

  @BeforeEach
  void setUp() {
    eth1Provider = mock(Eth1Provider.class);
    eth1EventsChannel = mock(Eth1EventsChannel.class);
    depositFetcher = mock(DepositFetcher.class);
    asyncRunner = new StubAsyncRunner();

    depositProcessingController =
        new DepositProcessingController(
            eth1Provider, eth1EventsChannel, asyncRunner, depositFetcher);

    blockPublisher = mockFlowablePublisher();
  }

  @Test
  void restartOnSubscriptionFailure() {
    blockPublisher.onError(new RuntimeException("Nope"));
    depositProcessingController.startSubscription(BigInteger.ONE);
    verify(eth1Provider).getLatestBlockFlowable();

    asyncRunner.executeQueuedActions();

    verify(eth1Provider, times(2)).getLatestBlockFlowable();
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

    Constants.MIN_GENESIS_DELAY = 1;
    // calculateCandidateGenesisTimestamp will return
    // blockTime + 2

    Constants.MIN_GENESIS_TIME = UnsignedLong.valueOf(100);

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

  private void mockBlockForEth1Provider(String blockHash, long blockNumber, long timestamp) {
    EthBlock.Block block = mock(EthBlock.Block.class);
    when(block.getTimestamp()).thenReturn(BigInteger.valueOf(timestamp));
    when(block.getNumber()).thenReturn(BigInteger.valueOf(blockNumber));
    when(block.getHash()).thenReturn(blockHash);
    when(eth1Provider.getGuaranteedEth1BlockFuture(UnsignedLong.valueOf(blockNumber)))
        .thenReturn(SafeFuture.completedFuture(block));
  }

  private void pushLatestCanonicalBlockWithNumber(long latestBlockNumber) {
    EthBlock.Block block = mock(EthBlock.Block.class);
    when(block.getNumber())
        .thenReturn(
            BigInteger.valueOf(latestBlockNumber).add(ETH1_FOLLOW_DISTANCE.bigIntegerValue()));
    blockPublisher.onNext(block);
  }

  private PublishSubject<EthBlock.Block> mockFlowablePublisher() {
    PublishSubject<EthBlock.Block> ps = PublishSubject.create();
    Flowable<EthBlock.Block> blockFlowable = ps.toFlowable(BackpressureStrategy.LATEST);
    when(eth1Provider.getLatestBlockFlowable()).thenReturn(blockFlowable);
    return ps;
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
