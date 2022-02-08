/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.services.executionengine;

import java.util.Arrays;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel.Version;

public class ExecutionEngineConfiguration {

  private final Spec spec;
  private final Optional<String> endpoint;
  private final Version version;

  private ExecutionEngineConfiguration(
      final Spec spec, final Optional<String> endpoint, final Version version) {
    this.spec = spec;
    this.endpoint = endpoint;
    this.version = version;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return spec.isMilestoneSupported(SpecMilestone.BELLATRIX);
  }

  public Spec getSpec() {
    return spec;
  }

  public String getEndpoint() {
    return endpoint.orElseThrow(
        () ->
            new InvalidConfigurationException(
                "Invalid configuration. --Xee-endpoint parameter is mandatory when Bellatrix milestone is enabled"));
  }

  public Version getVersion() {
    return version;
  }

  public static class Builder {
    private Spec spec;
    private Optional<String> endpoint = Optional.empty();
    private Version version;

    private Builder() {}

    public ExecutionEngineConfiguration build() {

      return new ExecutionEngineConfiguration(spec, endpoint, version);
    }

    public Builder endpoint(final String endpoint) {
      this.endpoint = Optional.ofNullable(endpoint);
      return this;
    }

    public Builder version(final String version) {
      try {
        this.version = Version.valueOf(version);
        return this;
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Invalid \"--Xee-version\". Allowed values are: " + Arrays.toString(Version.values()));
      }
    }

    public Builder specProvider(final Spec spec) {
      this.spec = spec;
      return this;
    }
  }
}
