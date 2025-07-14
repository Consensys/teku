/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.remote;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.validator.remote.RemoteBeaconNodeApi.MAX_API_EXECUTOR_QUEUE_SIZE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.validator.api.ValidatorConfig;

class RemoteBeaconNodeApiTest {

  private final ServiceConfig serviceConfig = mock(ServiceConfig.class);
  private final EventChannels eventChannels = mock(EventChannels.class);
  private final ValidatorConfig validatorConfig = mock(ValidatorConfig.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final Spec spec = TestSpecFactory.createMinimalAltair();

  @BeforeEach
  void setUp() {
    when(serviceConfig.getEventChannels()).thenReturn(eventChannels);
    when(serviceConfig.getMetricsSystem()).thenReturn(metricsSystem);
  }

  @Test
  void producesExceptionWhenInvalidUrlPassed() {
    assertThatThrownBy(
            () ->
                RemoteBeaconNodeApi.create(
                    serviceConfig, validatorConfig, spec, List.of(new URI("notvalid"))))
        .hasMessageContaining("Failed to convert remote api endpoint");
  }

  @Test
  void shouldConfigureBeaconApiPools_primaryOnly() throws URISyntaxException {
    RemoteBeaconNodeApi.create(
        serviceConfig, validatorConfig, spec, List.of(new URI("https://localhost")));

    verify(serviceConfig).createAsyncRunner("validatorBeaconAPI", 5, MAX_API_EXECUTOR_QUEUE_SIZE);

    verify(serviceConfig, times(1)).createAsyncRunner(anyString(), anyInt(), anyInt());
  }

  @Test
  void shouldConfigureBeaconApiPools_primaryAndSecondary() throws URISyntaxException {
    RemoteBeaconNodeApi.create(
        serviceConfig,
        validatorConfig,
        spec,
        List.of(new URI("https://localhost"), new URI("https://secondary")));

    verify(serviceConfig).createAsyncRunner("validatorBeaconAPI", 5, MAX_API_EXECUTOR_QUEUE_SIZE);
    verify(serviceConfig)
        .createAsyncRunner("validatorBeaconAPIReadiness", 4, MAX_API_EXECUTOR_QUEUE_SIZE);

    verify(serviceConfig, times(2)).createAsyncRunner(anyString(), anyInt(), anyInt());
  }

  @Test
  void shouldConfigureBeaconApiPools_primaryAndSecondaryWithFailoverPublish()
      throws URISyntaxException {
    when(validatorConfig.isFailoversPublishSignedDutiesEnabled()).thenReturn(true);

    RemoteBeaconNodeApi.create(
        serviceConfig,
        validatorConfig,
        spec,
        List.of(new URI("https://localhost"), new URI("https://secondary")));

    verify(serviceConfig).createAsyncRunner("validatorBeaconAPI", 8, MAX_API_EXECUTOR_QUEUE_SIZE);
    verify(serviceConfig)
        .createAsyncRunner("validatorBeaconAPIReadiness", 4, MAX_API_EXECUTOR_QUEUE_SIZE);

    verify(serviceConfig, times(2)).createAsyncRunner(anyString(), anyInt(), anyInt());
  }

  @Test
  void shouldConfigureBeaconApiPools_configOverride() throws URISyntaxException {
    when(validatorConfig.getBeaconApiExecutorThreads()).thenReturn(OptionalInt.of(2));
    when(validatorConfig.getBeaconApiReadinessExecutorThreads()).thenReturn(OptionalInt.of(4));

    RemoteBeaconNodeApi.create(
        serviceConfig,
        validatorConfig,
        spec,
        List.of(new URI("https://localhost"), new URI("https://secondary")));

    verify(serviceConfig).createAsyncRunner("validatorBeaconAPI", 2, MAX_API_EXECUTOR_QUEUE_SIZE);
    verify(serviceConfig)
        .createAsyncRunner("validatorBeaconAPIReadiness", 4, MAX_API_EXECUTOR_QUEUE_SIZE);

    verify(serviceConfig, times(2)).createAsyncRunner(anyString(), anyInt(), anyInt());
  }
}
