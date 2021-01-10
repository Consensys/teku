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
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.storage.server.metadata.V5DatabaseMetadata;
import tech.pegasys.teku.storage.server.metadata.V6DatabaseMetadata;
import tech.pegasys.teku.storage.server.network.DatabaseNetwork;
import tech.pegasys.teku.storage.server.noop.NoOpDatabase;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbConfiguration;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbDatabase;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaHot;
import tech.pegasys.teku.storage.server.rocksdb.schema.V6SchemaFinalized;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.StateStorageMode;

public class VersionedDatabaseFactory implements DatabaseFactory {
  private static final Logger LOG = LogManager.getLogger();

  public static final long DEFAULT_STORAGE_FREQUENCY = 2048L;
  @VisibleForTesting static final String DB_PATH = "db";
  @VisibleForTesting static final String ARCHIVE_PATH = "archive";
  @VisibleForTesting static final String DB_VERSION_PATH = "db.version";
  @VisibleForTesting static final String METADATA_FILENAME = "metadata.yml";
  @VisibleForTesting static final String NETWORK_FILENAME = "network.yml";

  private final MetricsSystem metricsSystem;
  private final File dataDirectory;
  private final File dbDirectory;
  private final File v5ArchiveDirectory;
  private final Optional<File> v6ArchiveDirectory;
  private final File dbVersionFile;
  private final StateStorageMode stateStorageMode;
  private final DatabaseVersion createDatabaseVersion;
  private final long stateStorageFrequency;
  private final Optional<Eth1Address> eth1Address;

  public VersionedDatabaseFactory(
      final MetricsSystem metricsSystem,
      final Path dataPath,
      final StateStorageMode dataStorageMode,
      final Optional<Eth1Address> depositContractAddress) {
    this(
        metricsSystem,
        dataPath,
        Optional.empty(),
        dataStorageMode,
        DatabaseVersion.DEFAULT_VERSION,
        DEFAULT_STORAGE_FREQUENCY,
        depositContractAddress);
  }

  public VersionedDatabaseFactory(
      final MetricsSystem metricsSystem,
      final Path dataPath,
      final StateStorageMode dataStorageMode,
      final DatabaseVersion createDatabaseVersion,
      final long stateStorageFrequency,
      final Optional<Eth1Address> eth1Address) {
    this(
        metricsSystem,
        dataPath,
        Optional.empty(),
        dataStorageMode,
        createDatabaseVersion,
        stateStorageFrequency,
        eth1Address);
  }

  public VersionedDatabaseFactory(
      final MetricsSystem metricsSystem,
      final Path dataPath,
      final Optional<Path> maybeArchiveDataPath,
      final StateStorageMode dataStorageMode,
      final DatabaseVersion createDatabaseVersion,
      final long stateStorageFrequency,
      final Optional<Eth1Address> eth1Address) {
    this.metricsSystem = metricsSystem;
    this.dataDirectory = dataPath.toFile();
    this.dbDirectory = this.dataDirectory.toPath().resolve(DB_PATH).toFile();
    this.v5ArchiveDirectory = this.dataDirectory.toPath().resolve(ARCHIVE_PATH).toFile();
    this.v6ArchiveDirectory = maybeArchiveDataPath.map(p -> p.resolve(ARCHIVE_PATH).toFile());
    this.dbVersionFile = this.dataDirectory.toPath().resolve(DB_VERSION_PATH).toFile();
    this.stateStorageMode = dataStorageMode;
    this.stateStorageFrequency = stateStorageFrequency;
    this.eth1Address = eth1Address;

    this.createDatabaseVersion = createDatabaseVersion;
  }

  @Override
  public Database createDatabase() {
    LOG.info("Beacon data directory set to: {}", dataDirectory.getAbsolutePath());
    validateDataPaths();
    final DatabaseVersion dbVersion = getDatabaseVersion();
    createDirectories(dbVersion);
    saveDatabaseVersion(dbVersion);

    Database database;
    switch (dbVersion) {
      case NOOP:
        database = new NoOpDatabase();
        LOG.info("Created no-op database");
        break;
      case V4:
        database = createV4Database();
        LOG.info(
            "Created V4 Hot database ({}) at {}",
            dbVersion.getValue(),
            dbDirectory.getAbsolutePath());
        LOG.info(
            "Created V4 Finalized database ({}) at {}",
            dbVersion.getValue(),
            v5ArchiveDirectory.getAbsolutePath());
        break;
      case V5:
        database = createV5Database();
        LOG.info(
            "Created V5 Hot database ({}) at {}",
            dbVersion.getValue(),
            dbDirectory.getAbsolutePath());
        LOG.info(
            "Created V5 Finalized database ({}) at {}",
            dbVersion.getValue(),
            v5ArchiveDirectory.getAbsolutePath());
        break;
      case V6:
        database = createV6Database();
        if (v6ArchiveDirectory.isPresent()) {
          LOG.info(
              "Created V6 Hot database ({}) at {}",
              dbVersion.getValue(),
              dbDirectory.getAbsolutePath());
          LOG.info(
              "Created V6 Finalized database ({}) at {}",
              dbVersion.getValue(),
              v6ArchiveDirectory.get().getAbsolutePath());
        } else {
          LOG.info(
              "Created V6 Hot and Finalized database ({}) at {}",
              dbVersion.getValue(),
              dbDirectory.getAbsolutePath());
        }
        break;
      default:
        throw new UnsupportedOperationException("Unhandled database version " + dbVersion);
    }
    return database;
  }

  private Database createV4Database() {
    try {
      DatabaseNetwork.init(getNetworkFile(), Constants.GENESIS_FORK_VERSION, eth1Address);
      return RocksDbDatabase.createV4(
          metricsSystem,
          RocksDbConfiguration.v4Settings(dbDirectory.toPath()),
          RocksDbConfiguration.v4Settings(v5ArchiveDirectory.toPath()),
          stateStorageMode,
          stateStorageFrequency);
    } catch (final IOException e) {
      throw new DatabaseStorageException("Failed to read configuration file", e);
    }
  }

  /**
   * V5 database is identical to V4 except for the RocksDB configuration
   *
   * @return the created database
   */
  private Database createV5Database() {
    try {
      final V5DatabaseMetadata metaData =
          V5DatabaseMetadata.init(getMetadataFile(), V5DatabaseMetadata.v5Defaults());
      DatabaseNetwork.init(getNetworkFile(), Constants.GENESIS_FORK_VERSION, eth1Address);
      return RocksDbDatabase.createV4(
          metricsSystem,
          metaData.getHotDbConfiguration().withDatabaseDir(dbDirectory.toPath()),
          metaData.getArchiveDbConfiguration().withDatabaseDir(v5ArchiveDirectory.toPath()),
          stateStorageMode,
          stateStorageFrequency);
    } catch (final IOException e) {
      throw new DatabaseStorageException("Failed to read metadata", e);
    }
  }

  private Database createV6Database() {
    try {
      final V6DatabaseMetadata defaultMetaData;
      if (v6ArchiveDirectory.isPresent()) {
        defaultMetaData = V6DatabaseMetadata.separateDBDefault();
      } else {
        defaultMetaData = V6DatabaseMetadata.singleDBDefault();
      }

      final V6DatabaseMetadata metaData =
          V6DatabaseMetadata.init(getMetadataFile(), defaultMetaData);
      if (defaultMetaData.isSingleDB() != metaData.isSingleDB()) {
        throw new DatabaseStorageException(
            "The database was originally created as "
                + (metaData.isSingleDB() ? "Single" : "Separate")
                + " but now accessed as "
                + (defaultMetaData.isSingleDB() ? "Single" : "Separate"));
      }

      DatabaseNetwork.init(getNetworkFile(), Constants.GENESIS_FORK_VERSION, eth1Address);

      final RocksDbConfiguration hotOrSingleDBConfiguration =
          metaData.isSingleDB()
              ? metaData.getSingleDbConfiguration().get().getConfiguration()
              : metaData.getSeparateDbConfiguration().get().getHotDbConfiguration();

      final Optional<RocksDbConfiguration> finalizedConfiguration =
          v6ArchiveDirectory.map(
              dir ->
                  metaData
                      .getSeparateDbConfiguration()
                      .get()
                      .getArchiveDbConfiguration()
                      .withDatabaseDir(dir.toPath()));

      return RocksDbDatabase.createV6(
          metricsSystem,
          hotOrSingleDBConfiguration.withDatabaseDir(dbDirectory.toPath()),
          finalizedConfiguration,
          V4SchemaHot.INSTANCE,
          V6SchemaFinalized.INSTANCE,
          stateStorageMode,
          stateStorageFrequency);
    } catch (final IOException e) {
      throw new DatabaseStorageException("Failed to read metadata", e);
    }
  }

  private File getMetadataFile() {
    return dataDirectory.toPath().resolve(METADATA_FILENAME).toFile();
  }

  private File getNetworkFile() {
    return dataDirectory.toPath().resolve(NETWORK_FILENAME).toFile();
  }

  private void validateDataPaths() {
    if (dbDirectory.exists() && !dbVersionFile.exists()) {
      throw new DatabaseStorageException(
          String.format(
              "No database version file was found, and the database path %s exists.",
              dataDirectory.getAbsolutePath()));
    }
  }

  private void createDirectories(DatabaseVersion dbVersion) {
    if (!dbDirectory.mkdirs() && !dbDirectory.isDirectory()) {
      throw new DatabaseStorageException(
          String.format(
              "Unable to create the path to store database files at %s",
              dbDirectory.getAbsolutePath()));
    }

    switch (dbVersion) {
      case V4:
      case V5:
        if (!v5ArchiveDirectory.mkdirs() && !v5ArchiveDirectory.isDirectory()) {
          throw new DatabaseStorageException(
              String.format(
                  "Unable to create the path to store archive files at %s",
                  v5ArchiveDirectory.getAbsolutePath()));
        }
        break;
      case V6:
        v6ArchiveDirectory.ifPresent(
            archiveDirectory -> {
              if (!archiveDirectory.mkdirs() && !archiveDirectory.isDirectory()) {
                throw new DatabaseStorageException(
                    "Unable to create the path to store archive files at "
                        + archiveDirectory.getAbsolutePath());
              }
            });
        break;
      default:
        // do nothing
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
