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
import tech.pegasys.teku.util.config.Eth1Address;
import tech.pegasys.teku.util.config.TekuConfiguration;

public class DepositOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadDepositOptionsFromConfigurationFile() {
    final TekuConfiguration config = getTekuConfigurationFromFile("depositOptions_config.yaml");
    final Eth1Address address =
        Eth1Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");

    assertThat(config.isEth1Enabled()).isTrue();
    assertThat(config.getEth1DepositContractAddress()).isEqualTo(address);
    assertThat(config.getEth1Endpoint()).isEqualTo("http://example.com:1234/path/");
    assertThat(config.isEth1DepositsFromStorageEnabled()).isTrue();
  }

  @Test
  public void shouldReportEth1EnabledIfEndpointSpecified() {
    final String[] args = {
      "--eth1-endpoint",
      "http://example.com:1234/path/",
      "--eth1-deposit-contract-address",
      "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
    };
    final TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments(args);
    assertThat(tekuConfiguration.isEth1Enabled()).isTrue();
  }

  @Test
  public void shouldReportEth1DisabledIfEndpointNotSpecified() {
    final TekuConfiguration tekuConfigurationFromArguments = getTekuConfigurationFromArguments();
    assertThat(tekuConfigurationFromArguments.isEth1Enabled()).isFalse();
  }

  @Test
  public void shouldDisableLoadFromStorageByDefault() {
    final TekuConfiguration tekuConfigurationFromArguments = getTekuConfigurationFromArguments();
    assertThat(tekuConfigurationFromArguments.isEth1DepositsFromStorageEnabled()).isFalse();
  }

  @Test
  public void eth1Deposit_requiredIfEth1Endpoint() {
    final String[] args = {"--eth1-endpoint", "http://example.com:1234/path/"};

    beaconNodeCommand.parse(args);
    final String str = getCommandLineOutput();
    assertThat(str).contains("eth1-deposit");
    assertThat(str).contains("To display full help:");
    assertThat(str).contains("--help");
    assertThat(str).doesNotContain("Default");
  }
}
