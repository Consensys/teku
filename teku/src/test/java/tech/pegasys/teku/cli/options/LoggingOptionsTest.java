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
import static tech.pegasys.teku.util.config.LoggingDestination.DEFAULT_BOTH;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.util.config.LoggingDestination;
import tech.pegasys.teku.util.config.TekuConfiguration;

public class LoggingOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void loggingOptions_shouldReadFromConfigurationFile() {
    final TekuConfiguration config = getTekuConfigurationFromFile("loggingOptions_config.yaml");

    assertThat(config.getLogDestination()).isEqualTo(LoggingDestination.FILE);
    assertThat(config.isLogColorEnabled()).isFalse();
    assertThat(config.isLogIncludeEventsEnabled()).isFalse();
    assertThat(config.getLogFile()).isEqualTo("a.log");
    assertThat(config.getLogFileNamePattern()).isEqualTo("a%d.log");
    assertThat(config.isLogWireCipher()).isTrue();
    assertThat(config.isLogWirePlain()).isTrue();
    assertThat(config.isLogWireMuxFrames()).isTrue();
    assertThat(config.isLogWireGossip()).isTrue();
  }

  @Test
  public void logDestination_shouldHaveSensibleDefaultValue() {
    // This is important!
    // If it defaults to "both" or some other value custom log4j configs get overwritten
    String[] args = {"--eth1-deposit-contract-address", ETH1_ADDRESS_STRING};
    beaconNodeCommand.parse(args);

    final TekuConfiguration tekuConfiguration = getResultingTekuConfiguration();
    assertThat(tekuConfiguration.getLogDestination()).isEqualTo(DEFAULT_BOTH);
  }

  @Test
  public void logDestination_shouldAcceptFileAsDestination() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--log-destination", "file", "--eth1-deposit-contract-address", ETH1_ADDRESS_STRING);
    assertThat(tekuConfiguration.getLogDestination()).isEqualTo(LoggingDestination.FILE);
  }

  @Test
  public void includeEvents_shouldNotRequireAValue() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--log-include-events-enabled", "--eth1-deposit-contract-address", ETH1_ADDRESS_STRING);
    assertThat(tekuConfiguration.isLogIncludeEventsEnabled()).isTrue();
  }

  @Test
  public void logDestination_shouldAcceptConsoleAsDestination() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--log-destination", "console", "--eth1-deposit-contract-address", ETH1_ADDRESS_STRING);
    assertThat(tekuConfiguration.getLogDestination()).isEqualTo(LoggingDestination.CONSOLE);
  }

  @Test
  public void logDestination_shouldAcceptBothAsDestination() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--log-destination", "both", "--eth1-deposit-contract-address", ETH1_ADDRESS_STRING);
    assertThat(tekuConfiguration.getLogDestination()).isEqualTo(LoggingDestination.BOTH);
  }
}
