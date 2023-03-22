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

package tech.pegasys.teku.services.executionlayer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel.STUB_ENDPOINT_PREFIX;

import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel.Version;

public class ExecutionLayerConfiguration {
  private static final Logger LOG = LogManager.getLogger();

  public static final boolean DEFAULT_BUILDER_CIRCUIT_BREAKER_ENABLED = true;
  public static final int DEFAULT_BUILDER_CIRCUIT_BREAKER_WINDOW = 32;
  public static final int DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_FAULTS = 5;
  public static final int DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_CONSECUTIVE_FAULTS = 3;
  public static final int BUILDER_CIRCUIT_BREAKER_WINDOW_HARD_CAP = 64;
  public static final int DEFAULT_BUILDER_BID_COMPARE_FACTOR = 100;
  public static final String BUILDER_ALWAYS_KEYWORD = "BUILDER_ALWAYS";

  private final Spec spec;
  private final Optional<String> engineEndpoint;
  private final Version engineVersion;
  private final Optional<String> engineJwtSecretFile;
  private final Optional<String> builderEndpoint;
  private final boolean isBuilderCircuitBreakerEnabled;
  private final int builderCircuitBreakerWindow;
  private final int builderCircuitBreakerAllowedFaults;
  private final int builderCircuitBreakerAllowedConsecutiveFaults;
  private final Optional<Integer> builderBidCompareFactor;
  private final boolean exchangeCapabilitiesEnabled;

  private ExecutionLayerConfiguration(
      final Spec spec,
      final Optional<String> engineEndpoint,
      final Version engineVersion,
      final Optional<String> engineJwtSecretFile,
      final Optional<String> builderEndpoint,
      final boolean isBuilderCircuitBreakerEnabled,
      final int builderCircuitBreakerWindow,
      final int builderCircuitBreakerAllowedFaults,
      final int builderCircuitBreakerAllowedConsecutiveFaults,
      final Optional<Integer> builderBidCompareFactor,
      final boolean exchangeCapabilitiesEnabled) {
    this.spec = spec;
    this.engineEndpoint = engineEndpoint;
    this.engineVersion = engineVersion;
    this.engineJwtSecretFile = engineJwtSecretFile;
    this.builderEndpoint = builderEndpoint;
    this.isBuilderCircuitBreakerEnabled = isBuilderCircuitBreakerEnabled;
    this.builderCircuitBreakerWindow = builderCircuitBreakerWindow;
    this.builderCircuitBreakerAllowedFaults = builderCircuitBreakerAllowedFaults;
    this.builderCircuitBreakerAllowedConsecutiveFaults =
        builderCircuitBreakerAllowedConsecutiveFaults;
    this.builderBidCompareFactor = builderBidCompareFactor;
    this.exchangeCapabilitiesEnabled = exchangeCapabilitiesEnabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return engineEndpoint.isPresent() || spec.isMilestoneSupported(SpecMilestone.BELLATRIX);
  }

  public Spec getSpec() {
    return spec;
  }

  public String getEngineEndpoint() {
    return engineEndpoint.orElseThrow(
        () ->
            new InvalidConfigurationException(
                "Invalid configuration. --ee-endpoint parameter is mandatory when Bellatrix milestone is enabled"));
  }

  public Optional<String> getEngineJwtSecretFile() {
    return engineJwtSecretFile;
  }

  public Version getEngineVersion() {
    return engineVersion;
  }

  public Optional<String> getBuilderEndpoint() {
    return builderEndpoint;
  }

  public boolean isBuilderCircuitBreakerEnabled() {
    return isBuilderCircuitBreakerEnabled;
  }

  public int getBuilderCircuitBreakerWindow() {
    return builderCircuitBreakerWindow;
  }

  public int getBuilderCircuitBreakerAllowedFaults() {
    return builderCircuitBreakerAllowedFaults;
  }

  public int getBuilderCircuitBreakerAllowedConsecutiveFaults() {
    return builderCircuitBreakerAllowedConsecutiveFaults;
  }

  public boolean isExchangeCapabilitiesEnabled() {
    return exchangeCapabilitiesEnabled;
  }

  public Optional<Integer> getBuilderBidCompareFactor() {
    return builderBidCompareFactor;
  }

  public static class Builder {
    private Spec spec;
    private Optional<String> engineEndpoint = Optional.empty();
    private Version engineVersion = Version.DEFAULT_VERSION;
    private Optional<String> engineJwtSecretFile = Optional.empty();
    private Optional<String> builderEndpoint = Optional.empty();
    private boolean isBuilderCircuitBreakerEnabled = DEFAULT_BUILDER_CIRCUIT_BREAKER_ENABLED;
    private int builderCircuitBreakerWindow = DEFAULT_BUILDER_CIRCUIT_BREAKER_WINDOW;
    private int builderCircuitBreakerAllowedFaults = DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_FAULTS;
    private int builderCircuitBreakerAllowedConsecutiveFaults =
        DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_CONSECUTIVE_FAULTS;
    private String builderBidCompareFactor = Integer.toString(DEFAULT_BUILDER_BID_COMPARE_FACTOR);
    private boolean exchangeCapabilitiesEnabled = false;

    private Builder() {}

    public ExecutionLayerConfiguration build() {
      validateStubEndpoints();
      validateBuilderCircuitBreaker();
      final Optional<Integer> builderBidCompareFactor = validateAndParseBuilderBidCompareFactor();

      if (builderEndpoint.isPresent()) {
        if (builderBidCompareFactor.isEmpty()) {
          LOG.info(
              "During block production, a valid builder bid will always be chosen over locally produced payload.");
        } else {
          final String additionalHint =
              builderBidCompareFactor.get() == DEFAULT_BUILDER_BID_COMPARE_FACTOR
                  ? " Configure with --builder-bid-compare-factor"
                  : "";
          LOG.info(
              "During block production, locally produced payload will be chosen when its value is equal or greater than {}% of the builder bid value."
                  + additionalHint,
              builderBidCompareFactor);
        }
      }

      return new ExecutionLayerConfiguration(
          spec,
          engineEndpoint,
          engineVersion,
          engineJwtSecretFile,
          builderEndpoint,
          isBuilderCircuitBreakerEnabled,
          builderCircuitBreakerWindow,
          builderCircuitBreakerAllowedFaults,
          builderCircuitBreakerAllowedConsecutiveFaults,
          builderBidCompareFactor,
          exchangeCapabilitiesEnabled);
    }

    public Builder engineEndpoint(final String engineEndpoint) {
      this.engineEndpoint = Optional.ofNullable(engineEndpoint);
      return this;
    }

    public Builder engineVersion(final Version version) {
      this.engineVersion = version;
      return this;
    }

    public Builder specProvider(final Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder engineJwtSecretFile(final String jwtSecretFile) {
      this.engineJwtSecretFile = Optional.ofNullable(jwtSecretFile).filter(StringUtils::isNotBlank);
      return this;
    }

    public Builder isBuilderCircuitBreakerEnabled(final boolean isBuilderCircuitBreakerEnabled) {
      this.isBuilderCircuitBreakerEnabled = isBuilderCircuitBreakerEnabled;
      return this;
    }

    public Builder builderCircuitBreakerWindow(final int builderCircuitBreakerWindow) {
      this.builderCircuitBreakerWindow = builderCircuitBreakerWindow;
      return this;
    }

    public Builder builderCircuitBreakerAllowedFaults(
        final int builderCircuitBreakerAllowedFaults) {
      this.builderCircuitBreakerAllowedFaults = builderCircuitBreakerAllowedFaults;
      return this;
    }

    public Builder builderCircuitBreakerAllowedConsecutiveFaults(
        final int builderCircuitBreakerAllowedConsecutiveFaults) {
      this.builderCircuitBreakerAllowedConsecutiveFaults =
          builderCircuitBreakerAllowedConsecutiveFaults;
      return this;
    }

    public Builder builderEndpoint(final String builderEndpoint) {
      this.builderEndpoint = Optional.ofNullable(builderEndpoint);
      return this;
    }

    public Builder builderBidCompareFactor(final String builderBidCompareFactor) {
      this.builderBidCompareFactor = builderBidCompareFactor;
      return this;
    }

    public Builder exchangeCapabilitiesEnabled(boolean enabled) {
      this.exchangeCapabilitiesEnabled = enabled;
      return this;
    }

    private void validateStubEndpoints() {
      final boolean engineIsStub =
          engineEndpoint.map(endpoint -> endpoint.equals(STUB_ENDPOINT_PREFIX)).orElse(false);
      final boolean builderIsStub =
          builderEndpoint.map(endpoint -> endpoint.equals(STUB_ENDPOINT_PREFIX)).orElse(false);

      checkState(
          engineIsStub == builderIsStub || builderEndpoint.isEmpty(),
          "mixed configuration with stubbed and non-stubbed execution layer endpoints is not supported");
    }

    private void validateBuilderCircuitBreaker() {
      if (builderCircuitBreakerWindow > BUILDER_CIRCUIT_BREAKER_WINDOW_HARD_CAP) {
        throw new InvalidConfigurationException(
            "Builder Circuit Breaker window cannot exceed "
                + BUILDER_CIRCUIT_BREAKER_WINDOW_HARD_CAP);
      }
    }

    private Optional<Integer> validateAndParseBuilderBidCompareFactor() {
      if (builderBidCompareFactor.toUpperCase(Locale.ROOT).equals(BUILDER_ALWAYS_KEYWORD)) {
        return Optional.empty();
      }
      if (builderBidCompareFactor.endsWith("%")) {
        builderBidCompareFactor =
            builderBidCompareFactor.substring(0, builderBidCompareFactor.length() - 1);
      }
      final int builderBidCompareFactorInt;
      try {
        builderBidCompareFactorInt = Integer.parseInt(builderBidCompareFactor);
      } catch (final NumberFormatException ex) {
        throw new InvalidConfigurationException(
            "Expecting number, percentage or "
                + BUILDER_ALWAYS_KEYWORD
                + "+ keyword for Builder bid compare factor");
      }
      checkArgument(
          builderBidCompareFactorInt >= 0, "Builder bid compare factor percentage should be >= 0");
      return Optional.of(builderBidCompareFactorInt);
    }
  }
}
