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
import tech.pegasys.teku.infrastructure.logging.LoggingDestination;
import tech.pegasys.teku.util.config.GlobalConfiguration;

public class LoggingOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void loggingOptions_shouldReadFromConfigurationFile() {
    final GlobalConfiguration config = getGlobalConfigurationFromFile("loggingOptions_config.yaml");

    assertThat(config.getLogDestination()).isEqualTo(LoggingDestination.FILE);
    assertThat(config.isLogColorEnabled()).isFalse();
    assertThat(config.isLogIncludeEventsEnabled()).isFalse();
    assertThat(config.getLogFile()).isEqualTo("a.log");
    assertThat(config.getLogFileNamePattern()).endsWith("a%d.log");
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

    final GlobalConfiguration globalConfiguration = getResultingGlobalConfiguration();
    assertThat(globalConfiguration.getLogDestination()).isEqualTo(DEFAULT_BOTH);
  }

  @Test
  public void logDestination_shouldAcceptFileAsDestination() {
    final GlobalConfiguration globalConfiguration =
        getGlobalConfigurationFromArguments("--log-destination", "file");
    assertThat(globalConfiguration.getLogDestination()).isEqualTo(LoggingDestination.FILE);
  }

  @Test
  public void includeEvents_shouldNotRequireAValue() {
    final GlobalConfiguration globalConfiguration =
        getGlobalConfigurationFromArguments("--log-include-events-enabled");
    assertThat(globalConfiguration.isLogIncludeEventsEnabled()).isTrue();
  }

  @Test
  public void logDestination_shouldAcceptConsoleAsDestination() {
    final GlobalConfiguration globalConfiguration =
        getGlobalConfigurationFromArguments("--log-destination", "console");
    assertThat(globalConfiguration.getLogDestination()).isEqualTo(LoggingDestination.CONSOLE);
  }

  @Test
  public void logDestination_shouldAcceptBothAsDestination() {
    final GlobalConfiguration globalConfiguration =
        getGlobalConfigurationFromArguments("--log-destination", "both");
    assertThat(globalConfiguration.getLogDestination()).isEqualTo(LoggingDestination.BOTH);
  }

  @Test
  public void defaultLogfileGivenDataDir_shouldDetermineForBeaconNode() {
    assertThat(LoggingOptions.getDefaultLogFileGivenDataDir("/foo", false))
        .isEqualTo("/foo/logs/teku.log");
  }

  @Test
  public void defaultLogfileGivenDataDir_shouldDetermineForValidator() {
    assertThat(LoggingOptions.getDefaultLogFileGivenDataDir("/foo", true))
        .isEqualTo("/foo/logs/teku-validator.log");
  }

  @Test
  public void defaultLogPatternGivenDataDir_shouldDeterminePathFromDefaultPattern() {
    assertThat(
            LoggingOptions.getLogPatternGivenDataDir(
                "/foo", LoggingOptions.DEFAULT_LOG_PATH_PATTERN))
        .isEqualTo("/foo/logs/" + LoggingOptions.DEFAULT_LOG_FILE_NAME_PATTERN);
  }

  @Test
  public void defaultLogPatternGivenDataDir_shouldDeterminePathFromPatternWithoutPath() {
    assertThat(LoggingOptions.getLogPatternGivenDataDir("/foo", "%d.log"))
        .isEqualTo("/foo/logs/%d.log");
  }

  @Test
  public void defaultLogPatternGivenDataDir_shouldDeterminePathFromPatternWithPath() {
    assertThat(LoggingOptions.getLogPatternGivenDataDir("/foo", "/%d.log")).isEqualTo("/%d.log");
  }
}
