/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.relaypublisher;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;

import java.util.Collections;
import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;

class MultiPublishingBeaconNodeApiTest {
  private final BeaconNodeApi delegate = mock(BeaconNodeApi.class);
  private final ValidatorApiChannel validator = mock(ValidatorApiChannel.class);

  @BeforeEach
  void setup() {
    when(delegate.getValidatorApi()).thenReturn(validator);
  }

  @Test
  void shouldOnlyCallDelegate_subscribeToEvents() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, validator);

    ignoreFuture(api.subscribeToEvents());
    verify(delegate, times(1)).subscribeToEvents();
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldOnlyCallDelegate_unsubscribeFromEvents() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, validator);

    ignoreFuture(api.unsubscribeFromEvents());
    verify(delegate, times(1)).unsubscribeFromEvents();
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldGetMultiPublishObject_getValidatorApi() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, validator);

    assertThat(api.getValidatorApi()).isEqualTo(validator);
    verifyNoInteractions(delegate);
  }

  @Test
  void shouldCreateWithMultiPublishingValidatorClient() {
    final ServiceConfig services = mock(ServiceConfig.class);
    final EventChannels eventChannels = mock(EventChannels.class);
    final MetricsSystem metricsSystem = mock(MetricsSystem.class);
    when(services.getEventChannels()).thenReturn(eventChannels);
    when(services.getMetricsSystem()).thenReturn(metricsSystem);
    when(eventChannels.getPublisher(any(), any())).thenReturn(validator);
    BeaconNodeApi api =
        MultiPublishingBeaconNodeApi.create(
            services,
            new StubAsyncRunner(),
            Optional.empty(),
            TestSpecFactory.createMinimalAltair(),
            false,
            false,
            Collections.emptyList());

    assertThat(api.getValidatorApi()).isInstanceOf(MultiPublishingValidatorApiChannel.class);
  }
}
