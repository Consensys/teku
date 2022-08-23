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

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel.STUB_ENDPOINT_PREFIX;

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel.Version;

public class ExecutionLayerConfiguration {
  public static final boolean DEFAULT_BUILDER_CIRCUIT_BREAKER_ENABLED = true;
  public static final int DEFAULT_BUILDER_CIRCUIT_BREAKER_WINDOW = 32;
  public static final int DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_FAULTS = 8;

  public static final int BUILDER_CIRCUIT_BREAKER_WINDOW_HARD_CAP = 64;

  private final Spec spec;
  private final Optional<String> engineEndpoint;
  private final Version engineVersion;
  private final Optional<String> engineJwtSecretFile;
  private final Optional<String> builderEndpoint;
  boolean isBuilderCircuitBreakerEnabled;
  int builderCircuitBreakerWindow;
  int builderCircuitBreakerAllowedFaults;

  private ExecutionLayerConfiguration(
      final Spec spec,
      final Optional<String> engineEndpoint,
      final Version engineVersion,
      final Optional<String> engineJwtSecretFile,
      final Optional<String> builderEndpoint,
      final boolean isBuilderCircuitBreakerEnabled,
      final int builderCircuitBreakerWindow,
      final int builderCircuitBreakerAllowedFaults) {
    this.spec = spec;
    this.engineEndpoint = engineEndpoint;
    this.engineVersion = engineVersion;
    this.engineJwtSecretFile = engineJwtSecretFile;
    this.builderEndpoint = builderEndpoint;
    this.isBuilderCircuitBreakerEnabled = isBuilderCircuitBreakerEnabled;
    this.builderCircuitBreakerWindow = builderCircuitBreakerWindow;
    this.builderCircuitBreakerAllowedFaults = builderCircuitBreakerAllowedFaults;
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

  public static class Builder {
    private Spec spec;
    private Optional<String> engineEndpoint = Optional.empty();
    private Version engineVersion = Version.DEFAULT_VERSION;
    private Optional<String> engineJwtSecretFile = Optional.empty();
    private Optional<String> builderEndpoint = Optional.empty();
    private boolean isBuilderCircuitBreakerEnabled = DEFAULT_BUILDER_CIRCUIT_BREAKER_ENABLED;
    private int builderCircuitBreakerWindow = DEFAULT_BUILDER_CIRCUIT_BREAKER_WINDOW;
    private int builderCircuitBreakerAllowedFaults = DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_FAULTS;

    private Builder() {}

    public ExecutionLayerConfiguration build() {
      validateStubEndpoints();
      validateBuilderCircuitBreaker();
      return new ExecutionLayerConfiguration(
          spec,
          engineEndpoint,
          engineVersion,
          engineJwtSecretFile,
          builderEndpoint,
          isBuilderCircuitBreakerEnabled,
          builderCircuitBreakerWindow,
          builderCircuitBreakerAllowedFaults);
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

    public Builder builderEndpoint(final String builderEndpoint) {
      this.builderEndpoint = Optional.ofNullable(builderEndpoint);
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
  }
}
