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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.primitives.Longs;
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
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.async.StubAsyncRunner;

public class DepositsFetcherTest {

  private Eth1Provider eth1Provider;
  private Eth1EventsChannel eth1EventsChannel;
  private DepositContract depositContract;
  private AsyncRunner asyncRunner;

  private DepositFetcher depositFetcher;

  @BeforeEach
  void setUp() {
    eth1Provider = mock(Eth1Provider.class);
    eth1EventsChannel = mock(Eth1EventsChannel.class);
    depositContract = mock(DepositContract.class);
    asyncRunner = new StubAsyncRunner();

    depositFetcher =
        new DepositFetcher(eth1Provider, eth1EventsChannel, depositContract, asyncRunner);
  }

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

    verify(eth1EventsChannel).onDepositsFromBlock(argThat(isEvent(1, 2)));
    verify(eth1EventsChannel).onDepositsFromBlock(argThat(isEvent(2, 1)));
    verify(eth1EventsChannel).onDepositsFromBlock(argThat(isEvent(5, 1)));
    verifyNoMoreInteractions(eth1EventsChannel);
  }

  private void mockBlockForEth1Provider(String blockHash, long blockNumber, long timestamp) {
    EthBlock.Block block = mock(EthBlock.Block.class);
    when(block.getTimestamp()).thenReturn(BigInteger.valueOf(timestamp));
    when(block.getNumber()).thenReturn(BigInteger.valueOf(blockNumber));
    when(block.getHash()).thenReturn(blockHash);
    when(eth1Provider.getGuaranteedEth1BlockFuture(blockHash))
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
