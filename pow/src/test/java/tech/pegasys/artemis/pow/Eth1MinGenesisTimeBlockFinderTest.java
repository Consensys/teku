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
import tech.pegasys.artemis.pow.api.MinGenesisTimeBlockEventChannel;
import tech.pegasys.artemis.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.async.StubAsyncRunner;
import tech.pegasys.artemis.util.config.Constants;

public class Eth1MinGenesisTimeBlockFinderTest {

  private Eth1Provider eth1Provider;
  private MinGenesisTimeBlockEventChannel minGenesisTimeBlockEventChannel;
  private StubAsyncRunner asyncRunner;

  private Eth1Manager eth1Manager;
  private PublishSubject<EthBlock.Block> blockPublisher;

  @BeforeEach
  void setUp() {
    eth1Provider = mock(Eth1Provider.class);
    minGenesisTimeBlockEventChannel = mock(MinGenesisTimeBlockEventChannel.class);
    asyncRunner = new StubAsyncRunner();

    eth1Manager = new Eth1Manager(eth1Provider, minGenesisTimeBlockEventChannel, asyncRunner);

    blockPublisher = mockFlowablePublisher();

    Constants.SECONDS_PER_DAY = 1;
    // calculateCandidateGenesisTimestamp will return
    // blockTime + 2
  }

  @Test
  void mainRetry() {
    when(eth1Provider.getLatestEth1BlockFuture())
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Nope")));

    eth1Manager.start();
    verify(eth1Provider).getLatestEth1BlockFuture();

    asyncRunner.executeQueuedActions();
    verify(eth1Provider, times(2)).getLatestEth1BlockFuture();
  }

  @Test
  void blockAtFollowDistance_isTheValidBlock() {
    setMinGenesisTime(10);

    mockLatestCanonicalBlock(10);
    mockBlockForEth1Provider("0x01", 10, 8);

    eth1Manager.start();
    verify(minGenesisTimeBlockEventChannel).onMinGenesisTimeBlock(argThat(isEvent("0x01", 10, 8)));
  }

  @Test
  void blockInHistory_belowEstimatedBlock() {
    Constants.SECONDS_PER_ETH1_BLOCK = UnsignedLong.valueOf(5);

    mockLatestCanonicalBlock(1000);
    mockBlockForEth1Provider("0xbf", 1000, 1000);

    setMinGenesisTime(500);

    // 1002 - 502 = 500, 500 / 5 = 100, the estimated genesis block number should be:
    // 1000 - 100 = 900

    mockBlockForEth1Provider("0x11", 900, 600);

    // since the estimated block still had higher timestamp than min genesis, we should explore
    // downwards

    mockBlockForEth1Provider("0x08", 899, 510);

    // since the second requested block still had higher timestamp than min genesis, we should
    // explore downwards

    mockBlockForEth1Provider("0x00", 898, 490);

    // since the last requested block now had lower timestamp than min genesis, we should publish
    // the block
    // right before this as the first valid block

    eth1Manager.start();

    verify(minGenesisTimeBlockEventChannel)
        .onMinGenesisTimeBlock(argThat(isEvent("0x08", 899, 510)));
  }

  @Test
  void blockInHistory_AboveEstimatedBlock() {
    Constants.SECONDS_PER_ETH1_BLOCK = UnsignedLong.valueOf(5);

    mockLatestCanonicalBlock(1000);
    mockBlockForEth1Provider("0xbf", 1000, 1000);

    setMinGenesisTime(500);

    // 1002 - 502 = 500, 500 / 5 = 100, the estimated genesis block number should be:
    // 1000 - 100 = 900

    mockBlockForEth1Provider("0x08", 900, 400);

    // since the estimated block still had lower timestamp than min genesis, we should explore
    // upwards

    mockBlockForEth1Provider("0x08", 901, 450);

    // since the second requested block still had lower timestamp than min genesis, we should
    // explore upwards

    mockBlockForEth1Provider("0x08", 902, 510);

    // since the last requested block now had higher timestamp than min genesis, we should publish
    // the block

    eth1Manager.start();

    verify(minGenesisTimeBlockEventChannel)
        .onMinGenesisTimeBlock(argThat(isEvent("0x08", 902, 510)));
  }

  @Test
  void blockInHistory_EstimatedBlockIsTheValidBlock() {
    Constants.SECONDS_PER_ETH1_BLOCK = UnsignedLong.valueOf(5);

    mockLatestCanonicalBlock(1000);
    mockBlockForEth1Provider("0xbf", 1000, 1000);

    setMinGenesisTime(502);

    // 1002 - 502 = 500, 500 / 5 = 100, the estimated genesis block number should be:
    // 1000 - 100 = 900

    mockBlockForEth1Provider("0x08", 900, 500);

    // since the genesis time calculated from the , we should publish the block

    eth1Manager.start();

    verify(minGenesisTimeBlockEventChannel)
        .onMinGenesisTimeBlock(argThat(isEvent("0x08", 900, 500)));
  }

  @Test
  void waitForFirstValidBlock() {
    mockLatestCanonicalBlock(1000);
    mockBlockForEth1Provider("0xbf", 1000, 1000);

    setMinGenesisTime(1100);

    eth1Manager.start();

    verify(eth1Provider).getLatestBlockFlowable();

    mockBlockForEth1Provider("0xbf", 1001, 1098);
    pushLatestCanonicalBlockWithNumber(1001);

    verify(minGenesisTimeBlockEventChannel)
        .onMinGenesisTimeBlock(argThat(isEvent("0xbf", 1001, 1098)));
  }

  @Test
  void waitForFirstValidBlock_errorAndRecover() {
    mockLatestCanonicalBlock(1000);
    mockBlockForEth1Provider("0xaf", 1000, 1000);

    setMinGenesisTime(1200);

    mockBlockForEth1Provider("0xcf", 1001, 1098);

    EthBlock.Block mockBlock = mock(EthBlock.Block.class);
    when(mockBlock.getHash()).thenReturn("0xbf");
    when(mockBlock.getNumber()).thenReturn(BigInteger.valueOf(1002));
    when(mockBlock.getTimestamp()).thenReturn(BigInteger.valueOf(1201));
    when(eth1Provider.getEth1BlockFuture(UnsignedLong.valueOf(1002)))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Nope")))
        .thenReturn(SafeFuture.completedFuture(mockBlock));

    eth1Manager.start();

    verify(eth1Provider).getLatestBlockFlowable();

    pushLatestCanonicalBlockWithNumber(1001);

    System.out.println(asyncRunner.countDelayedActions());

    pushLatestCanonicalBlockWithNumber(1002);

    asyncRunner.executeQueuedActions();

    pushLatestCanonicalBlockWithNumber(1002);
    verify(eth1Provider, times(2)).getLatestBlockFlowable();

    verify(minGenesisTimeBlockEventChannel)
        .onMinGenesisTimeBlock(argThat(isEvent("0xbf", 1002, 1201)));
  }

  @Test
  void waitForFirstValidBlock_failureScenario() {
    blockPublisher.onError(new RuntimeException("Nope"));

    mockLatestCanonicalBlock(1000);
    mockBlockForEth1Provider("0xbf", 1000, 1000);

    setMinGenesisTime(1100);

    eth1Manager.start();

    verify(eth1Provider).getLatestBlockFlowable();

    asyncRunner.executeQueuedActions();

    verify(eth1Provider, times(2)).getLatestBlockFlowable();
  }

  private void mockLatestCanonicalBlock(long latestBlockNumber) {
    EthBlock.Block block = mock(EthBlock.Block.class);
    when(block.getNumber())
        .thenReturn(
            BigInteger.valueOf(latestBlockNumber).add(ETH1_FOLLOW_DISTANCE.bigIntegerValue()));
    when(eth1Provider.getLatestEth1BlockFuture()).thenReturn(SafeFuture.completedFuture(block));
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

  private void mockBlockForEth1Provider(String blockHash, long blockNumber, long timestamp) {
    EthBlock.Block block = mock(EthBlock.Block.class);
    when(block.getTimestamp()).thenReturn(BigInteger.valueOf(timestamp));
    when(block.getNumber()).thenReturn(BigInteger.valueOf(blockNumber));
    when(block.getHash()).thenReturn(blockHash);
    when(eth1Provider.getEth1BlockFuture(UnsignedLong.valueOf(blockNumber)))
        .thenReturn(SafeFuture.completedFuture(block));
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

  private void setMinGenesisTime(long time) {
    Constants.MIN_GENESIS_TIME = UnsignedLong.valueOf(time);
  }
}
