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

package tech.pegasys.artemis.cli.options;

import com.google.common.collect.ImmutableSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import picocli.CommandLine;
import tech.pegasys.artemis.metrics.ArtemisMetricCategory;

public class MetricsOptions {

  public static final String METRICS_ENABLED_OPTION_NAME = "--metrics-enabled";
  public static final String METRICS_PORT_OPTION_NAME = "--metrics-port";
  public static final String METRICS_INTERFACE_OPTION_NAME = "--metrics-interface";
  public static final String METRICS_CATEGORIES_OPTION_NAME = "--metrics-categories";

  public static final boolean DEFAULT_METRICS_ENABLED = false;
  public static final int DEFAULT_METRICS_PORT = 8008;
  public static final String DEFAULT_METRICS_INTERFACE = "127.0.0.1";
  public static final ImmutableSet<MetricCategory> DEFAULT_METRICS_CATEGORIES =
      ImmutableSet.<MetricCategory>builder()
          .addAll(EnumSet.allOf(StandardMetricCategory.class))
          .addAll(EnumSet.allOf(ArtemisMetricCategory.class))
          .build();

  @CommandLine.Option(
      names = {METRICS_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Enables metrics collection via Prometheus",
      fallbackValue = "true",
      arity = "0..1")
  private boolean metricsEnabled = DEFAULT_METRICS_ENABLED;

  @CommandLine.Option(
      names = {METRICS_PORT_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Metrics port to expose metrics for Prometheus",
      arity = "1")
  private int metricsPort = DEFAULT_METRICS_PORT;

  @CommandLine.Option(
      names = {METRICS_INTERFACE_OPTION_NAME},
      paramLabel = "<NETWORK>",
      description = "Metrics network interface to expose metrics for Prometheus",
      arity = "1")
  private String metricsInterface = DEFAULT_METRICS_INTERFACE;

  @CommandLine.Option(
      names = {METRICS_CATEGORIES_OPTION_NAME},
      paramLabel = "<METRICS_CATEGORY>",
      description = "Metric categories to enable",
      split = ",",
      arity = "0..*")
  private Set<MetricCategory> metricsCategories = DEFAULT_METRICS_CATEGORIES;

  public boolean isMetricsEnabled() {
    return metricsEnabled;
  }

  public int getMetricsPort() {
    return metricsPort;
  }

  public String getMetricsInterface() {
    return metricsInterface;
  }

  public List<String> getMetricsCategories() {
    return metricsCategories.stream().map(value -> value.toString()).collect(Collectors.toList());
  }
}
