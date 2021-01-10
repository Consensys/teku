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
import tech.pegasys.teku.config.TekuConfiguration;

public class DepositOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadDepositOptionsFromConfigurationFile() {
    final TekuConfiguration config = getTekuConfigurationFromFile("depositOptions_config.yaml");

    assertThat(config.powchain().isEnabled()).isTrue();
    assertThat(config.powchain().getEth1Endpoint()).isEqualTo("http://example.com:1234/path/");
  }

  @Test
  public void shouldReportEth1EnabledIfEndpointSpecified() {
    final String[] args = {"--eth1-endpoint", "http://example.com:1234/path/"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.powchain().isEnabled()).isTrue();
  }

  @Test
  public void shouldReportEth1DisabledIfEndpointNotSpecified() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.powchain().isEnabled()).isFalse();
  }

  @Test
  public void shouldReportEth1DisabledIfEndpointIsEmpty() {
    final String[] args = {"--eth1-endpoint", "   "};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.powchain().isEnabled()).isFalse();
  }

  @Test
  public void eth1Deposit_requiredIfEth1Endpoint() {
    final String[] args = {
      "--network", "minimal", "--eth1-endpoint", "http://example.com:1234/path/"
    };

    beaconNodeCommand.parse(args);
    final String str = getCommandLineOutput();
    assertThat(str)
        .contains("Eth1 deposit contract address is required if an eth1 endpoint is specified");
    assertThat(str).contains("To display full help:");
    assertThat(str).contains("--help");
    assertThat(str).doesNotContain("Default");
  }
}
