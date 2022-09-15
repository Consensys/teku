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

import com.google.common.io.Resources;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingDestination;

public class ValidatorClientCommandTest extends AbstractBeaconNodeCommandTest {

  @BeforeEach
  void setUp() {
    expectValidatorClient = true;
  }

  @Test
  public void networkOption_ShouldFail_IfSpecifiedOnParentCommand() {
    final String[] argsNetworkOptOnParent =
        new String[] {
          "--network", "auto", "vc",
        };
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

  @Test
  public void sentryConfigOption_shouldBuildExpectedConfigWhenOptionHasFileNameParam() {
    final String[] argsWithSentryConfig =
        new String[] {
          "vc", "--network", "minimal", "--Xsentry-config-file", "someconfig.json",
        };

    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments(argsWithSentryConfig);

    assertThat(tekuConfig.validatorClient().getValidatorConfig().getSentryNodeConfigurationFile())
        .contains("someconfig.json");
  }

  @Test
  public void sentryConfigOption_emptyConfigWhenMissingSentryConfigFileParam() {
    final String[] argsWithoutSentryConfig =
        new String[] {
          "vc", "--network", "minimal",
        };

    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments(argsWithoutSentryConfig);

    assertThat(tekuConfig.validatorClient().getValidatorConfig().getSentryNodeConfigurationFile())
        .isEmpty();
  }

  @Test
  public void sentryConfigOption_shouldFailWhenOptionIsMissingRequiredFileNameParam() {
    final String[] argsWithSentryConfigMissingParam =
        new String[] {
          "vc", "--network", "minimal", "--Xsentry-config-file",
        };

    int parseResult = beaconNodeCommand.parse(argsWithSentryConfigMissingParam);
    assertThat(parseResult).isNotZero();

    String cmdOutput = getCommandLineOutput();
    assertThat(cmdOutput).contains("Missing required parameter for option '--Xsentry-config-file'");
  }

  @Test
  public void shouldThrowErrorWhenUsingBeaconNodeEndpointAndSentryNodesConfig() {
    final String[] args = {
      "vc",
      "--network",
      "minimal",
      "--beacon-node-api-endpoint",
      "http://127.0.0.1:1234",
      "--Xsentry-config-file",
      "/tmp/foo.json"
    };

    int parseResult = beaconNodeCommand.parse(args);
    assertThat(parseResult).isNotZero();

    String cmdOutput = getCommandLineOutput();
    assertThat(cmdOutput)
        .contains("Cannot use beacon-node-api-endpoint and sentry-config-file at the same time");
  }

  @Test
  public void dutiesProviderSentryNodeEndpointIsUsedAsMainBeaconNodeApiEndpoint() {
    // From sentry_node_config.json
    final URI expectedBeaconNodeApiEndpoint = URI.create("http://duties:5051");

    final String[] args = {
      "vc", "--network", "minimal", "--Xsentry-config-file", pathFor("sentry_node_config.json")
    };

    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments(args);

    assertThat(tekuConfig.validatorClient().getValidatorConfig().getBeaconNodeApiEndpoints())
        .contains(List.of(expectedBeaconNodeApiEndpoint));
  }

  private String pathFor(final String filename) {
    return Resources.getResource(ValidatorClientCommandTest.class, filename).toString();
  }
}
