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

package tech.pegasys.teku.cli.subcommand;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingDestination;

public class ValidatorClientCommandTest extends AbstractBeaconNodeCommandTest {
  private final String[] argsNetworkOptOnParent =
      new String[] {
        "--network", "auto", "vc",
      };

  @BeforeEach
  void setUp() {
    expectValidatorClient = true;
  }

  @Test
  public void networkOption_ShouldFail_IfSpecifiedOnParentCommand() {
    int parseResult = beaconNodeCommand.parse(argsNetworkOptOnParent);
    assertThat(parseResult).isEqualTo(1);
    String cmdOutput = getCommandLineOutput();
    assertThat(cmdOutput)
        .contains("--network option should not be specified before the validator-client command");
  }

  @Test
  void loggingOptions_shouldUseLoggingOptionsFromBeforeSubcommand() {
    final LoggingConfig config =
        getLoggingConfigurationFromArguments(
            "--log-destination=console", "vc", "--network=mainnet");
    assertThat(config.getDestination()).isEqualTo(LoggingDestination.CONSOLE);
  }

  @Test
  void loggingOptions_shouldUseLoggingOptionsFromAfterSubcommand() {
    final LoggingConfig config =
        getLoggingConfigurationFromArguments(
            "vc", "--network=mainnet", "--log-destination=console");
    assertThat(config.getDestination()).isEqualTo(LoggingDestination.CONSOLE);
  }

  @Test
  public void shouldIgnoreNonMatchingSubcommandParams() {
    final TekuConfiguration configuration =
        getTekuConfigurationFromFile(
            "beaconAndValidatorOptions_config.yaml", Optional.of("validator-client"));
    assertThat(configuration.validatorClient().getValidatorConfig().getValidatorKeys())
        .containsExactly("a.key:a.password", "b.json:b.txt");
  }
}
