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
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.metrics.MetricsEndpoint;
import tech.pegasys.teku.provider.JsonProvider;

public class MetricsPublisherManager {

  private static final long intervalBetweenPublications = 60;

  private final AsyncRunnerFactory asyncRunnerFactory;
  private final MetricsEndpoint metricsConfig;
  private static final Logger LOG = LogManager.getLogger();
  private final JsonProvider jsonProvider = new JsonProvider();
  private final MetricsDataFactory dataFactory;

  public MetricsPublisherManager(
      AsyncRunnerFactory asyncRunnerFactory, final MetricsEndpoint metricsConfig) {
    this.asyncRunnerFactory = asyncRunnerFactory;
    this.metricsConfig = metricsConfig;
    this.dataFactory = new MetricsDataFactory(metricsConfig.getMetricsSystem());
  }

  public void runPublisher() {
    if (metricsConfig.getMetricConfig().getMetricsEndpoint() != null) {
      AsyncRunner asyncRunner = asyncRunnerFactory.create("MetricPublisher", 1);
      asyncRunner.runWithFixedDelay(
          this::publishMetrics,
          Duration.ofSeconds(intervalBetweenPublications),
          (err) ->
              LOG.error(
                  "Encountered error while attempting to publish metrics to remote service", err));
    }
  }

  private void publishMetrics() {
    String endpointAddress = metricsConfig.getMetricConfig().getMetricsEndpoint();
    BaseMetricData clientData = dataFactory.getMetricData(MetricsDataClients.BEACONCHAIN);
    try {
      MetricsPublisher publisher =
          new MetricsPublisher(
              endpointAddress, jsonProvider.objectToJSON(clientData), new OkHttpClient());
      publisher.publishMetrics();
    } catch (JsonProcessingException e) {
      LOG.error("Error processing JSON object ", e);
    } catch (IOException e) {
      LOG.error("Error performing external connection ", e);
    }
  }
}
