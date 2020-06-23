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

package tech.pegasys.teku.storage.server;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbConfiguration;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbDatabase;
import tech.pegasys.teku.util.config.StateStorageMode;

public class VersionedDatabaseFactory implements DatabaseFactory {
  private static final Logger LOG = LogManager.getLogger();

  @VisibleForTesting static final String DB_PATH = "db";
  @VisibleForTesting static final String ARCHIVE_PATH = "archive";
  @VisibleForTesting static final String DB_VERSION_PATH = "db.version";

  private final MetricsSystem metricsSystem;
  private final File dataDirectory;
  private final File dbDirectory;
  private final File archiveDirectory;
  private final File dbVersionFile;
  private final StateStorageMode stateStorageMode;
  private final DatabaseVersion createDatabaseVersion;

  public VersionedDatabaseFactory(
      final MetricsSystem metricsSystem,
      final String dataPath,
      final StateStorageMode dataStorageMode) {
    this(metricsSystem, dataPath, dataStorageMode, DatabaseVersion.DEFAULT_VERSION.getValue());
  }

  public VersionedDatabaseFactory(
      final MetricsSystem metricsSystem,
      final String dataPath,
      final StateStorageMode dataStorageMode,
      final String createDatabaseVersion) {
    this.metricsSystem = metricsSystem;
    this.dataDirectory = Paths.get(dataPath).toFile();
    this.dbDirectory = this.dataDirectory.toPath().resolve(DB_PATH).toFile();
    this.archiveDirectory = this.dataDirectory.toPath().resolve(ARCHIVE_PATH).toFile();
    this.dbVersionFile = this.dataDirectory.toPath().resolve(DB_VERSION_PATH).toFile();
    this.stateStorageMode = dataStorageMode;

    this.createDatabaseVersion =
        DatabaseVersion.fromString(createDatabaseVersion).orElse(DatabaseVersion.DEFAULT_VERSION);
  }

  @Override
  public Database createDatabase() {
    LOG.info("Data directory set to: {}", dataDirectory.getAbsolutePath());
    validateDataPaths();
    final DatabaseVersion dbVersion = getDatabaseVersion();
    createDirectories();
    saveDatabaseVersion(dbVersion);

    Database database;
    switch (dbVersion) {
      case V3:
        database = createV3Database();
        LOG.trace(
            "Created V3 database ({}) at {}", dbVersion.getValue(), dbDirectory.getAbsolutePath());
        break;
      case V4:
        database = createV4Database();
        LOG.trace(
            "Created V4 Hot database ({}) at {}",
            dbVersion.getValue(),
            dbDirectory.getAbsolutePath());
        LOG.trace(
            "Created V4 Finalized database ({}) at {}",
            dbVersion.getValue(),
            archiveDirectory.getAbsolutePath());
        break;
      default:
        throw new UnsupportedOperationException("Unhandled database version " + dbVersion);
    }
    return database;
  }

  private Database createV3Database() {
    final RocksDbConfiguration rocksDbConfiguration =
        RocksDbConfiguration.withDataDirectory(dbDirectory.toPath());
    return RocksDbDatabase.createV3(metricsSystem, rocksDbConfiguration, stateStorageMode);
  }

  private Database createV4Database() {
    return RocksDbDatabase.createV4(
        metricsSystem,
        RocksDbConfiguration.withDataDirectory(dbDirectory.toPath()),
        RocksDbConfiguration.withDataDirectory(archiveDirectory.toPath()),
        stateStorageMode);
  }

  private void validateDataPaths() {
    if (dbDirectory.exists() && !dbVersionFile.exists()) {
      throw new DatabaseStorageException(
          String.format(
              "No database version file was found, and the database path %s exists.",
              dataDirectory.getAbsolutePath()));
    }
  }

  private void createDirectories() {
    if (!dbDirectory.mkdirs() && !dbDirectory.isDirectory()) {
      throw new DatabaseStorageException(
          String.format(
              "Unable to create the path to store database files at %s",
              dbDirectory.getAbsolutePath()));
    }
    if (!archiveDirectory.mkdirs() && !archiveDirectory.isDirectory()) {
      throw new DatabaseStorageException(
          String.format(
              "Unable to create the path to store archive files at %s",
              archiveDirectory.getAbsolutePath()));
    }
  }

  @VisibleForTesting
  DatabaseVersion getDatabaseVersion() {
    if (dbVersionFile.exists()) {
      try {
        final String versionValue = Files.readString(dbVersionFile.toPath()).trim();
        return DatabaseVersion.fromString(versionValue)
            .orElseThrow(
                () ->
                    new DatabaseStorageException("Unrecognized database version: " + versionValue));
      } catch (IOException e) {
        throw new DatabaseStorageException(
            String.format(
                "Unable to read database version from file %s", dbVersionFile.getAbsolutePath()),
            e);
      }
    }
    return this.createDatabaseVersion;
  }

  private void saveDatabaseVersion(final DatabaseVersion version) {
    if (!dbVersionFile.exists()) {
      try {
        Files.writeString(dbVersionFile.toPath(), version.getValue(), StandardOpenOption.CREATE);
      } catch (IOException e) {
        throw new DatabaseStorageException(
            "Failed to write database version to file " + dbVersionFile.getAbsolutePath(), e);
      }
    }
  }
}
