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
      names = {"--beacon-node-api-endpoints", "--beacon-node-api-endpoint"},
      paramLabel = "<ENDPOINT>",
      description =
          "Beacon Node REST API endpoint(s). If more than one endpoint is defined, the first node will be used as a primary and others as failovers.",
      split = ",",
      arity = "1..*")
  private List<String> beaconNodeApiEndpoints = ValidatorConfig.DEFAULT_BEACON_NODE_API_ENDPOINTS;

  @Option(
      names = {"--Xfailovers-send-subnet-subscriptions-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Send subnet subscriptions to Beacon Nodes which are used as failovers",
      hidden = true,
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      arity = "0..1",
      fallbackValue = "true")
  private boolean failoversSendSubnetSubscriptionsEnabled =
      ValidatorConfig.DEFAULT_FAILOVERS_SEND_SUBNET_SUBSCRIPTIONS_ENABLED;

  @CommandLine.Option(
      names = {"--Xprimary-beacon-node-event-stream-reconnect-attempt-period"},
      paramLabel = "<INTEGER>",
      description =
          "How often (in milliseconds) will a reconnection to the primary Beacon Node event stream be attempted during a failover.",
      hidden = true,
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      arity = "1")
  private long primaryBeaconNodeEventStreamReconnectAttemptPeriod =
      ValidatorConfig.DEFAULT_PRIMARY_BEACON_NODE_EVENT_STREAM_RECONNECT_ATTEMPT_PERIOD.toMillis();

  @Option(
      names = {"--Xbeacon-node-ssz-blocks-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Use SSZ encoding for API block requests",
      hidden = true,
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      arity = "0..1",
      fallbackValue = "true")
  private boolean validatorClientSszBlocksEnabled = DEFAULT_VALIDATOR_CLIENT_SSZ_BLOCKS_ENABLED;

  public void configure(TekuConfiguration.Builder builder) {
    builder.validator(
        config ->
            config
                .beaconNodeApiEndpoints(getBeaconNodeApiEndpoints())
                .validatorClientUseSszBlocksEnabled(validatorClientSszBlocksEnabled)
                .failoversSendSubnetSubscriptionsEnabled(failoversSendSubnetSubscriptionsEnabled)
                .primaryBeaconNodeEventStreamReconnectAttemptPeriod(
                    Duration.ofMillis(primaryBeaconNodeEventStreamReconnectAttemptPeriod)));
  }

  public URI getPrimaryBeaconNodeApiEndpoint() {
    return getBeaconNodeApiEndpoints().get(0);
  }

  private List<URI> getBeaconNodeApiEndpoints() {
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
