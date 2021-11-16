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

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.metrics.MetricsConfig;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class MetricsOptionsTest extends AbstractBeaconNodeCommandTest {
  private static final Comparator<Set<?>> SET_COMPARATOR = (o1, o2) -> o1.equals(o2) ? 0 : 1;
  private static final String[] SET_FIELDS = new String[] {"metricsConfig.metricsCategories"};

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
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--metrics-categories", category.toString());
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(config.getMetricsCategories()).isEqualTo(Set.of(category));
    assertThat(createConfigBuilder().metrics(b -> b.metricsCategories(Set.of(category))).build())
        .usingRecursiveComparison()
        .withComparatorForFields(SET_COMPARATOR, SET_FIELDS)
        .isEqualTo(tekuConfiguration);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(StandardMetricCategory.class)
  public void metricsCategories_shouldAcceptStandardMetricCategories(MetricCategory category) {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--metrics-categories", category.toString());
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(config.getMetricsCategories()).isEqualTo(Set.of(category));
    assertThat(createConfigBuilder().metrics(b -> b.metricsCategories(Set.of(category))).build())
        .usingRecursiveComparison()
        .withComparatorForFields(SET_COMPARATOR, SET_FIELDS)
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void metricsCategories_shouldAcceptMultipleValues() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--metrics-categories", "LIBP2P,NETWORK,EVENTBUS,PROCESS");
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(config.getMetricsCategories()).isEqualTo(Set.of(LIBP2P, NETWORK, EVENTBUS, PROCESS));
    assertThat(
            createConfigBuilder()
                .metrics(b -> b.metricsCategories(Set.of(LIBP2P, NETWORK, EVENTBUS, PROCESS)))
                .build())
        .usingRecursiveComparison()
        .withComparatorForFields(SET_COMPARATOR, SET_FIELDS)
        .isEqualTo(tekuConfiguration);
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
    TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments("--metrics-enabled");
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(config.isMetricsEnabled()).isTrue();
    assertThat(createConfigBuilder().metrics(b -> b.metricsEnabled(true)).build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void metricsHostAllowlist_shouldNotRequireAValue() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--metrics-host-allowlist");
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(config.getMetricsHostAllowlist()).isEmpty();
    assertThat(createConfigBuilder().metrics(b -> b.metricsHostAllowlist(List.of())).build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void metricsHostAllowlist_shouldSupportAllowingMultipleHosts() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--metrics-host-allowlist", "my.host,their.host");
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(config.getMetricsHostAllowlist()).containsOnly("my.host", "their.host");
    assertThat(
            createConfigBuilder()
                .metrics(b -> b.metricsHostAllowlist(List.of("my.host", "their.host")))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void metricsHostAllowlist_shouldSupportAllowingAllHosts() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--metrics-host-allowlist", "*");
    final MetricsConfig config = tekuConfiguration.metricsConfig();
    assertThat(config.getMetricsHostAllowlist()).containsOnly("*");
    assertThat(createConfigBuilder().metrics(b -> b.metricsHostAllowlist(List.of("*"))).build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void metricsHostAllowlist_shouldDefaultToLocalhost() {
    assertThat(getTekuConfigurationFromArguments().metricsConfig().getMetricsHostAllowlist())
        .containsOnly("localhost", "127.0.0.1");
  }
}
