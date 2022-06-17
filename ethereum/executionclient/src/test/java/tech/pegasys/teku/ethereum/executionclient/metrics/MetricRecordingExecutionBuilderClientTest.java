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

package tech.pegasys.teku.ethereum.executionclient.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.ethereum.executionclient.metrics.MetricRecordingExecutionBuilderClient.BUILDER_REQUESTS_COUNTER_NAME;

import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.ExecutionBuilderClient;
import tech.pegasys.teku.ethereum.executionclient.metrics.MetricRecordingExecutionBuilderClient.RequestOutcome;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class MetricRecordingExecutionBuilderClientTest {

  private static final long RESPONSE_DELAY = 1300;
  private static final String EXPECTED_TIME_INTERVAL = "[1000,2000)";

  private final ExecutionBuilderClient delegate = mock(ExecutionBuilderClient.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private StubTimeProvider stubTimeProvider;
  private MetricRecordingExecutionBuilderClient executionBuilderClient;

  @BeforeEach
  void setup() {
    stubTimeProvider = StubTimeProvider.withTimeInMillis(0);
    executionBuilderClient =
        new MetricRecordingExecutionBuilderClient(delegate, stubTimeProvider, metricsSystem);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestArguments")
  public void shouldCountSuccessfulRequest(
      final Function<ExecutionBuilderClient, SafeFuture<Object>> requestRunner,
      final String method,
      final Object value) {
    setupResponse(requestRunner, SafeFuture.completedFuture(value));

    final SafeFuture<Object> result = requestRunner.apply(executionBuilderClient);

    assertThat(result).isCompletedWithValue(value);

    assertThat(getCounterValue(method, RequestOutcome.SUCCESS)).isOne();
    assertThat(getCounterValue(method, RequestOutcome.ERROR)).isZero();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestArguments")
  public void shouldCountRequestWithFailedFutureResponse(
      final Function<ExecutionBuilderClient, SafeFuture<Object>> requestRunner,
      final String method) {
    final RuntimeException exception = new RuntimeException("Nope");
    setupResponse(requestRunner, SafeFuture.failedFuture(exception));

    final SafeFuture<Object> result = requestRunner.apply(executionBuilderClient);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCause(exception);

    assertThat(getCounterValue(method, RequestOutcome.ERROR)).isOne();
    assertThat(getCounterValue(method, RequestOutcome.SUCCESS)).isZero();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestWithResponseFailureArguments")
  public void shouldCountRequestWithResponseFailure(
      final Function<ExecutionBuilderClient, SafeFuture<Object>> requestRunner,
      final String method,
      final Object value) {
    setupResponse(requestRunner, SafeFuture.completedFuture(value));

    final SafeFuture<Object> result = requestRunner.apply(executionBuilderClient);

    assertThat(result).isCompletedWithValue(value);

    assertThat(getCounterValue(method, RequestOutcome.ERROR)).isOne();
    assertThat(getCounterValue(method, RequestOutcome.SUCCESS)).isZero();
  }

  private void setupResponse(
      final Function<ExecutionBuilderClient, SafeFuture<Object>> requestRunner,
      final SafeFuture<Object> response) {
    when(requestRunner.apply(delegate))
        .thenAnswer(
            __ -> {
              stubTimeProvider.advanceTimeByMillis(RESPONSE_DELAY);
              return response;
            });
  }

  private long getCounterValue(final String method, final RequestOutcome requestOutcome) {
    return metricsSystem
        .getCounter(TekuMetricCategory.BEACON, BUILDER_REQUESTS_COUNTER_NAME)
        .getValue(method, requestOutcome.name(), EXPECTED_TIME_INTERVAL);
  }

  public static Stream<Arguments> getRequestWithResponseFailureArguments() {
    return getRequestArguments()
        .peek(arguments -> arguments.get()[2] = Response.withErrorMessage("oopsy"));
  }

  public static Stream<Arguments> getRequestArguments() {
    final DataStructureUtil dataStructureUtil =
        new DataStructureUtil(TestSpecFactory.createMinimalBellatrix());
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(3);
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    final Bytes32 parentHash = dataStructureUtil.randomBytes32();
    final SignedBuilderBid builderBid = dataStructureUtil.randomSignedBuilderBid();
    final SignedBeaconBlock beaconBlock = dataStructureUtil.randomSignedBlindedBeaconBlock();
    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();

    return Stream.of(
        getArguments(
            "status",
            ExecutionBuilderClient::status,
            MetricRecordingExecutionBuilderClient.STATUS_METHOD,
            Response.withNullPayload()),
        getArguments(
            "registerValidators",
            client -> client.registerValidators(slot, validatorRegistrations),
            MetricRecordingExecutionBuilderClient.REGISTER_VALIDATORS_METHOD,
            Response.withNullPayload()),
        getArguments(
            "getHeader",
            client -> client.getHeader(slot, publicKey, parentHash),
            MetricRecordingExecutionBuilderClient.GET_HEADER_METHOD,
            new Response<>(builderBid)),
        getArguments(
            "getPayload",
            client -> client.getPayload(beaconBlock),
            MetricRecordingExecutionBuilderClient.GET_PAYLOAD_METHOD,
            new Response<>(executionPayload)));
  }

  private static <T> Arguments getArguments(
      final String name,
      final Function<ExecutionBuilderClient, SafeFuture<T>> method,
      final String counterName,
      final T value) {
    return Arguments.of(Named.of(name, method), counterName, value);
  }
}
