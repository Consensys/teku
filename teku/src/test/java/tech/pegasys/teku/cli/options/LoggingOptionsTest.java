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
import static tech.pegasys.teku.cli.OSUtils.SLASH;
import static tech.pegasys.teku.infrastructure.logging.LoggingDestination.DEFAULT_BOTH;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingDestination;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.networking.p2p.network.config.WireLogsConfig;

public class LoggingOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void loggingOptions_shouldReadFromConfigurationFile() {
    final TekuConfiguration config = getTekuConfigurationFromFile("loggingOptions_config.yaml");

    final LoggingConfig loggingConfig = config.loggingConfig();
    assertThat(loggingConfig.getDestination()).isEqualTo(LoggingDestination.FILE);
    assertThat(loggingConfig.isColorEnabled()).isFalse();
    assertThat(loggingConfig.isIncludeEventsEnabled()).isFalse();
    assertThat(loggingConfig.getLogFile())
        .isEqualTo(VersionProvider.defaultStoragePath() + SLASH + "logs" + SLASH + "a.log");
    assertThat(loggingConfig.getLogFileNamePattern())
        .isEqualTo(VersionProvider.defaultStoragePath() + SLASH + "logs" + SLASH + "a%d.log");

    final WireLogsConfig wireConfig = config.network().getWireLogsConfig();
    assertThat(wireConfig.isLogWireCipher()).isTrue();
    assertThat(wireConfig.isLogWirePlain()).isTrue();
    assertThat(wireConfig.isLogWireMuxFrames()).isTrue();
    assertThat(wireConfig.isLogWireGossip()).isTrue();
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
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--log-destination", "file");
    final LoggingConfig config = tekuConfiguration.loggingConfig();
    assertThat(config.getDestination()).isEqualTo(LoggingDestination.FILE);
    assertThat(createConfigBuilder().logging(b -> b.destination(LoggingDestination.FILE)).build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void includeEvents_shouldNotRequireAValue() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--log-include-events-enabled");
    final LoggingConfig config = tekuConfiguration.loggingConfig();
    assertThat(config.isIncludeEventsEnabled()).isTrue();
    assertThat(createConfigBuilder().logging(b -> b.includeEventsEnabled(true)).build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void logDestination_shouldAcceptConsoleAsDestination() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--log-destination", "console");
    final LoggingConfig config = tekuConfiguration.loggingConfig();
    assertThat(config.getDestination()).isEqualTo(LoggingDestination.CONSOLE);
    assertThat(
            createConfigBuilder().logging(b -> b.destination(LoggingDestination.CONSOLE)).build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }

  @Test
  public void logDestination_shouldAcceptBothAsDestination() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--log-destination", "both");
    final LoggingConfig config = tekuConfiguration.loggingConfig();
    assertThat(config.getDestination()).isEqualTo(LoggingDestination.BOTH);
    assertThat(createConfigBuilder().logging(b -> b.destination(LoggingDestination.BOTH)).build())
        .usingRecursiveComparison()
        .isEqualTo(tekuConfiguration);
  }
}
