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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;

import com.google.common.primitives.Longs;
import com.google.common.primitives.UnsignedLong;
import io.reactivex.Flowable;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;
import tech.pegasys.artemis.pow.api.DepositEventChannel;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.async.StubAsyncRunner;

public class DepositRequestManagerTest {

  private Eth1Provider eth1Provider;
  private DepositEventChannel depositEventChannel;
  private DepositContract depositContract;
  private AsyncRunner asyncRunner;

  private DepositRequestManager depositRequestManager;

  @BeforeEach
  void setUp() {
    eth1Provider = mock(Eth1Provider.class);
    depositEventChannel = mock(DepositEventChannel.class);
    depositContract = mock(DepositContract.class);
    asyncRunner = new StubAsyncRunner();

    depositRequestManager =
        spy(
            new DepositRequestManager(
                eth1Provider, asyncRunner, depositEventChannel, depositContract));
  }

  @Test
  void restartOnSubscriptionFailure() {
    mockLatestCanonicalBlockNumber(1);
    depositRequestManager.start();
    verify(depositRequestManager, atLeast(2)).start();
  }

  @Test
  void checkTheRangeAskedForIsCorrect() {
    mockLatestCanonicalBlockNumber(10);
    depositRequestManager.start();
    ArgumentCaptor<DefaultBlockParameter> fromBlockArgumentCaptor =
        ArgumentCaptor.forClass(DefaultBlockParameter.class);
    ArgumentCaptor<DefaultBlockParameter> toBlockArgumentCaptor =
        ArgumentCaptor.forClass(DefaultBlockParameter.class);
    verify(depositContract)
        .depositEventEventsInRange(
            fromBlockArgumentCaptor.capture(), toBlockArgumentCaptor.capture());

    assertThat(Numeric.decodeQuantity(fromBlockArgumentCaptor.getValue().getValue())).isEqualTo(0);
    assertThat(Numeric.decodeQuantity(toBlockArgumentCaptor.getValue().getValue())).isEqualTo(10);
  }

  @Test
  void depositsInConsecutiveBlocks() {
    mockLatestCanonicalBlockNumber(10);

    mockContractEventsInRange(
        0,
        10,
        List.of(
            mockDepositEventEventResponse(1, "0x1234", 1),
            mockDepositEventEventResponse(2, "0x1234", 1),
            mockDepositEventEventResponse(3, "0x2345", 2)));

    mockBlockForEth1Provider("0x1234", 1, 1000);
    mockBlockForEth1Provider("0x2345", 2, 1014);

    depositRequestManager.start();

    ArgumentCaptor<DepositsFromBlockEvent> eventArgumentCaptor =
        ArgumentCaptor.forClass(DepositsFromBlockEvent.class);
    verify(depositEventChannel, times(2)).notifyDepositsFromBlock(eventArgumentCaptor.capture());
    assertThat(eventArgumentCaptor.getAllValues()).hasSize(2);
    assertThat(
            eventArgumentCaptor.getAllValues().stream()
                .map(DepositsFromBlockEvent::getBlockNumber)
                .map(UnsignedLong::longValue)
                .collect(Collectors.toList()))
        .containsExactly(1L, 2L);
  }

  @Test
  void noDepositsInSomeBlocksInRange() {
    mockLatestCanonicalBlockNumber(10);

    mockContractEventsInRange(
        0,
        10,
        List.of(
            mockDepositEventEventResponse(1, "0x1234", 1),
            mockDepositEventEventResponse(2, "0x1234", 1),
            mockDepositEventEventResponse(3, "0x2345", 2),
            mockDepositEventEventResponse(4, "0x4567", 4)));

    mockBlockForEth1Provider("0x1234", 1, 1000);
    mockBlockForEth1Provider("0x2345", 2, 1014);
    mockBlockForEth1Provider("0x4567", 4, 1042);

    depositRequestManager.start();

    ArgumentCaptor<DepositsFromBlockEvent> eventArgumentCaptor =
        ArgumentCaptor.forClass(DepositsFromBlockEvent.class);
    verify(depositEventChannel, times(3)).notifyDepositsFromBlock(eventArgumentCaptor.capture());
    assertThat(eventArgumentCaptor.getAllValues()).hasSize(3);
    assertThat(
            eventArgumentCaptor.getAllValues().stream()
                .map(DepositsFromBlockEvent::getBlockNumber)
                .map(UnsignedLong::longValue)
                .collect(Collectors.toList()))
        .containsExactly(1L, 2L, 4L);
  }

  private void mockBlockForEth1Provider(String blockHash, long blockNumber, long timestamp) {
    EthBlock.Block block = mock(EthBlock.Block.class);
    when(block.getTimestamp()).thenReturn(BigInteger.valueOf(timestamp));
    when(block.getNumber()).thenReturn(BigInteger.valueOf(blockNumber));
    when(block.getHash()).thenReturn(blockHash);
    when(eth1Provider.getEth1BlockFuture(blockHash)).thenReturn(SafeFuture.completedFuture(block));
  }

  private void mockLatestCanonicalBlockNumber(long latestBlockNumber) {
    EthBlock.Block block = mock(EthBlock.Block.class);
    when(block.getNumber())
        .thenReturn(
            BigInteger.valueOf(latestBlockNumber).add(ETH1_FOLLOW_DISTANCE.bigIntegerValue()));
    Flowable<EthBlock.Block> blockFlowable = Flowable.just(block);
    when(eth1Provider.getLatestBlockFlowable()).thenReturn(blockFlowable);
  }

  private void mockContractEventsInRange(
      long fromBlockNumber,
      long toBlockNumber,
      List<DepositContract.DepositEventEventResponse> listOfEventResponsesToReturn) {
    when(depositContract.depositEventEventsInRange(
            argThat(
                argument ->
                    Numeric.decodeQuantity(argument.getValue())
                        .equals(BigInteger.valueOf(fromBlockNumber))),
            argThat(
                argument ->
                    Numeric.decodeQuantity(argument.getValue())
                        .equals(BigInteger.valueOf(toBlockNumber)))))
        .thenReturn(SafeFuture.completedFuture(listOfEventResponsesToReturn));
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

  private DefaultBlockParameter blockNum(long i) {
    return DefaultBlockParameter.valueOf(BigInteger.valueOf(i));
  }
}
