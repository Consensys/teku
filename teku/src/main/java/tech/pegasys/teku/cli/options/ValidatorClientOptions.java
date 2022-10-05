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

import static tech.pegasys.teku.validator.api.ValidatorConfig.DEFAULT_DOPPELGANGER_DETECTION_ENABLED;
import static tech.pegasys.teku.validator.api.ValidatorConfig.DEFAULT_VALIDATOR_CLIENT_SSZ_BLOCKS_ENABLED;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.remote.sentry.SentryNodesConfigLoader;

public class ValidatorClientOptions {

  @ArgGroup(multiplicity = "0..1")
  private ExclusiveParams exclusiveParams = new ExclusiveParams();

  @Option(
      names = {"--Xfailovers-send-subnet-subscriptions-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Send subnet subscriptions to beacon nodes which are used as failovers",
      hidden = true,
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      arity = "0..1",
      fallbackValue = "true")
  private boolean failoversSendSubnetSubscriptionsEnabled =
      ValidatorConfig.DEFAULT_FAILOVERS_SEND_SUBNET_SUBSCRIPTIONS_ENABLED;

  @Option(
      names = {"--Xbeacon-node-ssz-blocks-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Use SSZ encoding for API block requests",
      hidden = true,
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      arity = "0..1",
      fallbackValue = "true")
  private boolean validatorClientSszBlocksEnabled = DEFAULT_VALIDATOR_CLIENT_SSZ_BLOCKS_ENABLED;

  @Option(
      names = {"--Xdoppelganger-detection-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enable validators doppelganger detection",
      hidden = true,
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      arity = "0..1",
      fallbackValue = "true")
  private boolean doppelgangerDetectionEnabled = DEFAULT_DOPPELGANGER_DETECTION_ENABLED;

  public void configure(TekuConfiguration.Builder builder) {
    configureBeaconNodeApiEndpoints();

    builder.validator(
        config ->
            config
                .beaconNodeApiEndpoints(getBeaconNodeApiEndpoints())
                .validatorClientUseSszBlocksEnabled(validatorClientSszBlocksEnabled)
                .doppelgangerDetectionEnabled(doppelgangerDetectionEnabled)
                .failoversSendSubnetSubscriptionsEnabled(failoversSendSubnetSubscriptionsEnabled)
                .sentryNodeConfigurationFile(exclusiveParams.sentryConfigFile));
  }

  private void configureBeaconNodeApiEndpoints() {
    if (exclusiveParams.beaconNodeApiEndpoints == null) {
      if (exclusiveParams.sentryConfigFile == null) {
        exclusiveParams.beaconNodeApiEndpoints = ValidatorConfig.DEFAULT_BEACON_NODE_API_ENDPOINTS;
      } else {
        exclusiveParams.beaconNodeApiEndpoints =
            parseBeaconNodeApiEndpoints(
                new SentryNodesConfigLoader()
                    .load(exclusiveParams.sentryConfigFile)
                    .getBeaconNodesSentryConfig()
                    .getDutiesProviderNodeConfig()
                    .getEndpoints());
      }
    }
  }

  public List<URI> getBeaconNodeApiEndpoints() {
    configureBeaconNodeApiEndpoints();
    return exclusiveParams.beaconNodeApiEndpoints;
  }

  private List<URI> parseBeaconNodeApiEndpoints(final List<String> apiEndpoints) {
    return apiEndpoints.stream()
        .map(
            endpoint -> {
              try {
                return new URI(endpoint);
              } catch (URISyntaxException e) {
                throw new InvalidConfigurationException(
                    "Invalid configuration. Beacon node API endpoint is not a valid URL: "
                        + endpoint,
                    e);
              }
            })
        .collect(Collectors.toList());
  }

  private static class ExclusiveParams {

    @Option(
        names = {"--beacon-node-api-endpoint", "--beacon-node-api-endpoints"},
        paramLabel = "<ENDPOINT>",
        description =
            "Beacon Node REST API endpoint(s). If more than one endpoint is defined, the first node"
                + " will be used as a primary and others as failovers.",
        split = ",",
        arity = "1..*")
    List<URI> beaconNodeApiEndpoints;

    @Option(
        names = {"--Xsentry-config-file"},
        paramLabel = "<FILE>",
        description = "Config file with sentry node configuration",
        hidden = true,
        arity = "1")
    String sentryConfigFile = null;
  }
}
