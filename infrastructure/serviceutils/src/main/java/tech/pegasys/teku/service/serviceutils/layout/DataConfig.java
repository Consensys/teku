/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.service.serviceutils.layout;

import java.nio.file.Path;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.version.VersionProvider;

public class DataConfig {

  public static Path defaultDataPath() {
    return Path.of(VersionProvider.defaultStoragePath());
  }

  private final Path dataBasePath;
  private final Optional<Path> beaconDataPath;
  private final Optional<Path> validatorDataPath;

  private DataConfig(
      final Path dataBasePath,
      final Optional<Path> beaconDataPath,
      final Optional<Path> validatorDataPath) {
    this.dataBasePath = dataBasePath;
    this.beaconDataPath = beaconDataPath;
    this.validatorDataPath = validatorDataPath;
  }

  public static DataConfig.Builder builder() {
    return new Builder();
  }

  public Path getDataBasePath() {
    return dataBasePath;
  }

  public Optional<Path> getBeaconDataPath() {
    return beaconDataPath;
  }

  public Optional<Path> getValidatorDataPath() {
    return validatorDataPath;
  }

  public static final class Builder {

    private Path dataBasePath = defaultDataPath();
    private Optional<Path> beaconDataPath = Optional.empty();
    private Optional<Path> validatorDataPath = Optional.empty();

    private Builder() {}

    public Builder dataBasePath(Path dataBasePath) {
      this.dataBasePath = dataBasePath;
      return this;
    }

    public Builder beaconDataPath(Path beaconDataPath) {
      this.beaconDataPath = Optional.ofNullable(beaconDataPath);
      return this;
    }

    public Builder validatorDataPath(Path validatorDataPath) {
      this.validatorDataPath = Optional.ofNullable(validatorDataPath);
      return this;
    }

    public DataConfig build() {
      if (dataBasePath == null) {
        throw new InvalidConfigurationException("data-base-path must be specified");
      }
      return new DataConfig(dataBasePath, beaconDataPath, validatorDataPath);
    }
  }
}
