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

package tech.pegasys.teku.cli.options;

import com.google.common.base.Strings;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.metrics.MetricsConfig;

public class MetricsOptions {

  @Option(
      names = {"--metrics-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables metrics collection via Prometheus",
      fallbackValue = "true",
      arity = "0..1")
  private boolean metricsEnabled = false;

  @Option(
      names = {"--metrics-port"},
      paramLabel = "<INTEGER>",
      description = "Metrics port to expose metrics for Prometheus",
      arity = "1")
  private int metricsPort = MetricsConfig.DEFAULT_METRICS_PORT;

  @Option(
      names = {"--metrics-interface"},
      paramLabel = "<NETWORK>",
      description = "Metrics network interface to expose metrics for Prometheus",
      arity = "1")
  private String metricsInterface = MetricsConfig.DEFAULT_METRICS_INTERFACE;

  @Option(
      names = {"--metrics-categories"},
      paramLabel = "<METRICS_CATEGORY>",
      description = "Metric categories to enable",
      split = ",",
      arity = "0..*")
  private Set<MetricCategory> metricsCategories = MetricsConfig.DEFAULT_METRICS_CATEGORIES;

  @Option(
      names = {"--metrics-host-allowlist"},
      paramLabel = "<hostname>",
      description = "Comma-separated list of hostnames to allow, or * to allow any host",
      split = ",",
      arity = "0..*")
  private final List<String> metricsHostAllowlist = MetricsConfig.DEFAULT_METRICS_HOST_ALLOWLIST;

  @Option(
      names = {"--Xmetrics-idle-timeout"},
      paramLabel = "<INTEGER>",
      description = "Idle timeout for metrics connections in seconds",
      arity = "1",
      hidden = true)
  private int idleTimeoutSeconds = MetricsConfig.DEFAULT_IDLE_TIMEOUT_SECONDS;

  @Option(
      names = {"--metrics-publish-endpoint"},
      paramLabel = "<URL>",
      description = "Publish metrics for node monitoring to an external service",
      arity = "1")
  private String metricsEndpoint = null;

  @Option(
      names = {"--metrics-publish-interval"},
      paramLabel = "<INTEGER>",
      description =
          "Interval between metric publications to the external service (measured in seconds)",
      arity = "1")
  private int metricsPublicationInterval = MetricsConfig.DEFAULT_METRICS_PUBLICATION_INTERVAL;

  @Option(
      names = {"--Xmetrics-block-timing-tracking-enabled"},
      hidden = true,
      showDefaultValue = Visibility.ALWAYS,
      paramLabel = "<BOOLEAN>",
      description = "Whether block timing metrics are tracked and reported",
      fallbackValue = "true",
      arity = "0..1")
  private boolean blockPerformanceEnabled = MetricsConfig.DEFAULT_BLOCK_PERFORMANCE_ENABLED;

  @Option(
      names = {"--Xmetrics-tick-timing-tracking-enabled"},
      hidden = true,
      showDefaultValue = Visibility.ALWAYS,
      paramLabel = "<BOOLEAN>",
      description = "Whether time tick timing metrics are tracked and reported",
      fallbackValue = "true",
      arity = "0..1")
  private boolean tickPerformanceEnabled = MetricsConfig.DEFAULT_TICK_PERFORMANCE_ENABLED;

  public void configure(TekuConfiguration.Builder builder) {
    builder.metrics(
        b ->
            b.metricsEnabled(metricsEnabled)
                .metricsPort(metricsPort)
                .metricsInterface(metricsInterface)
                .metricsCategories(metricsCategories)
                .metricsHostAllowlist(metricsHostAllowlist)
                .metricsPublishEndpoint(parseMetricsEndpointUrl())
                .metricsPublishInterval(metricsPublicationInterval)
                .idleTimeoutSeconds(idleTimeoutSeconds)
                .blockPerformanceEnabled(blockPerformanceEnabled)
                .tickPerformanceEnabled(tickPerformanceEnabled));
  }

  private URL parseMetricsEndpointUrl() {
    if (Strings.isNullOrEmpty(metricsEndpoint)) {
      return null;
    }
    try {
      return new URL(metricsEndpoint);
    } catch (MalformedURLException e) {
      throw new InvalidConfigurationException(
          "Invalid configuration. Metrics Endpoint has invalid syntax", e);
    }
  }
}
