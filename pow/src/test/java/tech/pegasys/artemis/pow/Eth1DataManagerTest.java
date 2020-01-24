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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.utils.Numeric;
import tech.pegasys.artemis.pow.event.CacheEth1BlockEvent;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.AsyncRunnerTest;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

@SuppressWarnings({"rawtypes", "unchecked"})
public class Eth1DataManagerTest {

  private final EventBus eventBus = mock(EventBus.class);
  private Eth1DataManager eth1DataManager;
  private Web3j web3j;
  private DepositContractListener depositContractListener;

  private final AsyncRunner asyncRunner = new AsyncRunnerTest();

  private final Bytes32 HEX_STRING = Bytes32.fromHexString("0xdeadbeef");
  private final UnsignedLong LATEST_BLOCK_TIMESTAMP =
      UnsignedLong.valueOf(Instant.now().getEpochSecond());
  private final UnsignedLong INCONSEQUENTIAL_BLOCK_NUMBER = UnsignedLong.valueOf(100000);
  private final Request mockLatestBlockRequest =
      mockBlockRequest(INCONSEQUENTIAL_BLOCK_NUMBER, LATEST_BLOCK_TIMESTAMP);

  @BeforeEach
  void setUp() {
    web3j = mock(Web3j.class);
    depositContractListener = mock(DepositContractListener.class);
    when(depositContractListener.getDepositCount(any()))
        .thenReturn(SafeFuture.completedFuture(UnsignedLong.valueOf(1234)));
    when(depositContractListener.getDepositRoot(any()))
        .thenReturn(SafeFuture.completedFuture(HEX_STRING));
  }

  @Test
  void cacheStartup_blockActuallyInMidRange() {
    UnsignedLong midRangeTimestamp =
        Eth1DataManager.getCacheRangeUpperBound().minus(UnsignedLong.ONE);

    UnsignedLong firstUpwardNumber = INCONSEQUENTIAL_BLOCK_NUMBER.plus(UnsignedLong.ONE);

    UnsignedLong secondUpwardNumber = firstUpwardNumber.plus(UnsignedLong.ONE);
    UnsignedLong secondUpwardTimestamp = UnsignedLong.MAX_VALUE;

    UnsignedLong firstDownwardNumber = INCONSEQUENTIAL_BLOCK_NUMBER.minus(UnsignedLong.ONE);

    UnsignedLong secondDownwardNumber = firstDownwardNumber.minus(UnsignedLong.ONE);
    UnsignedLong secondDownwardTimestamp = UnsignedLong.ONE;

    Request midRangeBlockRequest =
        mockBlockRequest(INCONSEQUENTIAL_BLOCK_NUMBER, midRangeTimestamp);
    Request firstUpwardBlockRequest = mockBlockRequest(firstUpwardNumber, midRangeTimestamp);
    Request secondUpwardBlockRequest = mockBlockRequest(secondUpwardNumber, secondUpwardTimestamp);
    Request firstDownwardBlockRequest = mockBlockRequest(firstDownwardNumber, midRangeTimestamp);
    Request secondDownwardBlockRequest =
        mockBlockRequest(secondDownwardNumber, secondDownwardTimestamp);

    when(web3j.ethGetBlockByNumber(any(), eq(false)))
        .thenReturn(mockLatestBlockRequest)
        .thenReturn(midRangeBlockRequest)
        .thenReturn(firstUpwardBlockRequest)
        .thenReturn(secondUpwardBlockRequest)
        .thenReturn(firstDownwardBlockRequest)
        .thenReturn(secondDownwardBlockRequest);

    eth1DataManager = new Eth1DataManager(web3j, eventBus, depositContractListener, asyncRunner);

    ArgumentCaptor<CacheEth1BlockEvent> eventArgumentCaptor =
        ArgumentCaptor.forClass(CacheEth1BlockEvent.class);
    verify(eventBus, times(5)).post(eventArgumentCaptor.capture());

    ArgumentCaptor<DefaultBlockParameter> blockNumberArguments =
        ArgumentCaptor.forClass(DefaultBlockParameter.class);
    verify(web3j, times(6)).ethGetBlockByNumber(blockNumberArguments.capture(), eq(false));

    // The first mid-range number is only important in reference to upward and downward numbers
    assertThat(
            blockNumberArguments.getAllValues()
                .subList(2, blockNumberArguments.getAllValues().size()).stream()
                .map(DefaultBlockParameter::getValue)
                .map(Numeric::decodeQuantity)
                .map(UnsignedLong::valueOf)
                .collect(Collectors.toList()))
        .containsExactly(
            firstUpwardNumber, secondUpwardNumber, firstDownwardNumber, secondDownwardNumber);

    assertThat(
            eventArgumentCaptor.getAllValues().stream()
                .map(CacheEth1BlockEvent::getBlockTimestamp)
                .collect(Collectors.toList()))
        .containsExactly(
            midRangeTimestamp,
            midRangeTimestamp,
            secondUpwardTimestamp,
            midRangeTimestamp,
            secondDownwardTimestamp);
  }

  @Test
  void cacheStartup_recalculateSecondsToFindMidRangeBlock() {
    // First mid-range block does not actually have timestamp in range
    UnsignedLong firstMidRangeBlockTimestamp =
        Eth1DataManager.getCacheRangeLowerBound().minus(UnsignedLong.ONE);

    UnsignedLong secondMidRangeBlockNumber = INCONSEQUENTIAL_BLOCK_NUMBER;
    UnsignedLong secondMidRangeBlockTimestamp =
        Eth1DataManager.getCacheRangeUpperBound().minus(UnsignedLong.ONE);

    UnsignedLong upwardBlockNumber = secondMidRangeBlockNumber.plus(UnsignedLong.ONE);
    UnsignedLong upwardBlockTimestamp = UnsignedLong.MAX_VALUE;

    UnsignedLong downwardBlockNumber = secondMidRangeBlockNumber.minus(UnsignedLong.ONE);
    UnsignedLong downwardBlockTimestamp = UnsignedLong.ONE;

    Request firstMidRangeBlockRequest =
        mockBlockRequest(
            INCONSEQUENTIAL_BLOCK_NUMBER.minus(UnsignedLong.valueOf(1000)),
            firstMidRangeBlockTimestamp);
    Request secondMidRangeBlockRequest =
        mockBlockRequest(INCONSEQUENTIAL_BLOCK_NUMBER, secondMidRangeBlockTimestamp);
    Request upwardBlockRequest = mockBlockRequest(upwardBlockNumber, upwardBlockTimestamp);
    Request downwardBlockRequest = mockBlockRequest(downwardBlockNumber, downwardBlockTimestamp);

    when(web3j.ethGetBlockByNumber(any(), eq(false)))
        .thenReturn(mockLatestBlockRequest)
        .thenReturn(firstMidRangeBlockRequest)
        .thenReturn(secondMidRangeBlockRequest)
        .thenReturn(upwardBlockRequest)
        .thenReturn(downwardBlockRequest);

    eth1DataManager = new Eth1DataManager(web3j, eventBus, depositContractListener, asyncRunner);

    ArgumentCaptor<CacheEth1BlockEvent> eventArgumentCaptor =
        ArgumentCaptor.forClass(CacheEth1BlockEvent.class);
    verify(eventBus, times(3)).post(eventArgumentCaptor.capture());

    assertThat(
            eventArgumentCaptor.getAllValues().stream()
                .map(CacheEth1BlockEvent::getBlockTimestamp)
                .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(
            secondMidRangeBlockTimestamp, upwardBlockTimestamp, downwardBlockTimestamp);
  }

  @Test
  void cacheStartup_retryStartup() {
    Request mockRequest = mockFailedRequest();
    when(web3j.ethGetBlockByNumber(any(), eq(false))).thenReturn(mockRequest);

    eth1DataManager = new Eth1DataManager(web3j, eventBus, depositContractListener, asyncRunner);

    verify(web3j, times(Math.toIntExact(Constants.ETH1_CACHE_STARTUP_RETRY_GIVEUP)))
        .ethGetBlockByNumber(any(), eq(false));
  }

  @Test
  void onTick_startupNotDone() {

  }

  @Test
  void onTick_startupDoneGetNewBlocks() {}

  private Request<?, EthBlock> mockBlockRequest(UnsignedLong number, UnsignedLong timestamp) {
    return mockRequest(mockBlock(number, timestamp));
  }

  private EthBlock mockBlock(UnsignedLong number, UnsignedLong timestamp) {
    EthBlock mockBlock = mock(EthBlock.class);
    EthBlock.Block mockBlockBlock = mock(EthBlock.Block.class);
    when(mockBlock.getBlock()).thenReturn(mockBlockBlock);
    when(mockBlockBlock.getNumber()).thenReturn(number.bigIntegerValue());
    when(mockBlockBlock.getTimestamp()).thenReturn(timestamp.bigIntegerValue());
    when(mockBlockBlock.getHash()).thenReturn(HEX_STRING.toHexString());
    return mockBlock;
  }

  private Request<?, EthBlock> mockRequest(EthBlock block) {
    Request<?, EthBlock> mockRequest = mock(Request.class);
    when(mockRequest.sendAsync()).thenReturn(CompletableFuture.completedFuture(block));
    return mockRequest;
  }

  private Request mockFailedRequest() {
    Request mockRequest = mock(Request.class);
    when(mockRequest.sendAsync())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Nope")));
    return mockRequest;
  }
}
