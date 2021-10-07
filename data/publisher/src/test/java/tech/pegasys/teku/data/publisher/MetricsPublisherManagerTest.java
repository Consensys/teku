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

package tech.pegasys.teku.data.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.metrics.MetricsConfig;
import tech.pegasys.teku.infrastructure.metrics.MetricsEndpoint;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;

class MetricsPublisherManagerTest {

  private MetricsEndpoint metricsEndpoint;
  private MetricsConfig metricsConfig;
  private MetricsPublisher metricsPublisher;
  PrometheusMetricsSystem prometheusMetricsSystem;

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);
  private final StubAsyncRunnerFactory asyncRunnerFactory = new StubAsyncRunnerFactory();

  @BeforeEach
  void init_mocks() throws IOException {
    this.metricsEndpoint = mock(MetricsEndpoint.class);
    this.metricsConfig = mock(MetricsConfig.class);
    this.metricsPublisher = mock(MetricsPublisher.class);
    this.prometheusMetricsSystem = mock(PrometheusMetricsSystem.class);
    when(metricsConfig.getPublicationInterval()).thenReturn(1);
    when(metricsEndpoint.getMetricsSystem()).thenReturn(prometheusMetricsSystem);
    when(metricsEndpoint.getMetricConfig()).thenReturn(metricsConfig);
    when(metricsConfig.getMetricsEndpoint()).thenReturn("/");
    when(metricsPublisher.publishMetrics(anyString(), anyString())).thenReturn(200);
  }

  @Test
  public void shouldRunPublisherEveryXSeconds() throws InterruptedException, IOException {
    MetricsPublisherManager publisherManager =
        new MetricsPublisherManager(asyncRunnerFactory, timeProvider, metricsEndpoint);
    publisherManager.setMetricsPublisher(metricsPublisher);
    verify(metricsPublisher, times(0)).publishMetrics(anyString(), anyString());
    SafeFuture<?> safeFuture = publisherManager.doStart();
    assertThat(asyncRunnerFactory.getStubAsyncRunners().size()).isEqualTo(1);
    asyncRunnerFactory.getStubAsyncRunners().get(0).executeQueuedActions();
    verify(metricsPublisher, times(1)).publishMetrics(anyString(), anyString());
    asyncRunnerFactory.getStubAsyncRunners().get(0).executeQueuedActions();
    verify(metricsPublisher, times(2)).publishMetrics(anyString(), anyString());
    Assertions.assertThat(safeFuture).isEqualTo(SafeFuture.COMPLETE);
  }

  @Test
  public void shouldReturnHTTPStatusOk() throws IOException {
    MetricsPublisherManager publisherManager =
        new MetricsPublisherManager(asyncRunnerFactory, timeProvider, metricsEndpoint);
    publisherManager.setMetricsPublisher(metricsPublisher);
    Assertions.assertThat(publisherManager.publishMetrics()).isEqualTo(200);
  }

  @Test
  public void shouldStopGracefully() throws IOException {
    MetricsPublisherManager publisherManager =
        new MetricsPublisherManager(asyncRunnerFactory, timeProvider, metricsEndpoint);
    publisherManager.setMetricsPublisher(metricsPublisher);
    SafeFuture<?> safeFuture = publisherManager.doStart();
    Assertions.assertThat(safeFuture).isEqualTo(SafeFuture.COMPLETE);
    safeFuture = publisherManager.doStop();
    Assertions.assertThat(safeFuture).isEqualTo(SafeFuture.COMPLETE);
  }
}
