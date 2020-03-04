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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;

import com.google.common.primitives.Longs;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.subjects.PublishSubject;
import java.math.BigInteger;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;
import tech.pegasys.artemis.pow.api.Eth1EventsChannel;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.async.StubAsyncRunner;

public class DepositRequestManagerTest {

  private Eth1Provider eth1Provider;
  private Eth1EventsChannel depositEventChannel;
  private DepositContract depositContract;
  private StubAsyncRunner asyncRunner;

  private DepositProcessingController depositEventRequester;
  private PublishSubject<EthBlock.Block> blockPublisher;

  @BeforeEach
  void setUp() {
    eth1Provider = mock(Eth1Provider.class);
    depositEventChannel = mock(Eth1EventsChannel.class);
    depositContract = mock(DepositContract.class);
    asyncRunner = new StubAsyncRunner();

    depositEventRequester =
        new DepositProcessingController(
            eth1Provider, asyncRunner, depositEventChannel, depositContract);

    blockPublisher = mockFlowablePublisher();
  }

  @Test
  void restartOnSubscriptionFailure() {
    blockPublisher.onError(new RuntimeException("Nope"));
    depositEventRequester.startSubscription();
    verify(eth1Provider).getLatestBlockFlowable();

    asyncRunner.executeQueuedActions();

    verify(eth1Provider, times(2)).getLatestBlockFlowable();
  }

  @Test
  void depositsInConsecutiveBlocks() {
    SafeFuture<List<DepositContract.DepositEventEventResponse>> depositEventsFuture =
        mockContractEventsInRange(0, 10);

    mockBlockForEth1Provider("0x1234", 1, 1000);
    mockBlockForEth1Provider("0x2345", 2, 1014);

    depositEventRequester.startSubscription();

    pushLatestCanonicalBlockWithNumber(10);

    depositEventsFuture.complete(
        List.of(
            mockDepositEventEventResponse(1, "0x1234", 1),
            mockDepositEventEventResponse(2, "0x1234", 1),
            mockDepositEventEventResponse(3, "0x2345", 2)));

    verify(depositEventChannel).onDepositsFromBlock(argThat(isEvent(1, 2)));
    verify(depositEventChannel).onDepositsFromBlock(argThat(isEvent(2, 1)));
    verifyNoMoreInteractions(depositEventChannel);
  }

  @Test
  void noDepositsInSomeBlocksInRange() {
    SafeFuture<List<DepositContract.DepositEventEventResponse>> depositEventsFuture =
        mockContractEventsInRange(0, 10);

    mockBlockForEth1Provider("0x1234", 1, 1000);
    mockBlockForEth1Provider("0x2345", 2, 1014);
    mockBlockForEth1Provider("0x4567", 4, 1042);

    depositEventRequester.startSubscription();

    pushLatestCanonicalBlockWithNumber(10);

    depositEventsFuture.complete(
        List.of(
            mockDepositEventEventResponse(1, "0x1234", 1),
            mockDepositEventEventResponse(2, "0x2345", 2),
            mockDepositEventEventResponse(3, "0x4567", 4)));

    verify(depositEventChannel).onDepositsFromBlock(argThat(isEvent(1, 1)));
    verify(depositEventChannel).onDepositsFromBlock(argThat(isEvent(2, 1)));
    verify(depositEventChannel).onDepositsFromBlock(argThat(isEvent(4, 1)));
    verifyNoMoreInteractions(depositEventChannel);
  }

  @Test
  void doesAnotherRequestWhenTheLatestCanonicalBlockGetsUpdatedDuringCurrentRequest() {

    SafeFuture<List<DepositContract.DepositEventEventResponse>> firstRequestFuture =
        mockContractEventsInRange(0, 10);

    SafeFuture<List<DepositContract.DepositEventEventResponse>> secondRequestFuture =
        mockContractEventsInRange(11, 22);

    mockBlockForEth1Provider("0x0001", 1, 1000);
    mockBlockForEth1Provider("0x0002", 2, 1014);
    mockBlockForEth1Provider("0x0004", 4, 1042);
    mockBlockForEth1Provider("0x0010", 10, 1140);
    mockBlockForEth1Provider("0x0011", 11, 1154);
    mockBlockForEth1Provider("0x0014", 14, 1196);

    depositEventRequester.startSubscription();

    pushLatestCanonicalBlockWithNumber(10);

    firstRequestFuture.complete(
        List.of(
            mockDepositEventEventResponse(1, "0x0001", 1),
            mockDepositEventEventResponse(2, "0x0001", 1),
            mockDepositEventEventResponse(3, "0x0002", 2),
            mockDepositEventEventResponse(4, "0x0004", 4),
            mockDepositEventEventResponse(5, "0x0010", 10)));

    pushLatestCanonicalBlockWithNumber(22);

    secondRequestFuture.complete(
        List.of(
            mockDepositEventEventResponse(6, "0x0011", 11),
            mockDepositEventEventResponse(7, "0x0014", 14)));

    verify(depositEventChannel).onDepositsFromBlock(argThat(isEvent(1, 2)));
    verify(depositEventChannel).onDepositsFromBlock(argThat(isEvent(2, 1)));
    verify(depositEventChannel).onDepositsFromBlock(argThat(isEvent(4, 1)));
    verify(depositEventChannel).onDepositsFromBlock(argThat(isEvent(10, 1)));
    verify(depositEventChannel).onDepositsFromBlock(argThat(isEvent(11, 1)));
    verify(depositEventChannel).onDepositsFromBlock(argThat(isEvent(14, 1)));
    verifyNoMoreInteractions(depositEventChannel);
  }

  @Test
  void runSecondAttemptWhenFirstAttemptFails() {

    SafeFuture<List<DepositContract.DepositEventEventResponse>> firstRequestFuture =
        mockContractEventsInRange(0, 10);

    mockBlockForEth1Provider("0x0001", 1, 1000);
    mockBlockForEth1Provider("0x0002", 2, 1014);

    depositEventRequester.startSubscription();

    pushLatestCanonicalBlockWithNumber(10);

    firstRequestFuture.completeExceptionally(new RuntimeException("Nope"));

    SafeFuture<List<DepositContract.DepositEventEventResponse>> secondRequestFuture =
        mockContractEventsInRange(0, 10);

    secondRequestFuture.complete(
        List.of(
            mockDepositEventEventResponse(1, "0x0001", 1),
            mockDepositEventEventResponse(2, "0x0002", 2)));

    asyncRunner.executeQueuedActions();

    verify(depositEventChannel).onDepositsFromBlock(argThat(isEvent(1, 1)));
    verify(depositEventChannel).onDepositsFromBlock(argThat(isEvent(2, 1)));
    verifyNoMoreInteractions(depositEventChannel);
  }

  private void mockBlockForEth1Provider(String blockHash, long blockNumber, long timestamp) {
    EthBlock.Block block = mock(EthBlock.Block.class);
    when(block.getTimestamp()).thenReturn(BigInteger.valueOf(timestamp));
    when(block.getNumber()).thenReturn(BigInteger.valueOf(blockNumber));
    when(block.getHash()).thenReturn(blockHash);
    when(eth1Provider.getEth1BlockFuture(blockHash)).thenReturn(SafeFuture.completedFuture(block));
  }

  private void pushLatestCanonicalBlockWithNumber(long latestBlockNumber) {
    EthBlock.Block block = mock(EthBlock.Block.class);
    when(block.getNumber())
        .thenReturn(
            BigInteger.valueOf(latestBlockNumber).add(ETH1_FOLLOW_DISTANCE.bigIntegerValue()));
    blockPublisher.onNext(block);
  }

  private SafeFuture<List<DepositContract.DepositEventEventResponse>> mockContractEventsInRange(
      long fromBlockNumber, long toBlockNumber) {
    SafeFuture<List<DepositContract.DepositEventEventResponse>> safeFuture = new SafeFuture<>();
    doReturn(safeFuture)
        .when(depositContract)
        .depositEventInRange(
            argThat(
                argument ->
                    Numeric.decodeQuantity(argument.getValue())
                        .equals(BigInteger.valueOf(fromBlockNumber))),
            argThat(
                argument ->
                    Numeric.decodeQuantity(argument.getValue())
                        .equals(BigInteger.valueOf(toBlockNumber))));
    return safeFuture;
  }

  private DepositContract.DepositEventEventResponse mockDepositEventEventResponse(
      long index, String blockHash, long blockNumber) {
    Log log = mock(Log.class);
    when(log.getBlockHash()).thenReturn(blockHash);
    when(log.getBlockNumber()).thenReturn(BigInteger.valueOf(blockNumber));

    DepositContract.DepositEventEventResponse depositEventEventResponse =
        new DepositContract.DepositEventEventResponse();
    depositEventEventResponse.pubkey = new byte[48];
    depositEventEventResponse.withdrawal_credentials = new byte[32];
    depositEventEventResponse.amount = Longs.toByteArray(0);
    depositEventEventResponse.signature = new byte[96];

    depositEventEventResponse.log = log;
    depositEventEventResponse.index = Bytes.wrap(Longs.toByteArray(index)).reverse().toArray();
    return depositEventEventResponse;
  }

  private PublishSubject<EthBlock.Block> mockFlowablePublisher() {
    PublishSubject<EthBlock.Block> ps = PublishSubject.create();
    Flowable<EthBlock.Block> blockFlowable = ps.toFlowable(BackpressureStrategy.LATEST);
    when(eth1Provider.getLatestBlockFlowable()).thenReturn(blockFlowable);
    return ps;
  }

  private ArgumentMatcher<DepositsFromBlockEvent> isEvent(
      final long expectedBlockNumber, final long expectedNumberOfDeposits) {
    return argument ->
        argument.getBlockNumber().longValue() == expectedBlockNumber
            && argument.getDeposits().size() == expectedNumberOfDeposits;
  }
}
