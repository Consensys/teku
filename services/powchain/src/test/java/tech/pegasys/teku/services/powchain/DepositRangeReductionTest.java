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

package tech.pegasys.teku.services.powchain;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InterruptedIOException;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import tech.pegasys.teku.beacon.pow.DepositEventsAccessor;
import tech.pegasys.teku.beacon.pow.DepositFetcher;
import tech.pegasys.teku.beacon.pow.Eth1BlockFetcher;
import tech.pegasys.teku.beacon.pow.MonitorableEth1Provider;
import tech.pegasys.teku.beacon.pow.exception.RejectedRequestException;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;

public class DepositRangeReductionTest {

  private static final int MAX_BLOCK_RANGE = 10_000;
  private static final int REDUCED_BLOCK_RANGE = MAX_BLOCK_RANGE / 2;
  private final Eth1EventsChannel eth1EventsChannel = mock(Eth1EventsChannel.class);
  private final Eth1BlockFetcher eth1BlockFetcher = mock(Eth1BlockFetcher.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final BigInteger fromBlockNumber = BigInteger.ZERO;
  private final BigInteger toBlockNumber = BigInteger.valueOf(MAX_BLOCK_RANGE * 2 + 100);

  private final MonitorableEth1Provider underlyingEth1Provider1 =
      mock(MonitorableEth1Provider.class);
  private final MonitorableEth1Provider underlyingEth1Provider2 =
      mock(MonitorableEth1Provider.class);

  private Optional<BigInteger> provider1LastRequestSize = Optional.empty();
  private SafeFuture<List<EthLog.LogResult<?>>> provider1LastResponse;
  private Optional<BigInteger> provider2LastRequestSize = Optional.empty();
  private SafeFuture<List<EthLog.LogResult<?>>> provider2LastResponse;

  @BeforeEach
  void setUp() {
    when(underlyingEth1Provider1.validate()).thenReturn(SafeFuture.completedFuture(true));
    when(underlyingEth1Provider1.isValid()).thenReturn(true);
    when(underlyingEth1Provider1.ethGetLogs(any()))
        .thenAnswer(
            invocation -> {
              final EthFilter filter = invocation.getArgument(0);
              final BigInteger from =
                  ((DefaultBlockParameterNumber) filter.getFromBlock()).getBlockNumber();
              final BigInteger to =
                  ((DefaultBlockParameterNumber) filter.getToBlock()).getBlockNumber();
              provider1LastRequestSize = Optional.of(to.subtract(from));
              provider1LastResponse = new SafeFuture<>();
              return provider1LastResponse;
            });

    when(underlyingEth1Provider2.validate()).thenReturn(SafeFuture.completedFuture(true));
    when(underlyingEth1Provider2.isValid()).thenReturn(true);
    when(underlyingEth1Provider2.ethGetLogs(any()))
        .thenAnswer(
            invocation -> {
              final EthFilter filter = invocation.getArgument(0);
              final BigInteger from =
                  ((DefaultBlockParameterNumber) filter.getFromBlock()).getBlockNumber();
              final BigInteger to =
                  ((DefaultBlockParameterNumber) filter.getToBlock()).getBlockNumber();
              provider2LastRequestSize = Optional.of(to.subtract(from));
              provider2LastResponse = new SafeFuture<>();
              return provider2LastResponse;
            });
  }

  @ParameterizedTest
  @MethodSource("exceptionsToReduceRange")
  void singleProvider_shouldReduceBatchSize(final Throwable error) {
    final DepositFetcher depositFetcher = createWithProviders(underlyingEth1Provider1);
    final SafeFuture<Void> result =
        depositFetcher.fetchDepositsInRange(fromBlockNumber, toBlockNumber);

    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
    provider1LastResponse.completeExceptionally(error);
    assertThat(result).isNotCompleted();

    asyncRunner.executeQueuedActions();
    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(REDUCED_BLOCK_RANGE));
  }

  @Test
  void singleProvider_shouldNotReduceBatchSize() {
    final DepositFetcher depositFetcher = createWithProviders(underlyingEth1Provider1);
    final SafeFuture<Void> result =
        depositFetcher.fetchDepositsInRange(fromBlockNumber, toBlockNumber);

    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
    provider1LastResponse.completeExceptionally(new IllegalArgumentException("Whoops"));
    assertThat(result).isNotCompleted();

    asyncRunner.executeQueuedActions();
    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
  }

  @ParameterizedTest
  @MethodSource("exceptionsToReduceRange")
  void multipleProviders_shouldReduceBatchSize_secondOffline(final Throwable error) {
    when(underlyingEth1Provider2.isValid()).thenReturn(false);

    final DepositFetcher depositFetcher =
        createWithProviders(underlyingEth1Provider1, underlyingEth1Provider2);
    final SafeFuture<Void> result =
        depositFetcher.fetchDepositsInRange(fromBlockNumber, toBlockNumber);

    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
    provider1LastResponse.completeExceptionally(error);
    assertThat(result).isNotCompleted();

    asyncRunner.executeQueuedActions();
    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(REDUCED_BLOCK_RANGE));
  }

  @ParameterizedTest
  @MethodSource("exceptionsToReduceRange")
  void multipleProviders_shouldNotReduceBatchSizeWhenSecondSuccessful(final Throwable error) {
    final DepositFetcher depositFetcher =
        createWithProviders(underlyingEth1Provider1, underlyingEth1Provider2);
    final SafeFuture<Void> result =
        depositFetcher.fetchDepositsInRange(fromBlockNumber, toBlockNumber);

    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
    provider1LastResponse.completeExceptionally(error);

    assertThat(provider2LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
    provider2LastResponse.complete(emptyList());
    assertThat(result).isNotCompleted();

    asyncRunner.executeQueuedActions();
    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
  }

  @ParameterizedTest
  @MethodSource("exceptionsToReduceRange")
  void multipleProviders_shouldReduceBatchSize_secondSameError(final Throwable error) {
    final DepositFetcher depositFetcher =
        createWithProviders(underlyingEth1Provider1, underlyingEth1Provider2);
    final SafeFuture<Void> result =
        depositFetcher.fetchDepositsInRange(fromBlockNumber, toBlockNumber);

    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
    provider1LastResponse.completeExceptionally(error);

    assertThat(provider2LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
    provider2LastResponse.completeExceptionally(error);
    assertThat(result).isNotCompleted();

    asyncRunner.executeQueuedActions();
    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(REDUCED_BLOCK_RANGE));
  }

  @ParameterizedTest
  @MethodSource("exceptionsToReduceRange")
  void multipleProviders_shouldReduceBatchSize_secondNonReducingError(final Throwable error) {
    final DepositFetcher depositFetcher =
        createWithProviders(underlyingEth1Provider1, underlyingEth1Provider2);
    final SafeFuture<Void> result =
        depositFetcher.fetchDepositsInRange(fromBlockNumber, toBlockNumber);

    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
    provider1LastResponse.completeExceptionally(error);

    assertThat(provider2LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
    provider2LastResponse.completeExceptionally(new IllegalArgumentException("Whoops"));
    assertThat(result).isNotCompleted();

    asyncRunner.executeQueuedActions();
    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(REDUCED_BLOCK_RANGE));
  }

  @ParameterizedTest
  @MethodSource("exceptionsToReduceRange")
  void multipleProviders_shouldReduceBatchSize_firstNonReducingError(final Throwable error) {
    final DepositFetcher depositFetcher =
        createWithProviders(underlyingEth1Provider1, underlyingEth1Provider2);
    final SafeFuture<Void> result =
        depositFetcher.fetchDepositsInRange(fromBlockNumber, toBlockNumber);

    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
    provider1LastResponse.completeExceptionally(new IllegalArgumentException("Whoops"));

    assertThat(provider2LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
    provider2LastResponse.completeExceptionally(error);
    assertThat(result).isNotCompleted();

    asyncRunner.executeQueuedActions();
    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(REDUCED_BLOCK_RANGE));
  }

  @Test
  void multipleProviders_shouldNotReduceBatchSize() {
    final DepositFetcher depositFetcher =
        createWithProviders(underlyingEth1Provider1, underlyingEth1Provider2);
    final SafeFuture<Void> result =
        depositFetcher.fetchDepositsInRange(fromBlockNumber, toBlockNumber);

    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
    provider1LastResponse.completeExceptionally(new IllegalArgumentException("Whoops"));

    assertThat(provider2LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
    provider2LastResponse.completeExceptionally(new IllegalArgumentException("Whoops"));
    assertThat(result).isNotCompleted();

    asyncRunner.executeQueuedActions();
    assertThat(provider1LastRequestSize).contains(BigInteger.valueOf(MAX_BLOCK_RANGE));
  }

  static Stream<Arguments> exceptionsToReduceRange() {
    return Stream.of(
            new RejectedRequestException(-32005, "Nah mate"),
            new SocketTimeoutException("Too slow!"),
            new InterruptedIOException("Timed out"))
        .map(Arguments::of);
  }

  private DepositFetcher createWithProviders(final MonitorableEth1Provider... providers) {
    final Eth1Providers eth1Providers =
        Eth1Providers.create(List.of(providers), asyncRunner, timeProvider, metricsSystem);
    final DepositEventsAccessor depositEventsAccessor =
        new DepositEventsAccessor(
            eth1Providers.getEth1Provider(), "0xdddddddddddddddddddddddddddddddddddddddd");
    eth1Providers.start();
    asyncRunner.executeQueuedActions();
    return new DepositFetcher(
        eth1Providers.getEth1Provider(),
        eth1EventsChannel,
        depositEventsAccessor,
        eth1BlockFetcher,
        asyncRunner,
        MAX_BLOCK_RANGE);
  }
}
