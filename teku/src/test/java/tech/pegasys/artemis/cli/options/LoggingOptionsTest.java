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
import static tech.pegasys.artemis.cli.options.LoggingOptions.LOG_DESTINATION_OPTION_NAME;
import static tech.pegasys.artemis.util.config.LoggingDestination.DEFAULT_BOTH;

import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.LoggingDestination;

public class LoggingOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void loggingOptions_shouldReadFromConfigurationFile() {
    final ArtemisConfiguration config =
        getArtemisConfigurationFromFile("loggingOptions_config.yaml");

    assertThat(config.getLogDestination()).isEqualTo(LoggingDestination.FILE);
    assertThat(config.isLogColorEnabled()).isFalse();
    assertThat(config.isLogIncludeEventsEnabled()).isFalse();
    assertThat(config.getLogFile()).isEqualTo("a.log");
    assertThat(config.getLogFileNamePattern()).isEqualTo("a%d.log");
  }

  @Test
  public void logDestination_shouldHaveSensibleDefaultValue() {
    // This is important!
    // If it defaults to "both" or some other value custom log4j configs get overwritten
    beaconNodeCommand.parse(new String[0]);

    final ArtemisConfiguration artemisConfiguration = getResultingArtemisConfiguration();
    assertThat(artemisConfiguration.getLogDestination()).isEqualTo(DEFAULT_BOTH);
  }

  @Test
  public void logDestination_shouldAcceptFileAsDestination() {
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromArguments(LOG_DESTINATION_OPTION_NAME, "file");
    assertThat(artemisConfiguration.getLogDestination()).isEqualTo(LoggingDestination.FILE);
  }

  @Test
  public void logDestination_shouldAcceptConsoleAsDestination() {
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromArguments(LOG_DESTINATION_OPTION_NAME, "console");
    assertThat(artemisConfiguration.getLogDestination()).isEqualTo(LoggingDestination.CONSOLE);
  }

  @Test
  public void logDestination_shouldAcceptBothAsDestination() {
    final ArtemisConfiguration artemisConfiguration =
        getArtemisConfigurationFromArguments(LOG_DESTINATION_OPTION_NAME, "both");
    assertThat(artemisConfiguration.getLogDestination()).isEqualTo(LoggingDestination.BOTH);
  }
}
