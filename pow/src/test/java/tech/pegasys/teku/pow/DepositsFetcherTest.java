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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.primitives.Longs;
import java.math.BigInteger;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.contract.DepositContract;
import tech.pegasys.teku.pow.contract.RejectedRequestException;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;

public class DepositsFetcherTest {

  private static final int MAX_BLOCK_RANGE = 10_000;
  private final Eth1Provider eth1Provider = mock(Eth1Provider.class);
  private final Eth1EventsChannel eth1EventsChannel = mock(Eth1EventsChannel.class);
  private final DepositContract depositContract = mock(DepositContract.class);
  private final Eth1BlockFetcher eth1BlockFetcher = mock(Eth1BlockFetcher.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final DepositFetcher depositFetcher =
      new DepositFetcher(
          eth1Provider,
          eth1EventsChannel,
          depositContract,
          eth1BlockFetcher,
          asyncRunner,
          MAX_BLOCK_RANGE);

  @Test
  void depositsInConsecutiveBlocks() {
    SafeFuture<List<DepositContract.DepositEventEventResponse>> depositEventsFuture =
        mockContractEventsInRange(0, 10);

    mockBlockForEth1Provider("0x1234", 1, 1000);
    mockBlockForEth1Provider("0x2345", 2, 1014);
    mockBlockForEth1Provider("0x5678", 5, 1014);

    depositEventsFuture.complete(
        List.of(
            mockDepositEventEventResponse(1, "0x1234", 1),
            mockDepositEventEventResponse(2, "0x1234", 1),
            mockDepositEventEventResponse(3, "0x2345", 2),
            mockDepositEventEventResponse(4, "0x5678", 5)));

    depositFetcher.fetchDepositsInRange(BigInteger.ZERO, BigInteger.valueOf(10)).join();

    final InOrder inOrder = inOrder(eth1EventsChannel, eth1BlockFetcher);
    inOrder.verify(eth1BlockFetcher).fetch(BigInteger.ZERO, BigInteger.ZERO);
    inOrder.verify(eth1EventsChannel).onDepositsFromBlock(argThat(isEvent(1, 2)));
    inOrder.verify(eth1EventsChannel).onDepositsFromBlock(argThat(isEvent(2, 1)));
    inOrder.verify(eth1BlockFetcher).fetch(BigInteger.valueOf(3), BigInteger.valueOf(4));
    inOrder.verify(eth1EventsChannel).onDepositsFromBlock(argThat(isEvent(5, 1)));
    inOrder.verify(eth1BlockFetcher).fetch(BigInteger.valueOf(6), BigInteger.valueOf(10));
  }

  @Test
  void shouldUseMultipleBatchesWhenRangeIsLarge() {
    final BigInteger fromBlockNumber = BigInteger.ZERO;
    final BigInteger toBlockNumber = BigInteger.valueOf(3 * MAX_BLOCK_RANGE - 10);

    final BigInteger batch1End = fromBlockNumber.add(BigInteger.valueOf(MAX_BLOCK_RANGE));
    final BigInteger batch2Start = batch1End.add(BigInteger.ONE);
    final BigInteger batch2End = batch2Start.add(BigInteger.valueOf(MAX_BLOCK_RANGE));
    final BigInteger batch3Start = batch2End.add(BigInteger.ONE);
    final SafeFuture<List<DepositContract.DepositEventEventResponse>> batch1Response =
        new SafeFuture<>();
    final SafeFuture<List<DepositContract.DepositEventEventResponse>> batch2Response =
        new SafeFuture<>();
    final SafeFuture<List<DepositContract.DepositEventEventResponse>> batch3Response =
        new SafeFuture<>();

    when(depositContract.depositEventInRange(any(), any()))
        .thenReturn(batch1Response)
        .thenReturn(batch2Response)
        .thenReturn(batch3Response);

    final SafeFuture<Void> result =
        depositFetcher.fetchDepositsInRange(fromBlockNumber, toBlockNumber);
    assertThat(result).isNotDone();

    verify(depositContract)
        .depositEventInRange(
            refEq(DefaultBlockParameter.valueOf(fromBlockNumber)),
            refEq(DefaultBlockParameter.valueOf(batch1End)));
    verifyNoMoreInteractions(depositContract);

    batch1Response.complete(emptyList());
    verify(depositContract)
        .depositEventInRange(
            refEq(DefaultBlockParameter.valueOf(batch2Start)),
            refEq(DefaultBlockParameter.valueOf(batch2End)));
    verifyNoMoreInteractions(depositContract);

    batch2Response.complete(emptyList());
    verify(depositContract)
        .depositEventInRange(
            refEq(DefaultBlockParameter.valueOf(batch3Start)),
            refEq(DefaultBlockParameter.valueOf(toBlockNumber)));
    verifyNoMoreInteractions(depositContract);

    batch3Response.complete(emptyList());
    verifyNoMoreInteractions(depositContract);
  }

  @Test
  void shouldReduceBatchSizeWhenRequestIsRejected() {
    final BigInteger fromBlockNumber = BigInteger.ZERO;
    final BigInteger toBlockNumber = BigInteger.valueOf(MAX_BLOCK_RANGE + 100);

    final SafeFuture<List<DepositContract.DepositEventEventResponse>> request1Response =
        new SafeFuture<>();
    final SafeFuture<List<DepositContract.DepositEventEventResponse>> request2Response =
        new SafeFuture<>();
    final SafeFuture<List<DepositContract.DepositEventEventResponse>> request3Response =
        new SafeFuture<>();

    when(depositContract.depositEventInRange(any(), any()))
        .thenReturn(request1Response)
        .thenReturn(request2Response)
        .thenReturn(request3Response);

    final SafeFuture<Void> result =
        depositFetcher.fetchDepositsInRange(fromBlockNumber, toBlockNumber);
    assertThat(result).isNotDone();

    // First tries to request a full size batch
    verify(depositContract)
        .depositEventInRange(
            refEq(DefaultBlockParameter.valueOf(fromBlockNumber)),
            refEq(DefaultBlockParameter.valueOf(BigInteger.valueOf(MAX_BLOCK_RANGE))));
    verifyNoMoreInteractions(depositContract);

    // But there are too many results
    request1Response.completeExceptionally(new RejectedRequestException("Nah mate"));

    // So it halves the batch size and retries
    asyncRunner.executeQueuedActions();
    final BigInteger endSuccessfulRange =
        fromBlockNumber.add(BigInteger.valueOf(MAX_BLOCK_RANGE / 2));
    verify(depositContract)
        .depositEventInRange(
            refEq(DefaultBlockParameter.valueOf(fromBlockNumber)),
            refEq(DefaultBlockParameter.valueOf(endSuccessfulRange)));
    verifyNoMoreInteractions(depositContract);

    // And that works
    request2Response.complete(emptyList());

    // So it increases the batch size by 10% to avoid getting stuck with a very small batch size
    asyncRunner.executeQueuedActions();
    verify(depositContract)
        .depositEventInRange(
            refEq(DefaultBlockParameter.valueOf(endSuccessfulRange.add(BigInteger.ONE))),
            refEq(DefaultBlockParameter.valueOf(toBlockNumber)));
    verifyNoMoreInteractions(depositContract);
  }

  private void mockBlockForEth1Provider(String blockHash, long blockNumber, long timestamp) {
    EthBlock.Block block = mock(EthBlock.Block.class);
    when(block.getTimestamp()).thenReturn(BigInteger.valueOf(timestamp));
    when(block.getNumber()).thenReturn(BigInteger.valueOf(blockNumber));
    when(block.getHash()).thenReturn(blockHash);
    when(eth1Provider.getGuaranteedEth1Block(blockHash))
        .thenReturn(SafeFuture.completedFuture(block));
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

  private ArgumentMatcher<DepositsFromBlockEvent> isEvent(
      final long expectedBlockNumber, final long expectedNumberOfDeposits) {
    return argument ->
        argument.getBlockNumber().longValue() == expectedBlockNumber
            && argument.getDeposits().size() == expectedNumberOfDeposits;
  }
}
