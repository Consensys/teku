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
import static tech.pegasys.artemis.cli.options.P2POptions.P2P_DISCOVERY_ENABLED_OPTION_NAME;
import static tech.pegasys.artemis.cli.options.P2POptions.P2P_ENABLED_OPTION_NAME;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class P2POptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadFromConfigurationFile() {
    final ArtemisConfiguration config = getArtemisConfigurationFromFile("P2POptions_config.yaml");

    assertThat(config.getP2pAdvertisedIp()).isEqualTo("127.200.0.1");
    assertThat(config.getP2pInterface()).isEqualTo("127.100.0.1");
    assertThat(config.isP2pEnabled()).isTrue();
    assertThat(config.isP2pDiscoveryEnabled()).isTrue();
    assertThat(config.getP2pPort()).isEqualTo(4321);
    assertThat(config.getP2pPrivateKeyFile()).isEqualTo("/the/file");
    assertThat(config.getP2pStaticPeers()).isEqualTo(List.of("127.1.0.1", "127.1.1.1"));
    assertThat(config.getP2pPeerLowerBound()).isEqualTo(11);
    assertThat(config.getP2pPeerUpperBound()).isEqualTo(12);
  }

  @Test
  public void p2pEnabled_shouldNotRequireAValue() {
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromArguments(P2P_ENABLED_OPTION_NAME);
    assertThat(artemisConfiguration.isP2pEnabled()).isTrue();
  }

  @Test
  public void p2pDiscoveryEnabled_shouldNotRequireAValue() {
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromArguments(P2P_DISCOVERY_ENABLED_OPTION_NAME);
    assertThat(artemisConfiguration.isP2pEnabled()).isTrue();
  }
}
