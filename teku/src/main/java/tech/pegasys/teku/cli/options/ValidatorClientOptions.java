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
import java.util.List;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.validator.api.ValidatorConfig;

public class ValidatorClientOptions {

  @Option(
      names = {"--beacon-node-api-endpoint"},
      paramLabel = "<ENDPOINT>",
      description = "Endpoint of the Beacon Node REST API",
      arity = "1")
  private String beaconNodeApiEndpoint = ValidatorConfig.DEFAULT_BEACON_NODE_API_ENDPOINT;

  @Option(
      names = {"--Xbeacon-node-api-endpoints"},
      paramLabel = "<ENDPOINT>",
      description =
          "Beacon Node API endpoint(s). If more than one is defined, the first will be used as a primary node and other(s) will be used as failovers.",
      split = ",",
      hidden = true,
      arity = "1..*")
  private List<String> beaconNodeApiEndpoints = ValidatorConfig.DEFAULT_BEACON_NODE_API_ENDPOINTS;

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
                .beaconNodeApiEndpoint(parseBeaconNodeApiEndpoint())
                .beaconNodeApiEndpoints(parseBeaconNodeApiEndpoints())
                .validatorClientUseSszBlocksEnabled(validatorClientSszBlocksEnabled));
  }

  public URI parseBeaconNodeApiEndpoint() {
    return parseBeaconNodeApiEndpoint(beaconNodeApiEndpoint);
  }

  private List<URI> parseBeaconNodeApiEndpoints() {
    return beaconNodeApiEndpoints.stream()
        .map(this::parseBeaconNodeApiEndpoint)
        .collect(Collectors.toList());
  }

  private URI parseBeaconNodeApiEndpoint(final String beaconNodeApiEndpoint) {
    try {
      return new URI(beaconNodeApiEndpoint);
    } catch (URISyntaxException e) {
      throw new InvalidConfigurationException(
          "Invalid configuration. Beacon node API endpoint is not a valid URL: "
              + beaconNodeApiEndpoint,
          e);
    }
  }
}
