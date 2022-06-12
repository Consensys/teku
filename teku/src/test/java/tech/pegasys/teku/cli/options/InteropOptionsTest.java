/*
 * Copyright ConsenSys Software Inc., 2022
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

public class InteropOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  void interopEnabled_shouldDefaultGenesisTime() {
    TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments("--Xinterop-enabled");

    assertThat(tekuConfiguration.validatorClient().getInteropConfig().isInteropEnabled()).isTrue();

    int interopGenesisTime =
        tekuConfiguration.validatorClient().getInteropConfig().getInteropGenesisTime();
    assertThat(interopGenesisTime)
        .isGreaterThanOrEqualTo((int) (System.currentTimeMillis() / 1000));

    assertThat(
            createConfigBuilder()
                .interop(b -> b.interopEnabled(true))
                .build()
                .validatorClient()
                .getInteropConfig()
                .getInteropGenesisTime())
        .isGreaterThanOrEqualTo((int) (System.currentTimeMillis() / 1000));

    assertThat(
            createConfigBuilder()
                .interop(b -> b.interopEnabled(true).interopGenesisTime(interopGenesisTime))
                .build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }
}
