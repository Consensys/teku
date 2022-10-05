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
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;
import tech.pegasys.teku.storage.server.kvstore.schema.V6SchemaCombinedSnapshot;
import tech.pegasys.teku.storage.server.leveldb.LevelDbDatabaseFactory;
import tech.pegasys.teku.storage.server.metadata.V5DatabaseMetadata;
import tech.pegasys.teku.storage.server.metadata.V6DatabaseMetadata;
import tech.pegasys.teku.storage.server.network.DatabaseNetwork;
import tech.pegasys.teku.storage.server.noop.NoOpDatabase;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbDatabaseFactory;

public class VersionedDatabaseFactory implements DatabaseFactory {
  private static final Logger LOG = LogManager.getLogger();

  @VisibleForTesting static final String DB_PATH = "db";
  @VisibleForTesting static final String ARCHIVE_PATH = "archive";
  @VisibleForTesting static final String DB_VERSION_PATH = "db.version";
  @VisibleForTesting static final String METADATA_FILENAME = "metadata.yml";
  @VisibleForTesting static final String NETWORK_FILENAME = "network.yml";

  private final MetricsSystem metricsSystem;
  private final File dataDirectory;
  private final int maxKnownNodeCacheSize;
  private final int blockMigrationBatchSize;
  private final int blockMigrationBatchDelay;
  private final boolean storeBlockExecutionPayloadSeparately;
  private final File dbDirectory;
  private final File v5ArchiveDirectory;
  private final File dbVersionFile;
  private final StateStorageMode stateStorageMode;
  private final DatabaseVersion createDatabaseVersion;
  private final long stateStorageFrequency;
  private final Eth1Address eth1Address;
  private final Spec spec;
  private final boolean storeNonCanonicalBlocks;

  private final Optional<AsyncRunner> asyncRunner;

  public VersionedDatabaseFactory(
      final MetricsSystem metricsSystem,
      final Path dataPath,
      final Optional<AsyncRunner> asyncRunner,
      final StorageConfiguration config) {

    this.metricsSystem = metricsSystem;
    this.dataDirectory = dataPath.toFile();
    this.asyncRunner = asyncRunner;

    this.stateStorageMode = config.getDataStorageMode();
    this.createDatabaseVersion = config.getDataStorageCreateDbVersion();
    this.maxKnownNodeCacheSize = config.getMaxKnownNodeCacheSize();
    this.storeBlockExecutionPayloadSeparately = config.isStoreBlockExecutionPayloadSeparately();
    this.stateStorageFrequency = config.getDataStorageFrequency();
    this.eth1Address = config.getEth1DepositContract();
    this.storeNonCanonicalBlocks = config.isStoreNonCanonicalBlocksEnabled();
    this.blockMigrationBatchSize = config.getBlockMigrationBatchSize();
    this.blockMigrationBatchDelay = config.getBlockMigrationBatchDelay();
    this.spec = config.getSpec();

    this.dbDirectory = this.dataDirectory.toPath().resolve(DB_PATH).toFile();
    this.v5ArchiveDirectory = this.dataDirectory.toPath().resolve(ARCHIVE_PATH).toFile();
    this.dbVersionFile = this.dataDirectory.toPath().resolve(DB_VERSION_PATH).toFile();
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
        LOG.info(
            "Created V6 Hot and Finalized database ({}) at {}",
            dbVersion.getValue(),
            dbDirectory.getAbsolutePath());
        break;
      case LEVELDB1:
        database = createLevelDbV1Database();
        LOG.info(
            "Created leveldb1 Hot database ({}) at {}",
            dbVersion.getValue(),
            dbDirectory.getAbsolutePath());
        LOG.info(
            "Created leveldb1 Finalized database ({}) at {}",
            dbVersion.getValue(),
            v5ArchiveDirectory.getAbsolutePath());
        break;
      case LEVELDB2:
        database = createLevelDbV2Database();
        LOG.info(
            "Created leveldb2 Hot and Finalized database ({}) at {}",
            dbVersion.getValue(),
            dbDirectory.getAbsolutePath());
        break;
      case LEVELDB_TREE:
        database = createLevelDbTreeDatabase();
        LOG.info(
            "Created leveldb_tree Hot and Finalized database ({}) at {}",
            dbVersion.getValue(),
            dbDirectory.getAbsolutePath());
        break;
      default:
        throw new UnsupportedOperationException("Unhandled database version " + dbVersion);
    }
    return database;
  }

  private Database createV4Database() {
    try {
      DatabaseNetwork.init(
          getNetworkFile(), spec.getGenesisSpecConfig().getGenesisForkVersion(), eth1Address);
      return RocksDbDatabaseFactory.createV4(
          metricsSystem,
          KvStoreConfiguration.v4Settings(dbDirectory.toPath()),
          KvStoreConfiguration.v4Settings(v5ArchiveDirectory.toPath()),
          stateStorageMode,
          stateStorageFrequency,
          storeNonCanonicalBlocks,
          storeBlockExecutionPayloadSeparately,
          blockMigrationBatchSize,
          blockMigrationBatchDelay,
          asyncRunner,
          spec);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to read configuration file", e);
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
      DatabaseNetwork.init(
          getNetworkFile(), spec.getGenesisSpecConfig().getGenesisForkVersion(), eth1Address);
      return RocksDbDatabaseFactory.createV4(
          metricsSystem,
          metaData.getHotDbConfiguration().withDatabaseDir(dbDirectory.toPath()),
          metaData.getArchiveDbConfiguration().withDatabaseDir(v5ArchiveDirectory.toPath()),
          stateStorageMode,
          stateStorageFrequency,
          storeNonCanonicalBlocks,
          storeBlockExecutionPayloadSeparately,
          blockMigrationBatchSize,
          blockMigrationBatchDelay,
          asyncRunner,
          spec);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to read metadata", e);
    }
  }

  private Database createV6Database() {
    try {

      final KvStoreConfiguration dbConfiguration = initV6Configuration();

      final V6SchemaCombinedSnapshot schema = V6SchemaCombinedSnapshot.createV6(spec);
      return RocksDbDatabaseFactory.createV6(
          metricsSystem,
          dbConfiguration.withDatabaseDir(dbDirectory.toPath()),
          schema,
          stateStorageMode,
          stateStorageFrequency,
          storeNonCanonicalBlocks,
          storeBlockExecutionPayloadSeparately,
          blockMigrationBatchSize,
          blockMigrationBatchDelay,
          asyncRunner,
          spec);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to read metadata", e);
    }
  }

  /**
   * LevelDb database uses same setup as v4 but backed by leveldb instead of RocksDb
   *
   * @return the created database
   */
  private Database createLevelDbV1Database() {
    try {
      final V5DatabaseMetadata metaData =
          V5DatabaseMetadata.init(getMetadataFile(), V5DatabaseMetadata.v5Defaults());
      DatabaseNetwork.init(
          getNetworkFile(), spec.getGenesisSpecConfig().getGenesisForkVersion(), eth1Address);
      return LevelDbDatabaseFactory.createLevelDb(
          metricsSystem,
          metaData.getHotDbConfiguration().withDatabaseDir(dbDirectory.toPath()),
          metaData.getArchiveDbConfiguration().withDatabaseDir(v5ArchiveDirectory.toPath()),
          stateStorageMode,
          stateStorageFrequency,
          storeNonCanonicalBlocks,
          storeBlockExecutionPayloadSeparately,
          blockMigrationBatchSize,
          blockMigrationBatchDelay,
          asyncRunner,
          spec);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to read metadata", e);
    }
  }

  private Database createLevelDbV2Database() {
    try {
      final KvStoreConfiguration dbConfiguration = initV6Configuration();

      return LevelDbDatabaseFactory.createLevelDbV2(
          metricsSystem,
          dbConfiguration.withDatabaseDir(dbDirectory.toPath()),
          stateStorageMode,
          stateStorageFrequency,
          storeNonCanonicalBlocks,
          storeBlockExecutionPayloadSeparately,
          blockMigrationBatchSize,
          blockMigrationBatchDelay,
          asyncRunner,
          spec);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to read metadata", e);
    }
  }

  private Database createLevelDbTreeDatabase() {
    try {
      final KvStoreConfiguration dbConfiguration = initV6Configuration();

      return LevelDbDatabaseFactory.createLevelDbTree(
          metricsSystem,
          dbConfiguration.withDatabaseDir(dbDirectory.toPath()),
          stateStorageMode,
          storeNonCanonicalBlocks,
          storeBlockExecutionPayloadSeparately,
          blockMigrationBatchSize,
          blockMigrationBatchDelay,
          maxKnownNodeCacheSize,
          asyncRunner,
          spec);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to read metadata", e);
    }
  }

  private KvStoreConfiguration initV6Configuration() throws IOException {
    final V6DatabaseMetadata metaData =
        V6DatabaseMetadata.init(getMetadataFile(), V6DatabaseMetadata.singleDBDefault());

    DatabaseNetwork.init(
        getNetworkFile(), spec.getGenesisSpecConfig().getGenesisForkVersion(), eth1Address);

    return metaData.getSingleDbConfiguration().getConfiguration();
  }

  private File getMetadataFile() {
    return dataDirectory.toPath().resolve(METADATA_FILENAME).toFile();
  }

  private File getNetworkFile() {
    return dataDirectory.toPath().resolve(NETWORK_FILENAME).toFile();
  }

  private void validateDataPaths() {
    if (dbDirectory.exists() && !dbVersionFile.exists()) {
      throw DatabaseStorageException.unrecoverable(
          String.format(
              "No database version file was found, and the database path %s exists.",
              dataDirectory.getAbsolutePath()));
    }
  }

  private void createDirectories(DatabaseVersion dbVersion) {
    if (!dbDirectory.mkdirs() && !dbDirectory.isDirectory()) {
      throw DatabaseStorageException.unrecoverable(
          String.format(
              "Unable to create the path to store database files at %s",
              dbDirectory.getAbsolutePath()));
    }

    switch (dbVersion) {
      case V4:
      case V5:
      case LEVELDB1:
        if (!v5ArchiveDirectory.mkdirs() && !v5ArchiveDirectory.isDirectory()) {
          throw DatabaseStorageException.unrecoverable(
              String.format(
                  "Unable to create the path to store archive files at %s",
                  v5ArchiveDirectory.getAbsolutePath()));
        }
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
                    DatabaseStorageException.unrecoverable(
                        "Unrecognized database version: " + versionValue));
      } catch (IOException e) {
        throw DatabaseStorageException.unrecoverable(
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
        throw DatabaseStorageException.unrecoverable(
            "Failed to write database version to file " + dbVersionFile.getAbsolutePath(), e);
      }
    }
  }
}
