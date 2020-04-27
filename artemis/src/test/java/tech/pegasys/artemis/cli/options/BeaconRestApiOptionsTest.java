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
import static tech.pegasys.artemis.cli.options.BeaconRestApiOptions.REST_API_DOCS_ENABLED_OPTION_NAME;
import static tech.pegasys.artemis.cli.options.BeaconRestApiOptions.REST_API_ENABLED_OPTION_NAME;

import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class BeaconRestApiOptionsTest extends AbstractBeaconNodeCommandTest {
  @Test
  public void shouldReadFromConfigurationFile() {
    final ArtemisConfiguration config =
        getArtemisConfigurationFromFile("beaconRestApiOptions_config.yaml");

    assertThat(config.getRestApiInterface()).isEqualTo("127.100.0.1");
    assertThat(config.getRestApiPort()).isEqualTo(5055);
    assertThat(config.isRestApiDocsEnabled()).isTrue();
    assertThat(config.isRestApiEnabled()).isTrue();
  }

  @Test
  public void restApiDocsEnabled_shouldNotRequireAValue() {
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromArguments(REST_API_DOCS_ENABLED_OPTION_NAME);
    assertThat(artemisConfiguration.isRestApiDocsEnabled()).isTrue();
  }

  @Test
  public void restApiEnabled_shouldNotRequireAValue() {
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromArguments(REST_API_ENABLED_OPTION_NAME);
    assertThat(artemisConfiguration.isRestApiEnabled()).isTrue();
  }
}
