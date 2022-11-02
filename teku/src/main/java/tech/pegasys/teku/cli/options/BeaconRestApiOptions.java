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

import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.config.TekuConfiguration;

public class BeaconRestApiOptions {

  @CommandLine.Spec CommandLine.Model.CommandSpec cliSpec;

  private int maxUrlLength = BeaconRestApiConfig.DEFAULT_MAX_URL_LENGTH;

  @Option(
      names = {"--rest-api-port"},
      paramLabel = "<INTEGER>",
      description = "Port number of Beacon Rest API",
      arity = "1")
  private int restApiPort = BeaconRestApiConfig.DEFAULT_REST_API_PORT;

  @Option(
      names = {"--rest-api-docs-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enable swagger-docs and swagger-ui endpoints",
      fallbackValue = "true",
      arity = "0..1")
  private boolean restApiDocsEnabled = false;

  @Option(
      names = {"--rest-api-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables Beacon Rest API",
      fallbackValue = "true",
      arity = "0..1")
  private boolean restApiEnabled = false;

  @Option(
      names = {"--rest-api-interface"},
      paramLabel = "<NETWORK>",
      description = "Interface of Beacon Rest API",
      arity = "1")
  private String restApiInterface = BeaconRestApiConfig.DEFAULT_REST_API_INTERFACE;

  @Option(
      names = {"--rest-api-host-allowlist"},
      paramLabel = "<hostname>",
      description = "Comma-separated list of hostnames to allow, or * to allow any host",
      split = ",",
      arity = "0..*")
  private final List<String> restApiHostAllowlist =
      BeaconRestApiConfig.DEFAULT_REST_API_HOST_ALLOWLIST;

  @Option(
      names = {"--rest-api-cors-origins"},
      paramLabel = "<origin>",
      description = "Comma separated list of origins to allow, or * to allow any origin",
      split = ",",
      arity = "0..*")
  private final List<String> restApiCorsAllowedOrigins =
      BeaconRestApiConfig.DEFAULT_REST_API_CORS_ALLOWED_ORIGINS;

  @Option(
      names = {"--Xrest-api-max-pending-events"},
      paramLabel = "<INTEGER>",
      hidden = true)
  private int maxPendingEvents = BeaconRestApiConfig.DEFAULT_MAX_EVENT_QUEUE_SIZE;

  @Option(
      names = {"--Xrest-api-max-url-length"},
      description = "Set the maximum url length for rest api requests",
      paramLabel = "<INTEGER>",
      defaultValue = "65535",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  public void setMaxUrlLength(int maxUrlLength) {
    if (maxUrlLength < 4096 || maxUrlLength > 1052672) {
      throw new CommandLine.ParameterException(
          cliSpec.commandLine(),
          String.format(
              "Invalid value '%s' for option '--Xrest-api-max-url-length': "
                  + "value outside of the expected range (min: 4096, max: 1052672)",
              maxUrlLength));
    }
    this.maxUrlLength = maxUrlLength;
  }

  // beacon-liveness-tracking-enabled
  @Option(
      names = {"--Xbeacon-liveness-tracking-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Track validator liveness and enable requests to the liveness rest api.",
      arity = "0..1",
      fallbackValue = "true",
      hidden = true)
  private Boolean beaconLivenessTrackingEnabled =
      BeaconRestApiConfig.DEFAULT_BEACON_LIVENESS_TRACKING_ENABLED;

  @Option(
      names = {"--Xrest-api-validator-threads"},
      description = "Set the number of threads used to handle validator api requests",
      paramLabel = "<INTEGER>",
      hidden = true)
  private int validatorThreads = BeaconRestApiConfig.DEFAULT_SUBSCRIBE_THREADS_COUNT;

  public void configure(final TekuConfiguration.Builder builder) {
    builder.restApi(
        restApiBuilder ->
            restApiBuilder
                .restApiEnabled(restApiEnabled)
                .restApiDocsEnabled(restApiDocsEnabled)
                .restApiPort(restApiPort)
                .restApiInterface(restApiInterface)
                .restApiHostAllowlist(restApiHostAllowlist)
                .restApiCorsAllowedOrigins(restApiCorsAllowedOrigins)
                .maxUrlLength(maxUrlLength)
                .beaconLivenessTrackingEnabled(beaconLivenessTrackingEnabled)
                .maxPendingEvents(maxPendingEvents)
                .validatorThreads(validatorThreads));
  }
}
