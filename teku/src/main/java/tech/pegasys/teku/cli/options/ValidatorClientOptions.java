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

import static tech.pegasys.teku.validator.api.ValidatorConfig.DEFAULT_VALIDATOR_CLIENT_SSZ_BLOCKS_ENABLED;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.validator.api.ValidatorConfig;

public class ValidatorClientOptions {

  @Option(
      names = {"--beacon-node-api-endpoint", "--beacon-node-api-endpoints"},
      paramLabel = "<ENDPOINT>",
      description =
          "Beacon Node REST API endpoint(s). If more than one endpoint is defined, the first node will be used as a primary and others as failovers.",
      split = ",",
      arity = "1..*")
  private List<String> beaconNodeApiEndpoints = ValidatorConfig.DEFAULT_BEACON_NODE_API_ENDPOINTS;

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

  @CommandLine.Option(
      names = {"--Xbeacon-nodes-syncing-query-period"},
      paramLabel = "<INTEGER>",
      description =
          "Period in seconds when the syncing status of the configured beacon nodes will be queried. "
              + "The result of the query would be used to determine if a validator client should send requests to a beacon node or failover immediately. "
              + "It would also be used to determine if the beacon node event stream should failover.",
      hidden = true,
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      arity = "1")
  private long beaconNodesSyncingQueryPeriod =
      ValidatorConfig.DEFAULT_BEACON_NODES_SYNCING_QUERY_PERIOD.toSeconds();

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
      names = {"--Xsentry-config-file"},
      paramLabel = "<FILE>",
      description = "Config file with sentry node configuration",
      hidden = true,
      arity = "1")
  private String sentryConfigFile = null;

  public void configure(TekuConfiguration.Builder builder) {
    builder.validator(
        config ->
            config
                .beaconNodeApiEndpoints(getBeaconNodeApiEndpoints())
                .validatorClientUseSszBlocksEnabled(validatorClientSszBlocksEnabled)
                .failoversSendSubnetSubscriptionsEnabled(failoversSendSubnetSubscriptionsEnabled)
                .beaconNodesSyncingQueryPeriod(Duration.ofSeconds(beaconNodesSyncingQueryPeriod))
                .sentryNodeConfigurationFile(sentryConfigFile));
  }

  public List<URI> getBeaconNodeApiEndpoints() {
    return beaconNodeApiEndpoints.stream()
        .map(this::parseBeaconNodeApiEndpoint)
        .collect(Collectors.toList());
  }

  private URI parseBeaconNodeApiEndpoint(final String apiEndpoint) {
    try {
      return new URI(apiEndpoint);
    } catch (URISyntaxException e) {
      throw new InvalidConfigurationException(
          "Invalid configuration. Beacon node API endpoint is not a valid URL: " + apiEndpoint, e);
    }
  }
}
