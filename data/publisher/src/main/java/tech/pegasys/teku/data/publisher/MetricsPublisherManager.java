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

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.MetricsEndpoint;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.service.serviceutils.Service;

public class MetricsPublisherManager extends Service {

  private static final Logger LOG = LogManager.getLogger();

  private final long intervalBetweenPublications;
  private final AsyncRunnerFactory asyncRunnerFactory;
  private final TimeProvider timeProvider;
  private final MetricsEndpoint metricsEndpoint;
  private final MetricsDataFactory dataFactory;
  private final JsonProvider jsonProvider = new JsonProvider();

  private MetricsPublisher publisher;
  private volatile Cancellable publisherTask;

  public MetricsPublisherManager(
      AsyncRunnerFactory asyncRunnerFactory,
      final TimeProvider timeProvider,
      final MetricsEndpoint metricsEndpoint) {
    this.asyncRunnerFactory = asyncRunnerFactory;
    this.timeProvider = timeProvider;
    this.metricsEndpoint = metricsEndpoint;
    this.dataFactory = new MetricsDataFactory(metricsEndpoint.getMetricsSystem());
    this.publisher = new MetricsPublisher(new OkHttpClient());
    this.intervalBetweenPublications = metricsEndpoint.getMetricConfig().getPublicationInterval();
  }

  void setMetricsPublisher(final MetricsPublisher metricsPublisher) {
    this.publisher = metricsPublisher;
  }

  @Override
  public SafeFuture<?> start() {
    SafeFuture<?> safeFuture = SafeFuture.COMPLETE;
    if (metricsEndpoint.getMetricConfig().getMetricsEndpoint() != null) {
      safeFuture = this.doStart();
    }
    return safeFuture;
  }

  int publishMetrics() {
    String endpointAddress = metricsEndpoint.getMetricConfig().getMetricsEndpoint();
    List<BaseMetricData> clientData = dataFactory.getMetricData(this.timeProvider);
    try {
      return publisher.publishMetrics(endpointAddress, jsonProvider.objectToJSON(clientData));
    } catch (JsonProcessingException e) {
      LOG.error("Error processing JSON object ", e);
    } catch (IOException e) {
      LOG.error("Error performing external connection ", e);
    }
    return 0;
  }

  @Override
  protected SafeFuture<?> doStart() {
    AsyncRunner asyncRunner = asyncRunnerFactory.create("MetricPublisher", 1);
    publisherTask =
        asyncRunner.runWithFixedDelay(
            this::publishMetrics,
            Duration.ofSeconds(intervalBetweenPublications),
            (err) ->
                LOG.error(
                    "Encountered error while attempting to publish metrics to remote service",
                    err));
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    final Cancellable publisherTask = this.publisherTask;
    if (publisherTask != null) {
      publisherTask.cancel();
    }
    return SafeFuture.COMPLETE;
  }
}
