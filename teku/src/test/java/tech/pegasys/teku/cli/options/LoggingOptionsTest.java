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
import static tech.pegasys.teku.infrastructure.logging.LoggingDestination.DEFAULT_BOTH;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingDestination;
import tech.pegasys.teku.util.cli.VersionProvider;

public class LoggingOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void loggingOptions_shouldReadFromConfigurationFile() {
    final LoggingConfig config =
        getTekuConfigurationFromFile("loggingOptions_config.yaml").loggingConfig();

    assertThat(config.getDestination()).isEqualTo(LoggingDestination.FILE);
    assertThat(config.isColorEnabled()).isFalse();
    assertThat(config.isIncludeEventsEnabled()).isFalse();
    assertThat(config.getLogFile()).isEqualTo(VersionProvider.defaultStoragePath() + "/logs/a.log");
    assertThat(config.getLogFileNamePattern())
        .isEqualTo(VersionProvider.defaultStoragePath() + "/logs/a%d.log");
    assertThat(config.isLogWireCipher()).isTrue();
    assertThat(config.isLogWirePlain()).isTrue();
    assertThat(config.isLogWireMuxFrames()).isTrue();
    assertThat(config.isLogWireGossip()).isTrue();
  }

  @Test
  public void logDestination_shouldHaveSensibleDefaultValue() {
    // This is important!
    // If it defaults to "both" or some other value custom log4j configs get overwritten
    beaconNodeCommand.parse(new String[0]);

    final LoggingConfig config = getResultingTekuConfiguration().loggingConfig();
    assertThat(config.getDestination()).isEqualTo(DEFAULT_BOTH);
  }

  @Test
  public void logDestination_shouldAcceptFileAsDestination() {
    final LoggingConfig config =
        getTekuConfigurationFromArguments("--log-destination", "file").loggingConfig();
    assertThat(config.getDestination()).isEqualTo(LoggingDestination.FILE);
  }

  @Test
  public void includeEvents_shouldNotRequireAValue() {
    final LoggingConfig config =
        getTekuConfigurationFromArguments("--log-include-events-enabled").loggingConfig();
    assertThat(config.isIncludeEventsEnabled()).isTrue();
  }

  @Test
  public void logDestination_shouldAcceptConsoleAsDestination() {
    final LoggingConfig config =
        getTekuConfigurationFromArguments("--log-destination", "console").loggingConfig();
    assertThat(config.getDestination()).isEqualTo(LoggingDestination.CONSOLE);
  }

  @Test
  public void logDestination_shouldAcceptBothAsDestination() {
    final LoggingConfig config =
        getTekuConfigurationFromArguments("--log-destination", "both").loggingConfig();
    assertThat(config.getDestination()).isEqualTo(LoggingDestination.BOTH);
  }
}
