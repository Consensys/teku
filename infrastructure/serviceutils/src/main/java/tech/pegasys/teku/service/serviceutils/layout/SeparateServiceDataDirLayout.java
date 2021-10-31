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

public class SeparateServiceDataDirLayout implements DataDirLayout {
  static final String BEACON_DATA_DIR_NAME = "beacon";
  static final String VALIDATOR_DATA_DIR_NAME = "validator";
  private final Path beaconNodeDataDir;
  private final Path validatorDataDir;

  public SeparateServiceDataDirLayout(
      final Path baseDir,
      final Optional<Path> beaconDataDirectory,
      final Optional<Path> validatorDataDirectory) {
    beaconNodeDataDir = beaconDataDirectory.orElseGet(() -> baseDir.resolve(BEACON_DATA_DIR_NAME));
    validatorDataDir =
        validatorDataDirectory.orElseGet(() -> baseDir.resolve(VALIDATOR_DATA_DIR_NAME));
  }

  @Override
  public Path getBeaconDataDirectory() {
    return beaconNodeDataDir;
  }

  @Override
  public Path getValidatorDataDirectory() {
    return validatorDataDir;
  }
}
