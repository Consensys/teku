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

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class SeparateServiceDataDirLayout implements DataDirLayout {

  private static final List<String> BEACON_FILES_TO_MIGRATE =
      List.of("archive", "db", "db.version", "kvstore", "metadata.yml", "network.yml");
  static final String BEACON_DATA_DIR_NAME = "beacon";
  static final String VALIDATOR_DATA_DIR_NAME = "validator";
  private final Path baseDir;
  private final Path beaconNodeDataDir;
  private final Path validatorDataDir;

  public SeparateServiceDataDirLayout(
      final Path baseDir,
      final Optional<Path> beaconDataDirectory,
      final Optional<Path> validatorDataDirectory) {
    this.baseDir = baseDir;
    beaconNodeDataDir = beaconDataDirectory.orElseGet(() -> baseDir.resolve(BEACON_DATA_DIR_NAME));
    validatorDataDir =
        validatorDataDirectory.orElseGet(() -> baseDir.resolve(VALIDATOR_DATA_DIR_NAME));
  }

  public void migrateIfNecessary() throws IOException {
    if (baseDir.resolve("db.version").toFile().exists()
        || baseDir.resolve("data").resolve("db.version").toFile().exists()) {
      performMigration();
    }
  }

  private void performMigration() throws IOException {
    STATUS_LOG.migratingDataDirectory(baseDir);
    final File dataSubdir = baseDir.resolve("data").toFile();
    File[] fileList = dataSubdir.listFiles();
    if (fileList != null) {
      for (final File file : fileList) {
        Files.move(file.toPath(), baseDir.resolve(file.getName()));
      }
    }
    if (!dataSubdir.delete() && dataSubdir.isDirectory()) {
      throw new IOException(
          "Unable to delete " + dataSubdir.getAbsolutePath() + " during migration");
    }
    mkdir(beaconNodeDataDir);
    for (final String beaconFileName : BEACON_FILES_TO_MIGRATE) {
      final Path source = baseDir.resolve(beaconFileName);
      if (source.toFile().exists()) {
        Files.move(source, beaconNodeDataDir.resolve(beaconFileName));
      }
    }

    if (baseDir.resolve("validators").toFile().exists()) {
      Files.move(baseDir.resolve("validators"), baseDir.resolve(VALIDATOR_DATA_DIR_NAME));
    }
  }

  private void mkdir(final Path path) throws IOException {
    final File file = path.toFile();
    if (!file.mkdir() && !file.isDirectory()) {
      throw new IOException("Unable to create directory " + path.toAbsolutePath());
    }
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
