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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InterruptedIOException;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;
import tech.pegasys.teku.beacon.pow.exception.Eth1RequestException;
import tech.pegasys.teku.beacon.pow.exception.RejectedRequestException;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@SuppressWarnings("FutureReturnValueIgnored")
public class FallbackAwareEth1ProviderSelectorTest {

  public static final String EXPECTED_TIMEOUT_MESSAGE =
      "Request to eth1 endpoint timed out. Retrying with next eth1 endpoint";
  StubAsyncRunner asyncRunner;
  final MonitorableEth1Provider node1 = mock(MonitorableEth1Provider.class);
  final MonitorableEth1Provider node2 = mock(MonitorableEth1Provider.class);
  final MonitorableEth1Provider node3 = mock(MonitorableEth1Provider.class);
  final List<MonitorableEth1Provider> providers = Arrays.asList(node1, node2, node3);
  Eth1ProviderSelector providerSelector;
  FallbackAwareEth1Provider fallbackAwareEth1Provider;
  final EthFilter ethLogFilter =
      new EthFilter(
          DefaultBlockParameter.valueOf(BigInteger.ONE),
          DefaultBlockParameter.valueOf(BigInteger.TEN),
          "0");

  @BeforeEach
  public void setup() {
    asyncRunner = new StubAsyncRunner();
    providerSelector = new Eth1ProviderSelector(providers);
    providerSelector.notifyValidationCompletion();
    fallbackAwareEth1Provider = new FallbackAwareEth1Provider(providerSelector, asyncRunner);

    when(node1.isValid()).thenReturn(true);
    when(node2.isValid()).thenReturn(true);
    when(node3.isValid()).thenReturn(true);
  }

  @Test
  void shouldFallbackOnGetLogs() throws ExecutionException, InterruptedException {
    // node 1 ready
    when(node1.ethGetLogs(ethLogFilter)).thenReturn(readyProviderGetLogs());
    assertThat(fallbackAwareEth1Provider.ethGetLogs(ethLogFilter)).isCompleted();

    // node 1 failing and node 2 ready
    when(node1.ethGetLogs(ethLogFilter))
        .thenReturn(failingProviderGetLogsWithError(new RuntimeException("error")));
    when(node2.ethGetLogs(ethLogFilter)).thenReturn(readyProviderGetLogs());
    final SafeFuture<List<EthLog.LogResult<?>>> logList =
        fallbackAwareEth1Provider.ethGetLogs(ethLogFilter);
    assertThat(logList.get()).isNotNull();

    verify(node1, times(2)).ethGetLogs(ethLogFilter);
    verify(node2, times(1)).ethGetLogs(ethLogFilter);
  }

  @Test
  void shouldFallbackOnGetLogs_smallerRangeException_timeout() {
    // all nodes failing, one with socket timeout error
    when(node1.ethGetLogs(ethLogFilter))
        .thenReturn(failingProviderGetLogsWithError(new RuntimeException("error")));
    when(node2.ethGetLogs(ethLogFilter))
        .thenReturn(
            failingProviderGetLogsWithError(new SocketTimeoutException("socket timeout error")));
    when(node3.ethGetLogs(ethLogFilter))
        .thenReturn(failingProviderGetLogsWithError(new RuntimeException("error")));

    assertThat(
            fallbackAwareEth1Provider
                .ethGetLogs(ethLogFilter)
                .thenRun(() -> fail("should fail!"))
                .exceptionallyCompose(
                    err -> {
                      final Throwable errCause = err.getCause();
                      assertThat(errCause).isInstanceOf(Eth1RequestException.class);
                      assertThat(
                              ((Eth1RequestException) errCause)
                                  .containsExceptionSolvableWithSmallerRange())
                          .isTrue();
                      return SafeFuture.COMPLETE;
                    }))
        .isCompleted();
  }

  @Test
  void shouldFallbackOnGetLogs_smallerRangeException_interruptedIOException() {
    // all nodes failing, one with socket timeout error
    when(node1.ethGetLogs(ethLogFilter))
        .thenReturn(failingProviderGetLogsWithError(new RuntimeException("error")));
    when(node2.ethGetLogs(ethLogFilter))
        .thenReturn(failingProviderGetLogsWithError(new InterruptedIOException("timeout")));
    when(node3.ethGetLogs(ethLogFilter))
        .thenReturn(failingProviderGetLogsWithError(new RuntimeException("error")));

    assertThat(
            fallbackAwareEth1Provider
                .ethGetLogs(ethLogFilter)
                .thenRun(() -> fail("should fail!"))
                .exceptionallyCompose(
                    err -> {
                      final Throwable errCause = err.getCause();
                      assertThat(errCause).isInstanceOf(Eth1RequestException.class);
                      assertThat(
                              ((Eth1RequestException) errCause)
                                  .containsExceptionSolvableWithSmallerRange())
                          .isTrue();
                      return SafeFuture.COMPLETE;
                    }))
        .isCompleted();
  }

  @Test
  void shouldNotIncludeTimeoutExceptionStacktraceInLogs() {
    // One node failing with socket timeout error
    when(node2.isValid()).thenReturn(false);
    when(node3.isValid()).thenReturn(false);
    when(node1.ethGetLogs(ethLogFilter))
        .thenReturn(
            failingProviderGetLogsWithError(new SocketTimeoutException("socket timeout error")));
    LogCaptor logCaptor = LogCaptor.forClass(FallbackAwareEth1Provider.class);
    SafeFuture<?> future = fallbackAwareEth1Provider.ethGetLogs(ethLogFilter);
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasCauseInstanceOf(Eth1RequestException.class);
    final String expectedWarningMessage = EXPECTED_TIMEOUT_MESSAGE;
    assertThat(logCaptor.getWarnLogs()).containsExactly(expectedWarningMessage);
    assertThat(logCaptor.getLogEvents()).isNotEmpty();
    assertThat(logCaptor.getLogEvents().get(0).getMessage()).isEqualTo(expectedWarningMessage);
    assertThat(logCaptor.getLogEvents().get(0).getThrowable()).isEmpty();
  }

  @Test
  void shouldNotIncludeTimeoutExceptionStacktraceInLogs_interruptedIOException() {
    // One node failing with socket timeout error
    when(node2.isValid()).thenReturn(false);
    when(node3.isValid()).thenReturn(false);
    when(node1.ethGetLogs(ethLogFilter))
        .thenReturn(
            failingProviderGetLogsWithError(new InterruptedIOException("socket timeout error")));
    LogCaptor logCaptor = LogCaptor.forClass(FallbackAwareEth1Provider.class);
    SafeFuture<?> future = fallbackAwareEth1Provider.ethGetLogs(ethLogFilter);
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasCauseInstanceOf(Eth1RequestException.class);
    final String expectedWarningMessage = EXPECTED_TIMEOUT_MESSAGE;
    assertThat(logCaptor.getWarnLogs()).containsExactly(expectedWarningMessage);
    assertThat(logCaptor.getLogEvents()).isNotEmpty();
    assertThat(logCaptor.getLogEvents().get(0).getMessage()).isEqualTo(expectedWarningMessage);
    assertThat(logCaptor.getLogEvents().get(0).getThrowable()).isEmpty();
  }

  @Test
  void shouldFallbackOnGetLogs_smallerRangeException_rejected() {
    // all nodes failing, one with rejected request error
    when(node1.ethGetLogs(ethLogFilter))
        .thenReturn(failingProviderGetLogsWithError(new RuntimeException("error")));
    when(node2.ethGetLogs(ethLogFilter))
        .thenReturn(
            failingProviderGetLogsWithError(new RejectedRequestException(-32005, "too many")));
    when(node3.ethGetLogs(ethLogFilter))
        .thenReturn(failingProviderGetLogsWithError(new RuntimeException("error")));

    assertThat(
            fallbackAwareEth1Provider
                .ethGetLogs(ethLogFilter)
                .thenRun(() -> fail("should fail!"))
                .exceptionallyCompose(
                    err -> {
                      final Throwable errCause = err.getCause();
                      assertThat(errCause).isInstanceOf(Eth1RequestException.class);
                      assertThat(
                              ((Eth1RequestException) errCause)
                                  .containsExceptionSolvableWithSmallerRange())
                          .isTrue();
                      return SafeFuture.COMPLETE;
                    }))
        .isCompleted();
  }

  @Test
  void shouldFallbackOnGetLogs_NoSmallerRangeException() {
    // all nodes failing
    when(node1.ethGetLogs(ethLogFilter))
        .thenReturn(failingProviderGetLogsWithError(new RuntimeException("error")));
    when(node2.ethGetLogs(ethLogFilter))
        .thenReturn(failingProviderGetLogsWithError(new RuntimeException("error")));
    when(node3.ethGetLogs(ethLogFilter))
        .thenReturn(failingProviderGetLogsWithError(new RuntimeException("error")));

    assertThat(
            fallbackAwareEth1Provider
                .ethGetLogs(ethLogFilter)
                .thenRun(() -> fail("should fail!"))
                .exceptionallyCompose(
                    err -> {
                      final Throwable errCause = err.getCause();
                      assertThat(errCause).isInstanceOf(Eth1RequestException.class);
                      assertThat(
                              ((Eth1RequestException) errCause)
                                  .containsExceptionSolvableWithSmallerRange())
                          .isFalse();
                      return SafeFuture.COMPLETE;
                    }))
        .isCompleted();
  }

  @Test
  void shouldFallbackOnGetLatestEth1Block() throws ExecutionException, InterruptedException {
    // node 1 ready
    when(node1.getLatestEth1Block()).thenReturn(readyProviderEthBlock());
    fallbackAwareEth1Provider.getLatestEth1Block();

    // node 1 failing and node 2 ready
    when(node1.getLatestEth1Block()).thenReturn(failingProviderEthBlock());
    when(node2.getLatestEth1Block()).thenReturn(readyProviderEthBlock());
    final SafeFuture<EthBlock.Block> latestEth1Block =
        fallbackAwareEth1Provider.getLatestEth1Block();
    assertThat(latestEth1Block.get()).isNotNull();

    // node 1 failing and node 2 are failing
    when(node1.getLatestEth1Block()).thenReturn(failingProviderEthBlock());
    when(node2.getLatestEth1Block()).thenReturn(failingProviderEthBlock());
    assertThat(fallbackAwareEth1Provider.getLatestEth1Block()).isCompletedExceptionally();

    verify(node1, times(3)).getLatestEth1Block();
    verify(node2, times(2)).getLatestEth1Block();
  }

  @Test
  void shouldFallbackOnGetGuaranteedEth1Block_byBlockNumber()
      throws ExecutionException, InterruptedException {
    // node 1,2 and 3 are all failing
    when(node1.getEth1Block(UInt64.ZERO)).thenReturn(failingProviderOptionalEthBlock());
    when(node2.getEth1Block(UInt64.ZERO)).thenReturn(failingProviderOptionalEthBlock());
    when(node3.getEth1Block(UInt64.ZERO)).thenReturn(failingProviderOptionalEthBlock());
    final SafeFuture<EthBlock.Block> blockByNumber =
        fallbackAwareEth1Provider.getGuaranteedEth1Block(UInt64.ZERO);

    // we expect a delayed action after all nodes has been contacted
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    verify(node1, atLeastOnce()).getEth1Block(UInt64.ZERO);
    verify(node2, atLeastOnce()).getEth1Block(UInt64.ZERO);
    verify(node3, atLeastOnce()).getEth1Block(UInt64.ZERO);

    // now we set node2 to be responsive and execute queued actions
    when(node2.getEth1Block(UInt64.ZERO)).thenReturn(readyProviderOptionalEthBlock());
    asyncRunner.executeQueuedActions();

    // we should have now a block
    assertThat(blockByNumber.get()).isNotNull();
  }

  @Test
  void shouldFallbackOnGetGuaranteedEth1Block_byHash()
      throws ExecutionException, InterruptedException {
    // node 1,2 and 3 are all failing
    when(node1.getEth1Block("")).thenReturn(failingProviderOptionalEthBlock());
    when(node2.getEth1Block("")).thenReturn(failingProviderOptionalEthBlock());
    when(node3.getEth1Block("")).thenReturn(failingProviderOptionalEthBlock());
    final SafeFuture<EthBlock.Block> blockByHash =
        fallbackAwareEth1Provider.getGuaranteedEth1Block("");

    // we expect a delayed action after all nodes has been contacted
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    verify(node1, atLeastOnce()).getEth1Block("");
    verify(node2, atLeastOnce()).getEth1Block("");
    verify(node3, atLeastOnce()).getEth1Block("");

    // now we set node2 to be responsive and execute queued actions
    when(node2.getEth1Block("")).thenReturn(readyProviderOptionalEthBlock());
    asyncRunner.executeQueuedActions();

    // we should have now a block
    assertThat(blockByHash.get()).isNotNull();
  }

  @Test
  void shouldFallbackOnGetEth1BlockWithRetry_byBlockNumber()
      throws ExecutionException, InterruptedException {
    // node 1,2 and 3 are all failing
    when(node1.getEth1Block(UInt64.ZERO)).thenReturn(failingProviderOptionalEthBlock());
    when(node2.getEth1Block(UInt64.ZERO)).thenReturn(failingProviderOptionalEthBlock());
    when(node3.getEth1Block(UInt64.ZERO)).thenReturn(failingProviderOptionalEthBlock());
    final SafeFuture<Optional<EthBlock.Block>> blockByNumber =
        fallbackAwareEth1Provider.getEth1BlockWithRetry(UInt64.ZERO, Duration.ofSeconds(5), 2);

    // we expect a delayed action after all nodes has been contacted
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    verify(node1, atLeastOnce()).getEth1Block(UInt64.ZERO);
    verify(node2, atLeastOnce()).getEth1Block(UInt64.ZERO);
    verify(node3, atLeastOnce()).getEth1Block(UInt64.ZERO);

    // now we set node2 to be responsive and execute queued actions
    when(node2.getEth1Block(UInt64.ZERO)).thenReturn(readyProviderOptionalEthBlock());
    asyncRunner.executeQueuedActions();

    // we should have now a block
    assertThat(blockByNumber.get()).isNotNull();

    // retries exhaustion
    when(node2.getEth1Block(UInt64.ZERO)).thenReturn(failingProviderOptionalEthBlock());
    final SafeFuture<Optional<EthBlock.Block>> blockByNumberFail =
        fallbackAwareEth1Provider.getEth1BlockWithRetry(UInt64.ZERO, Duration.ofSeconds(5), 1);
    asyncRunner.executeQueuedActions();
    assertThat(blockByNumberFail).isCompletedExceptionally();
  }

  @Test
  void shouldFallbackOnGetEth1BlockWithRetry_byHash()
      throws ExecutionException, InterruptedException {
    // node 1,2 and 3 are all failing
    when(node1.getEth1Block("")).thenReturn(failingProviderOptionalEthBlock());
    when(node2.getEth1Block("")).thenReturn(failingProviderOptionalEthBlock());
    when(node3.getEth1Block("")).thenReturn(failingProviderOptionalEthBlock());
    final SafeFuture<Optional<EthBlock.Block>> blockByHash =
        fallbackAwareEth1Provider.getEth1BlockWithRetry("", Duration.ofSeconds(5), 2);

    // we expect a delayed action after all nodes has been contacted
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    verify(node1, atLeastOnce()).getEth1Block("");
    verify(node2, atLeastOnce()).getEth1Block("");
    verify(node3, atLeastOnce()).getEth1Block("");

    // now we set node2 to be responsive and execute queued actions
    when(node2.getEth1Block("")).thenReturn(readyProviderOptionalEthBlock());
    asyncRunner.executeQueuedActions();

    // we should have now a block
    assertThat(blockByHash.get()).isNotNull();

    // retries exhaustion
    when(node2.getEth1Block("")).thenReturn(failingProviderOptionalEthBlock());
    final SafeFuture<Optional<EthBlock.Block>> blockByHashFail =
        fallbackAwareEth1Provider.getEth1BlockWithRetry("", Duration.ofSeconds(5), 1);
    asyncRunner.executeQueuedActions();
    assertThat(blockByHashFail).isCompletedExceptionally();
  }

  @Test
  void providerSelectorBehaviourOnGetGuaranteedLatestEth1Block() {
    when(node1.isValid()).thenReturn(false);
    when(node2.isValid()).thenReturn(true);
    when(node3.isValid()).thenReturn(true);

    when(node1.getLatestEth1Block()).thenReturn(readyProviderEthBlock());
    when(node2.getLatestEth1Block()).thenReturn(readyProviderEthBlock());
    when(node3.getLatestEth1Block()).thenReturn(readyProviderEthBlock());

    final SafeFuture<EthBlock.Block> latestBlock =
        fallbackAwareEth1Provider.getGuaranteedLatestEth1Block();

    // we should have a block
    assertThat(latestBlock).isNotNull();

    verify(node1, never()).getLatestEth1Block();
    verify(node2, times(1)).getLatestEth1Block();
    verify(node3, never()).getLatestEth1Block();

    // node1 is now available again
    when(node1.isValid()).thenReturn(true);

    final SafeFuture<EthBlock.Block> latestBlock2 =
        fallbackAwareEth1Provider.getGuaranteedLatestEth1Block();

    // we should have a block
    assertThat(latestBlock2).isNotNull();

    verify(node1, times(1)).getLatestEth1Block();
    verify(node2, times(1)).getLatestEth1Block();
    verify(node3, never()).getLatestEth1Block();

    asyncRunner.executeQueuedActions();
  }

  @Test
  void shouldWaitUntilProviderSelectorHasBeenNotified()
      throws ExecutionException, InterruptedException {
    asyncRunner = new StubAsyncRunner();
    providerSelector = new Eth1ProviderSelector(providers);
    fallbackAwareEth1Provider = new FallbackAwareEth1Provider(providerSelector, asyncRunner);

    when(node1.isValid()).thenReturn(true);
    when(node1.ethSyncing()).thenReturn(SafeFuture.completedFuture(true));

    final SafeFuture<Boolean> ethSyncing = fallbackAwareEth1Provider.ethSyncing();

    providerSelector.notifyValidationCompletion();

    assertThat(ethSyncing.get()).isTrue();
  }

  private static SafeFuture<List<EthLog.LogResult<?>>> readyProviderGetLogs() {
    final SafeFuture<List<EthLog.LogResult<?>>> logListSafeFuture = new SafeFuture<>();
    logListSafeFuture.complete(List.of());
    return logListSafeFuture;
  }

  private static SafeFuture<List<EthLog.LogResult<?>>> failingProviderGetLogsWithError(
      Throwable error) {
    final SafeFuture<List<EthLog.LogResult<?>>> logListSafeFuture = new SafeFuture<>();
    logListSafeFuture.completeExceptionally(error);
    return logListSafeFuture;
  }

  private static SafeFuture<EthBlock.Block> readyProviderEthBlock() {
    final SafeFuture<EthBlock.Block> blockSafeFuture = new SafeFuture<>();
    blockSafeFuture.complete(mock(EthBlock.Block.class));
    return blockSafeFuture;
  }

  private static SafeFuture<EthBlock.Block> failingProviderEthBlock() {
    final SafeFuture<EthBlock.Block> blockSafeFuture = new SafeFuture<>();
    blockSafeFuture.completeExceptionally(new RuntimeException("cannot get block"));
    return blockSafeFuture;
  }

  private static SafeFuture<Optional<EthBlock.Block>> readyProviderOptionalEthBlock() {
    final SafeFuture<Optional<EthBlock.Block>> blockSafeFuture = new SafeFuture<>();
    blockSafeFuture.complete(Optional.of(mock(EthBlock.Block.class)));
    return blockSafeFuture;
  }

  private static SafeFuture<Optional<EthBlock.Block>> failingProviderOptionalEthBlock() {
    final SafeFuture<Optional<EthBlock.Block>> blockSafeFuture = new SafeFuture<>();
    blockSafeFuture.completeExceptionally(new RuntimeException("cannot get block"));
    return blockSafeFuture;
  }
}
