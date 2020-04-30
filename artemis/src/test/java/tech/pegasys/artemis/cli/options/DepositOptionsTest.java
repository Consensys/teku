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

import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Eth1DepositContractAddress;

public class DepositOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadDepositOptionsFromConfigurationFile() {
    final ArtemisConfiguration config =
        getArtemisConfigurationFromFile("depositOptions_config.yaml");
    Eth1DepositContractAddress address =
        Eth1DepositContractAddress.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");

    assertThat(config.isEth1Enabled()).isFalse();
    assertThat(config.getEth1DepositContractAddress()).isEqualTo(address);
    assertThat(config.getEth1Endpoint()).isEqualTo("http://example.com:1234/path/");
  }

  @Test
  public void eth1Enabled_shouldNotRequireAValue() {
    final ArtemisConfiguration config = getArtemisConfigurationFromArguments("--eth1-enabled");
    assertThat(config.isEth1Enabled()).isTrue();
  }
}
