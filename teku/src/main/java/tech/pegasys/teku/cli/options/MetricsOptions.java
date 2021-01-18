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

package tech.pegasys.teku.cli.options;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class MetricsOptions {

  public static final ImmutableSet<MetricCategory> DEFAULT_METRICS_CATEGORIES =
      ImmutableSet.<MetricCategory>builder()
          .addAll(EnumSet.allOf(StandardMetricCategory.class))
          .addAll(EnumSet.allOf(TekuMetricCategory.class))
          .build();

  @Option(
      names = {"--metrics-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enables metrics collection via Prometheus",
      fallbackValue = "true",
      arity = "0..1")
  private boolean metricsEnabled = false;

  @Option(
      names = {"--metrics-port"},
      paramLabel = "<INTEGER>",
      description = "Metrics port to expose metrics for Prometheus",
      arity = "1")
  private int metricsPort = 8008;

  @Option(
      names = {"--metrics-interface"},
      paramLabel = "<NETWORK>",
      description = "Metrics network interface to expose metrics for Prometheus",
      arity = "1")
  private String metricsInterface = "127.0.0.1";

  @Option(
      names = {"--metrics-categories"},
      paramLabel = "<METRICS_CATEGORY>",
      description = "Metric categories to enable",
      split = ",",
      arity = "0..*")
  private Set<MetricCategory> metricsCategories = DEFAULT_METRICS_CATEGORIES;

  @Option(
      names = {"--metrics-host-allowlist"},
      paramLabel = "<hostname>",
      description = "Comma-separated list of hostnames to allow, or * to allow any host",
      split = ",",
      arity = "0..*")
  private final List<String> metricsHostAllowlist = Arrays.asList("127.0.0.1", "localhost");

  public void configure(TekuConfiguration.Builder builder) {
    builder.metrics(
        b ->
            b.metricsEnabled(metricsEnabled)
                .metricsPort(metricsPort)
                .metricsInterface(metricsInterface)
                .metricsCategories(metricsCategories)
                .metricsHostAllowlist(metricsHostAllowlist));
  }
}
