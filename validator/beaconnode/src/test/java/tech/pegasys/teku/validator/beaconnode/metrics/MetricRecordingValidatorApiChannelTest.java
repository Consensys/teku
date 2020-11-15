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

package tech.pegasys.teku.validator.beaconnode.metrics;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.genesis.GenesisData;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.metrics.BeaconChainRequestCounter.RequestOutcome;

class MetricRecordingValidatorApiChannelTest {

  private final ValidatorApiChannel delegate = mock(ValidatorApiChannel.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final MetricRecordingValidatorApiChannel apiChannel =
      new MetricRecordingValidatorApiChannel(metricsSystem, delegate);

  @ParameterizedTest(name = "{displayName} - {0}")
  @MethodSource("getDataRequestArguments")
  public void shouldRecordSuccessfulRequestForData(
      final String name,
      final Function<ValidatorApiChannel, SafeFuture<Optional<Object>>> method,
      final String counterName,
      final Object value) {
    final Optional<Object> response = Optional.of(value);
    when(method.apply(delegate)).thenReturn(SafeFuture.completedFuture(response));

    final SafeFuture<Optional<Object>> result = method.apply(apiChannel);

    assertThat(result).isCompletedWithValue(response);

    assertThat(getCounterValue(counterName, RequestOutcome.SUCCESS)).isEqualTo(1);
    assertThat(getCounterValue(counterName, RequestOutcome.ERROR)).isZero();
    assertThat(getCounterValue(counterName, RequestOutcome.DATA_UNAVAILABLE)).isZero();
  }

  @ParameterizedTest(name = "{displayName} - {0}")
  @MethodSource("getDataRequestArguments")
  public void shouldRecordFailedRequestForData(
      final String name,
      final Function<ValidatorApiChannel, SafeFuture<Optional<Object>>> method,
      final String counterName,
      final Object value) {
    final RuntimeException exception = new RuntimeException("Nope");
    when(method.apply(delegate)).thenReturn(SafeFuture.failedFuture(exception));

    final SafeFuture<Optional<Object>> result = method.apply(apiChannel);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCause(exception);

    assertThat(getCounterValue(counterName, RequestOutcome.ERROR)).isEqualTo(1);
    assertThat(getCounterValue(counterName, RequestOutcome.SUCCESS)).isZero();
    assertThat(getCounterValue(counterName, RequestOutcome.DATA_UNAVAILABLE)).isZero();
  }

  @ParameterizedTest(name = "{displayName} - {0}")
  @MethodSource("getDataRequestArguments")
  public void shouldRecordRequestForDataWhenDataUnavailable(
      final String name,
      final Function<ValidatorApiChannel, SafeFuture<Optional<Object>>> method,
      final String counterName,
      final Object value) {
    when(method.apply(delegate)).thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<Optional<Object>> result = method.apply(apiChannel);
    assertThat(result).isCompletedWithValue(Optional.empty());

    assertThat(getCounterValue(counterName, RequestOutcome.DATA_UNAVAILABLE)).isEqualTo(1);
    assertThat(getCounterValue(counterName, RequestOutcome.SUCCESS)).isZero();
    assertThat(getCounterValue(counterName, RequestOutcome.ERROR)).isZero();
  }

  @ParameterizedTest(name = "{displayName} - {0}")
  @MethodSource("getNoResponseCallArguments")
  public void shouldRecordCallsWithNoResponse(
      final String name, final Consumer<ValidatorApiChannel> method, final String counterName) {
    method.accept(apiChannel);

    Assertions.assertThat(
            metricsSystem.getCounter(TekuMetricCategory.VALIDATOR, counterName).getValue())
        .isEqualTo(1);
  }

  public static Stream<Arguments> getNoResponseCallArguments() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    return Stream.of(
        noResponseTest(
            "subscribeToBeaconCommitteeForAggregation",
            channel -> channel.subscribeToBeaconCommittee(emptyList()),
            MetricRecordingValidatorApiChannel.AGGREGATION_SUBSCRIPTION_COUNTER_NAME),
        noResponseTest(
            "subscribeToPersistentSubnets",
            channel -> channel.subscribeToPersistentSubnets(emptySet()),
            MetricRecordingValidatorApiChannel.PERSISTENT_SUBSCRIPTION_COUNTER_NAME),
        noResponseTest(
            "sendSignedAttestation",
            channel -> channel.sendSignedAttestation(dataStructureUtil.randomAttestation()),
            MetricRecordingValidatorApiChannel.PUBLISHED_ATTESTATION_COUNTER_NAME),
        noResponseTest(
            "sendAggregateAndProof",
            channel ->
                channel.sendAggregateAndProof(dataStructureUtil.randomSignedAggregateAndProof()),
            MetricRecordingValidatorApiChannel.PUBLISHED_AGGREGATE_COUNTER_NAME));
  }

  private static Arguments noResponseTest(
      final String name, final Consumer<ValidatorApiChannel> method, final String counterName) {
    return Arguments.of(name, method, counterName);
  }

  public static Stream<Arguments> getDataRequestArguments() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    return Stream.of(
        requestDataTest(
            "getForkInfo",
            ValidatorApiChannel::getFork,
            MetricRecordingValidatorApiChannel.FORK_REQUESTS_COUNTER_NAME,
            dataStructureUtil.randomFork()),
        requestDataTest(
            "getGenesisData",
            ValidatorApiChannel::getGenesisData,
            MetricRecordingValidatorApiChannel.GENESIS_TIME_REQUESTS_COUNTER_NAME,
            new GenesisData(dataStructureUtil.randomUInt64(), Bytes32.random())),
        requestDataTest(
            "createUnsignedBlock",
            channel -> channel.createUnsignedBlock(slot, signature, Optional.empty()),
            MetricRecordingValidatorApiChannel.UNSIGNED_BLOCK_REQUESTS_COUNTER_NAME,
            dataStructureUtil.randomBeaconBlock(slot)),
        requestDataTest(
            "createUnsignedAttestation",
            channel -> channel.createUnsignedAttestation(slot, 4),
            MetricRecordingValidatorApiChannel.UNSIGNED_ATTESTATION_REQUEST_COUNTER_NAME,
            dataStructureUtil.randomAttestation()),
        requestDataTest(
            "createAggregate",
            channel ->
                channel.createAggregate(attestationData.getSlot(), attestationData.hashTreeRoot()),
            MetricRecordingValidatorApiChannel.AGGREGATE_REQUESTS_COUNTER_NAME,
            dataStructureUtil.randomAttestation()));
  }

  private static <T> Arguments requestDataTest(
      final String name,
      final Function<ValidatorApiChannel, SafeFuture<Optional<T>>> method,
      final String counterName,
      final T presentValue) {
    return Arguments.of(name, method, counterName, presentValue);
  }

  private long getCounterValue(final String counterName, final RequestOutcome outcome) {
    return metricsSystem
        .getCounter(TekuMetricCategory.VALIDATOR, counterName)
        .getValue(outcome.name());
  }
}
