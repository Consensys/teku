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
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;

public class BeaconRestApiOptionsTest extends AbstractBeaconNodeCommandTest {

  private BeaconRestApiConfig getConfig(final TekuConfiguration tekuConfiguration) {
    return tekuConfiguration.beaconChain().beaconRestApiConfig();
  }

  @Test
  public void shouldReadFromConfigurationFile() {
    final BeaconRestApiConfig config =
        getConfig(getTekuConfigurationFromFile("beaconRestApiOptions_config.yaml"));

    assertThat(config.getRestApiInterface()).isEqualTo("127.100.0.1");
    assertThat(config.getRestApiPort()).isEqualTo(5055);
    assertThat(config.isRestApiDocsEnabled()).isTrue();
    assertThat(config.isRestApiEnabled()).isTrue();
    assertThat(config.getRestApiHostAllowlist()).containsExactly("test.domain.com", "11.12.13.14");
    assertThat(config.getRestApiCorsAllowedOrigins())
        .containsExactly("127.1.2.3", "origin.allowed.com");
  }

  @Test
  public void restApiDocsEnabled_shouldNotRequireAValue() {
    final BeaconRestApiConfig config =
        getConfig(getTekuConfigurationFromArguments("--rest-api-docs-enabled"));
    assertThat(config.isRestApiDocsEnabled()).isTrue();
  }

  @Test
  public void restApiEnabled_shouldNotRequireAValue() {
    final BeaconRestApiConfig config =
        getConfig(getTekuConfigurationFromArguments("--rest-api-enabled"));
    assertThat(config.isRestApiEnabled()).isTrue();
  }

  @Test
  public void restApiHostAllowlist_shouldNotRequireAValue() {
    final BeaconRestApiConfig config =
        getConfig(getTekuConfigurationFromArguments("--rest-api-host-allowlist"));
    assertThat(config.getRestApiHostAllowlist()).isEmpty();
  }

  @Test
  public void restApiHostAllowlist_shouldSupportAllowingMultipleHosts() {
    final BeaconRestApiConfig config =
        getConfig(
            getTekuConfigurationFromArguments("--rest-api-host-allowlist", "my.host,their.host"));
    assertThat(config.getRestApiHostAllowlist()).containsOnly("my.host", "their.host");
  }

  @Test
  public void restApiHostAllowlist_shouldSupportAllowingAllHosts() {
    final BeaconRestApiConfig config =
        getConfig(getTekuConfigurationFromArguments("--rest-api-host-allowlist", "*"));
    assertThat(config.getRestApiHostAllowlist()).containsOnly("*");
  }

  @Test
  public void restApiHostAllowlist_shouldDefaultToLocalhost() {
    final BeaconRestApiConfig config = getConfig(getTekuConfigurationFromArguments());
    assertThat(config.getRestApiHostAllowlist()).containsOnly("localhost", "127.0.0.1");
  }

  @Test
  public void restApiCorsAllowedOrigins_shouldNotRequireAValue() {
    final BeaconRestApiConfig config =
        getConfig(getTekuConfigurationFromArguments("--rest-api-cors-origins"));
    assertThat(config.getRestApiCorsAllowedOrigins()).isEmpty();
  }

  @Test
  public void restApiCorsAllowedOrigins_shouldSupportAllowingMultipleHosts() {
    final BeaconRestApiConfig config =
        getConfig(
            getTekuConfigurationFromArguments("--rest-api-cors-origins", "my.host,their.host"));
    assertThat(config.getRestApiCorsAllowedOrigins()).containsOnly("my.host", "their.host");
  }

  @Test
  public void restApiCorsAllowedOrigins_shouldSupportAllowingAllHosts() {
    final BeaconRestApiConfig config =
        getConfig(getTekuConfigurationFromArguments("--rest-api-cors-origins", "*"));
    assertThat(config.getRestApiCorsAllowedOrigins()).containsOnly("*");
  }
}
