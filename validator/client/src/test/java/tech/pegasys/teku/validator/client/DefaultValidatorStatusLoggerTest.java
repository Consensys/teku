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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.core.signatures.NoOpSigner.NO_OP_SIGNER;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class DefaultValidatorStatusLoggerTest {

  public static final String VALIDATOR_COUNTS_METRIC = "local_validator_counts";
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final BLSPublicKey validatorKey = BLSTestUtil.randomPublicKey(0);
  private final Collection<BLSPublicKey> validatorKeys = Set.of(validatorKey);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final DefaultValidatorStatusLogger logger =
      new DefaultValidatorStatusLogger(
          metricsSystem,
          new OwnedValidators(
              Map.of(validatorKey, new Validator(validatorKey, NO_OP_SIGNER, Optional::empty))),
          validatorApiChannel,
          asyncRunner);

  @Test
  @SuppressWarnings("unchecked")
  void shouldRetryPrintingInitialValidatorStatuses() {
    when(validatorApiChannel.getValidatorStatuses(validatorKeys))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Collections.EMPTY_MAP)));

    assertThat(logger.printInitialValidatorStatuses()).isNotCompleted();
    verify(validatorApiChannel).getValidatorStatuses(validatorKeys);

    asyncRunner.executeQueuedActions();

    verify(validatorApiChannel, times(2)).getValidatorStatuses(validatorKeys);

    asyncRunner.executeQueuedActions();

    verify(validatorApiChannel, times(3)).getValidatorStatuses(validatorKeys);

    asyncRunner.executeUntilDone();

    verify(validatorApiChannel, times(3)).getValidatorStatuses(validatorKeys);
  }

  @Test
  void shouldSetMetricsForInitialValidatorStatuses() {
    when(validatorApiChannel.getValidatorStatuses(validatorKeys))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(Map.of(validatorKey, ValidatorStatus.pending_initialized))));

    assertThat(logger.printInitialValidatorStatuses()).isCompleted();

    final StubLabelledGauge gauge =
        metricsSystem.getLabelledGauge(TekuMetricCategory.VALIDATOR, VALIDATOR_COUNTS_METRIC);
    assertThat(gauge.getValue(ValidatorStatus.pending_initialized.name()))
        .isEqualTo(OptionalDouble.of(1));
    assertThat(gauge.getValue(ValidatorStatus.active_ongoing.name()))
        .isEqualTo(OptionalDouble.of(0));
  }

  @Test
  void shouldUpdateMetricsForValidatorStatuses() {
    when(validatorApiChannel.getValidatorStatuses(validatorKeys))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(Map.of(validatorKey, ValidatorStatus.pending_initialized))))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(Map.of(validatorKey, ValidatorStatus.active_ongoing))));

    assertThat(logger.printInitialValidatorStatuses()).isCompleted();

    final StubLabelledGauge gauge =
        metricsSystem.getLabelledGauge(TekuMetricCategory.VALIDATOR, VALIDATOR_COUNTS_METRIC);
    assertThat(gauge.getValue(ValidatorStatus.pending_initialized.name()))
        .isEqualTo(OptionalDouble.of(1));
    assertThat(gauge.getValue(ValidatorStatus.active_ongoing.name()))
        .isEqualTo(OptionalDouble.of(0));

    logger.checkValidatorStatusChanges();

    assertThat(gauge.getValue(ValidatorStatus.pending_initialized.name()))
        .isEqualTo(OptionalDouble.of(0));
    assertThat(gauge.getValue(ValidatorStatus.active_ongoing.name()))
        .isEqualTo(OptionalDouble.of(1));
  }
}
