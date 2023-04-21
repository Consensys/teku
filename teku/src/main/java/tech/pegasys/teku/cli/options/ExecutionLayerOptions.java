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

import static tech.pegasys.teku.config.TekuConfiguration.Builder;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.BUILDER_ALWAYS_KEYWORD;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_BID_COMPARE_FACTOR;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_CONSECUTIVE_FAULTS;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_FAULTS;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_CIRCUIT_BREAKER_ENABLED;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_CIRCUIT_BREAKER_WINDOW;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_SET_USER_AGENT_HEADER;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_EXCHANGE_CAPABILITIES_ENABLED;

import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel.Version;

public class ExecutionLayerOptions {

  @Mixin private DepositOptions depositOptions = new DepositOptions();

  @Option(
      names = {"--ee-endpoint"},
      paramLabel = "<NETWORK>",
      description = "URL for Execution Engine node.",
      arity = "1")
  private String executionEngineEndpoint = null;

  @Option(
      names = {"--Xee-version"},
      paramLabel = "<EXECUTION_ENGINE_VERSION>",
      description = "Execution Engine API version. " + "Valid values: ${COMPLETION-CANDIDATES}",
      arity = "1",
      hidden = true)
  private Version executionEngineVersion = Version.DEFAULT_VERSION;

  @Option(
      names = {"--ee-jwt-secret-file"},
      paramLabel = "<FILENAME>",
      description =
          "Location of the file specifying the hex-encoded 256 bit secret key to be used for verifying/generating jwt tokens",
      arity = "1")
  private String engineJwtSecretFile = null;

  @Option(
      names = {"--builder-endpoint"},
      paramLabel = "<NETWORK>",
      description = "URL for an external Builder node (optional).",
      showDefaultValue = Visibility.ALWAYS,
      arity = "1")
  private String builderEndpoint = null;

  @Option(
      names = {"--Xbuilder-circuit-breaker-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enables Circuit Breaker logic for builder usage.",
      arity = "1",
      showDefaultValue = Visibility.ALWAYS,
      fallbackValue = "true",
      hidden = true)
  private boolean builderCircuitBreakerEnabled = DEFAULT_BUILDER_CIRCUIT_BREAKER_ENABLED;

  @Option(
      names = {"--Xbuilder-circuit-breaker-window"},
      paramLabel = "<INTEGER>",
      description = "Circuit Breaker fault inspection window.",
      arity = "1",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private int builderCircuitBreakerWindow = DEFAULT_BUILDER_CIRCUIT_BREAKER_WINDOW;

  @Option(
      names = {"--Xbuilder-circuit-breaker-allowed-faults"},
      paramLabel = "<INTEGER>",
      description =
          "Circuit Breaker maximum allowed faults (missing block) within the specified inspection window.",
      arity = "1",
      hidden = true)
  private int builderCircuitBreakerAllowedFaults = DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_FAULTS;

  @Option(
      names = {"--Xbuilder-circuit-breaker-allowed-consecutive-faults"},
      paramLabel = "<INTEGER>",
      description = "Circuit Breaker maximum allowed consecutive faults (missing block).",
      arity = "1",
      hidden = true)
  private int builderCircuitBreakerAllowedConsecutiveFaults =
      DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_CONSECUTIVE_FAULTS;

  @Option(
      names = {"--builder-bid-compare-factor"},
      paramLabel = "<STRING>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Set the compare factor applied to the builder bid value when comparing it with locally produced payload."
              + " Factor is expressed in percentage (e.g. '100' means locally produced payload will be chosen when its value is equal or greater than the entire builder bid value, "
              + "'80' means local payload will be chosen when its value is at least 80%% of builder bid value).\n"
              + "Set it to '"
              + BUILDER_ALWAYS_KEYWORD
              + "' to always use builder bid. In this configuration locally produced payload will be used only when the bid is invalid.",
      arity = "1")
  private String builderBidCompareFactor = Integer.toString(DEFAULT_BUILDER_BID_COMPARE_FACTOR);

  @Option(
      names = {"--builder-set-user-agent-header"},
      paramLabel = "<BOOLEAN>",
      description =
          "Set User-Agent header when making a builder bid request to the builder to help builders identify clients and versions",
      arity = "1",
      showDefaultValue = Visibility.ALWAYS,
      fallbackValue = "true",
      hidden = true)
  private boolean builderSetUserAgentHeader = DEFAULT_BUILDER_SET_USER_AGENT_HEADER;

  @Option(
      hidden = true,
      names = {"--exchange-capabilities-enabled", "--Xexchange-capabilities-enabled"},
      paramLabel = "<BOOLEAN>",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables Engine API capabilities negotiation with Execution Client",
      arity = "0..1")
  private boolean exchangeCapabilitiesEnabled = DEFAULT_EXCHANGE_CAPABILITIES_ENABLED;

  public void configure(final Builder builder) {
    builder.executionLayer(
        b ->
            b.engineEndpoint(executionEngineEndpoint)
                .engineVersion(executionEngineVersion)
                .engineJwtSecretFile(engineJwtSecretFile)
                .builderEndpoint(builderEndpoint)
                .isBuilderCircuitBreakerEnabled(builderCircuitBreakerEnabled)
                .builderCircuitBreakerWindow(builderCircuitBreakerWindow)
                .builderCircuitBreakerAllowedFaults(builderCircuitBreakerAllowedFaults)
                .builderCircuitBreakerAllowedConsecutiveFaults(
                    builderCircuitBreakerAllowedConsecutiveFaults)
                .builderBidCompareFactor(builderBidCompareFactor)
                .builderSetUserAgentHeader(builderSetUserAgentHeader)
                .exchangeCapabilitiesEnabled(exchangeCapabilitiesEnabled));
    depositOptions.configure(builder);
  }
}
