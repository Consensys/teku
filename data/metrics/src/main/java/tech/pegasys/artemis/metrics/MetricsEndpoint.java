/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.metrics;

import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableMap;
import io.vertx.core.Vertx;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.StandardMetricCategory;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsService;
import tech.pegasys.pantheon.metrics.prometheus.PrometheusMetricsSystem;

public class MetricsEndpoint {

  private final Optional<MetricsService> metricsService;
  private final MetricsSystem metricsSystem;
  private static final Map<String, MetricCategory> SUPPORTED_CATEGORIES;

  static {
    final ImmutableMap.Builder<String, MetricCategory> builder = ImmutableMap.builder();
    addCategories(builder, StandardMetricCategory.class);
    addCategories(builder, ArtemisMetricCategory.class);
    SUPPORTED_CATEGORIES = builder.build();
  }

  public MetricsEndpoint(final ArtemisConfiguration artemisConfig, final Vertx vertx) {
    final MetricsConfiguration metricsConfig = createMetricsConfiguration(artemisConfig);
    metricsSystem = PrometheusMetricsSystem.init(metricsConfig);
    if (metricsConfig.isEnabled()) {
      metricsService = Optional.of(MetricsService.create(vertx, metricsConfig, metricsSystem));
    } else {
      metricsService = Optional.empty();
    }
  }

  public void start() {
    metricsService.ifPresent(MetricsService::start);
  }

  public void stop() {
    metricsService.ifPresent(MetricsService::stop);
  }

  public MetricsSystem getMetricsSystem() {
    return metricsSystem;
  }

  private MetricsConfiguration createMetricsConfiguration(
      final ArtemisConfiguration artemisConfig) {
    return MetricsConfiguration.builder()
        .enabled(artemisConfig.isMetricsEnabled())
        .port(artemisConfig.getMetricsPort())
        .host(artemisConfig.getMetricsNetworkInterface())
        .metricCategories(getEnabledMetricCategories(artemisConfig))
        .build();
  }

  private Set<MetricCategory> getEnabledMetricCategories(final ArtemisConfiguration artemisConfig) {
    return artemisConfig.getMetricCategories().stream()
        .map(SUPPORTED_CATEGORIES::get)
        .filter(Objects::nonNull)
        .collect(toSet());
  }

  private static <T extends Enum<T> & MetricCategory> void addCategories(
      ImmutableMap.Builder<String, MetricCategory> builder, Class<T> categoryEnum) {
    EnumSet.allOf(categoryEnum).forEach(category -> builder.put(category.name(), category));
  }
}
