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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.metrics.StandardMetricCategory.JVM;
import static org.hyperledger.besu.metrics.StandardMetricCategory.PROCESS;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.EVENTBUS;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.LIBP2P;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.NETWORK;

import java.util.Set;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.infrastructure.metrics.MetricsConfig;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class MetricsOptionsTest extends AbstractBeaconNodeCommandTest {
  @Test
  public void shouldReadFromConfigurationFile() {
    final MetricsConfig config =
        getTekuConfigurationFromFile("metricsOptions_config.yaml").metricsConfig();

    assertThat(config.getMetricsInterface()).isEqualTo("127.100.0.1");
    assertThat(config.getMetricsPort()).isEqualTo(8888);
    assertThat(config.isMetricsEnabled()).isTrue();
    assertThat(config.getMetricsCategories()).isEqualTo(Set.of(JVM, PROCESS));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(TekuMetricCategory.class)
  public void metricsCategories_shouldAcceptValues(MetricCategory category) {
    final MetricsConfig config =
        getTekuConfigurationFromArguments("--metrics-categories", category.toString())
            .metricsConfig();
    assertThat(config.getMetricsCategories()).isEqualTo(Set.of(category));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(StandardMetricCategory.class)
  public void metricsCategories_shouldAcceptStandardMetricCategories(MetricCategory category) {
    final MetricsConfig config =
        getTekuConfigurationFromArguments("--metrics-categories", category.toString())
            .metricsConfig();
    assertThat(config.getMetricsCategories()).isEqualTo(Set.of(category));
  }

  @Test
  public void metricsCategories_shouldAcceptMultipleValues() {
    final MetricsConfig config =
        getTekuConfigurationFromArguments("--metrics-categories", "LIBP2P,NETWORK,EVENTBUS,PROCESS")
            .metricsConfig();
    assertThat(config.getMetricsCategories()).isEqualTo(Set.of(LIBP2P, NETWORK, EVENTBUS, PROCESS));
  }

  @Test
  public void metricsCategories_shouldNotBeCaseSensitive() {
    final MetricsConfig config =
        getTekuConfigurationFromArguments("--metrics-categories", "LibP2P,network,EventBUS,PROCESS")
            .metricsConfig();
    assertThat(config.getMetricsCategories()).isEqualTo(Set.of(LIBP2P, NETWORK, EVENTBUS, PROCESS));
  }

  @Test
  public void metricsEnabled_shouldNotRequireAValue() {
    final MetricsConfig config =
        getTekuConfigurationFromArguments("--metrics-enabled").metricsConfig();
    assertThat(config.isMetricsEnabled()).isTrue();
  }

  @Test
  public void metricsHostAllowlist_shouldNotRequireAValue() {
    final MetricsConfig config =
        getTekuConfigurationFromArguments("--metrics-host-allowlist").metricsConfig();
    assertThat(config.getMetricsHostAllowlist()).isEmpty();
  }

  @Test
  public void metricsHostAllowlist_shouldSupportAllowingMultipleHosts() {
    final MetricsConfig config =
        getTekuConfigurationFromArguments("--metrics-host-allowlist", "my.host,their.host")
            .metricsConfig();
    assertThat(config.getMetricsHostAllowlist()).containsOnly("my.host", "their.host");
  }

  @Test
  public void metricsHostAllowlist_shouldSupportAllowingAllHosts() {
    final MetricsConfig config =
        getTekuConfigurationFromArguments("--metrics-host-allowlist", "*").metricsConfig();
    assertThat(config.getMetricsHostAllowlist()).containsOnly("*");
  }

  @Test
  public void metricsHostAllowlist_shouldDefaultToLocalhost() {
    assertThat(getTekuConfigurationFromArguments().metricsConfig().getMetricsHostAllowlist())
        .containsOnly("localhost", "127.0.0.1");
  }
}
