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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.util.config.TekuConfiguration;

public class BeaconRestApiOptionsTest extends AbstractBeaconNodeCommandTest {
  @Test
  public void shouldReadFromConfigurationFile() {
    final TekuConfiguration config =
        getTekuConfigurationFromFile("beaconRestApiOptions_config.yaml");

    assertThat(config.getRestApiInterface()).isEqualTo("127.100.0.1");
    assertThat(config.getRestApiPort()).isEqualTo(5055);
    assertThat(config.isRestApiDocsEnabled()).isTrue();
    assertThat(config.isRestApiEnabled()).isTrue();
  }

  @Test
  public void restApiDocsEnabled_shouldNotRequireAValue() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--rest-api-docs-enabled");
    assertThat(tekuConfiguration.isRestApiDocsEnabled()).isTrue();
  }

  @Test
  public void restApiEnabled_shouldNotRequireAValue() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--rest-api-enabled");
    assertThat(tekuConfiguration.isRestApiEnabled()).isTrue();
  }

  @Test
  public void restApiHostAllowlist_shouldNotRequireAValue() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--rest-api-host-allowlist");
    assertThat(tekuConfiguration.getRestApiHostAllowlist()).isEmpty();
  }

  @Test
  public void restApiHostAllowlist_shouldSupportAllowingMultipleHosts() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--rest-api-host-allowlist", "my.host,their.host");
    assertThat(tekuConfiguration.getRestApiHostAllowlist()).containsOnly("my.host", "their.host");
  }

  @Test
  public void restApiHostAllowlist_shouldSupportAllowingAllHosts() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--rest-api-host-allowlist", "*");
    assertThat(tekuConfiguration.getRestApiHostAllowlist()).containsOnly("*");
  }

  @Test
  public void restApiHostAllowlist_shouldDefaultToLocalhost() {
    assertThat(getTekuConfigurationFromArguments().getRestApiHostAllowlist())
        .containsOnly("localhost", "127.0.0.1");
  }
}
