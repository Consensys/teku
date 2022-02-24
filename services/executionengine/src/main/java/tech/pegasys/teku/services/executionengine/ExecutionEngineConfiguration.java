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

import java.net.URL;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel.Version;

public class ExecutionEngineConfiguration {

  private final Spec spec;
  private final Optional<String> endpoint;
  private final Version version;
  private final Optional<String> jwtSecretFile;
  private final List<URL> mevBoostUrls;

  private ExecutionEngineConfiguration(
      final Spec spec,
      final Optional<String> endpoint,
      final Version version,
      final Optional<String> jwtSecretFile,
      final List<URL> mevBoostUrls) {
    this.spec = spec;
    this.endpoint = endpoint;
    this.version = version;
    this.jwtSecretFile = jwtSecretFile;
    this.mevBoostUrls = mevBoostUrls;
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

  public Optional<String> getJwtSecretFile() {
    return jwtSecretFile;
  }

  public Version getVersion() {
    return version;
  }

  public List<URL> getMevBoostUrls() {
    return mevBoostUrls;
  }

  public static class Builder {
    private Spec spec;
    private Optional<String> endpoint = Optional.empty();
    private Version version = Version.DEFAULT_VERSION;
    private Optional<String> jwtSecretFile = Optional.empty();
    private List<URL> mevBoostUrls = List.of();

    private Builder() {}

    public ExecutionEngineConfiguration build() {

      return new ExecutionEngineConfiguration(spec, endpoint, version, jwtSecretFile, mevBoostUrls);
    }

    public Builder endpoint(final String endpoint) {
      this.endpoint = Optional.ofNullable(endpoint);
      return this;
    }

    public Builder version(final Version version) {
      this.version = version;
      return this;
    }

    public Builder specProvider(final Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder jwtSecretFile(final String jwtSecretFile) {
      this.jwtSecretFile = Optional.ofNullable(jwtSecretFile).filter(StringUtils::isNotBlank);
      return this;
    }

    public Builder mevBoostUrls(final List<URL> mevBoostUrls) {
      this.mevBoostUrls = mevBoostUrls;
      return this;
    }
  }
}
