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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.artemis.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class MetricsOptionsTest extends AbstractBeaconNodeCommandTest {
  @Test
  public void shouldReadFromConfigurationFile() {
    final ArtemisConfiguration config =
        getArtemisConfigurationFromFile("metricsOptions_config.yaml");

    assertThat(config.getMetricsInterface()).isEqualTo("127.100.0.1");
    assertThat(config.getMetricsPort()).isEqualTo(8888);
    assertThat(config.isMetricsEnabled()).isTrue();
    assertThat(config.getMetricsCategories()).isEqualTo(List.of("JVM", "PROCESS"));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"BEACON", "LIBP2P", "NETWORK", "EVENTBUS", "JVM", "PROCESS"})
  public void metricsCategories_shouldAcceptValues(String category) {
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromArguments("--metrics-categories", category);
    assertThat(artemisConfiguration.getMetricsCategories()).isEqualTo(List.of(category));
  }

  @Test
  public void metricsCategories_shouldAcceptMultipleValues() {
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromArguments(
            "--metrics-categories", "LIBP2P,NETWORK,EVENTBUS,PROCESS");
    assertThat(artemisConfiguration.getMetricsCategories())
        .isEqualTo(List.of("LIBP2P", "NETWORK", "EVENTBUS", "PROCESS"));
  }

  @Test
  public void metricsEnabled_shouldNotRequireAValue() {
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromArguments("--metrics-enabled");
    assertThat(artemisConfiguration.isMetricsEnabled()).isTrue();
  }
}
