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
import java.util.Optional;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.service.serviceutils.ServiceInterface;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.pantheon.metrics.StandardMetricCategory;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsService;

public class MetricsServer implements ServiceInterface {

  private Optional<MetricsService> metricsService = Optional.empty();

  @Override
  public void init(final ServiceConfig config) {
    final ArtemisConfiguration artemisConfig = config.getConfig();
    if (!artemisConfig.isMetricsEnabled()) {
      return;
    }
    final MetricsConfiguration metricsConfig =
        MetricsConfiguration.builder()
            .enabled(artemisConfig.isMetricsEnabled())
            .port(artemisConfig.getMetricsPort())
            .host(artemisConfig.getMetricsNetworkInterface())
            .metricCategories(new HashSet<>(EnumSet.allOf(StandardMetricCategory.class)))
            .build();
    metricsService =
        Optional.of(
            MetricsService.create(config.getVertx(), metricsConfig, config.getMetricsSystem()));
  }

  @Override
  public void run() {
    metricsService.ifPresent(MetricsService::start);
  }

  @Override
  public void stop() {
    metricsService.ifPresent(MetricsService::stop);
  }
}
