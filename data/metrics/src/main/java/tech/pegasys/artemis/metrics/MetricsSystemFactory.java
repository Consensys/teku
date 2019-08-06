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

import java.util.EnumSet;
import java.util.HashSet;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.StandardMetricCategory;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.PrometheusMetricsSystem;

/**
 * Registers a Prometheus REST endpoint that can be called remotely by Prometheus to gather metrics.
 */
public final class MetricsSystemFactory {

  /**
   * Creates a new endpoint for the network interface and port provided. The endpoint lifecycle is
   * tied to the lifecycle of the Vertx object.
   *
   * @param config the configuration for this Artemis instance.
   */
  public static MetricsSystem createMetricsSystem(ArtemisConfiguration config) {
    final MetricsConfiguration metricsConfiguration =
        MetricsConfiguration.builder()
            .enabled(config.isMetricsEnabled())
            .metricCategories(new HashSet<>(EnumSet.allOf(StandardMetricCategory.class)))
            .build();
    return PrometheusMetricsSystem.init(metricsConfiguration);
  }
}
