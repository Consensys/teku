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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.util.config.StateStorageMode.PRUNE;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.util.config.StateStorageMode;

public class VersionedDatabaseFactoryTest {

  private static final StateStorageMode DATA_STORAGE_MODE = PRUNE;
  private final Optional<Eth1Address> eth1Address =
      Optional.of(Eth1Address.fromHexString("0x77f7bED277449F51505a4C54550B074030d989bC"));
  @TempDir Path dataDir;

  @Test
  public void createDatabase_fromEmptyDataDir() throws Exception {
    final DatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(), dataDir, DATA_STORAGE_MODE, eth1Address);
    try (final Database db = dbFactory.createDatabase()) {
      assertThat(db).isNotNull();

      assertDbVersionSaved(dataDir, DatabaseVersion.DEFAULT_VERSION);
    }
  }

  @Test
  public void createDatabase_fromExistingDataDir() throws Exception {
    createDbDirectory(dataDir);
    createVersionFile(dataDir, DatabaseVersion.V4);

    final VersionedDatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(), dataDir, DATA_STORAGE_MODE, eth1Address);
    try (final Database db = dbFactory.createDatabase()) {
      assertThat(db).isNotNull();
    }
    assertThat(dbFactory.getDatabaseVersion()).isEqualTo(DatabaseVersion.V4);
  }

  @Test
  public void createDatabase_asV4Database() throws Exception {
    final DatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            DATA_STORAGE_MODE,
            DatabaseVersion.V4,
            1L,
            eth1Address);
    try (final Database db = dbFactory.createDatabase()) {
      assertThat(db).isNotNull();
      assertDbVersionSaved(dataDir, DatabaseVersion.V4);
    }
    final File dbDirectory = new File(dataDir.toFile(), VersionedDatabaseFactory.DB_PATH);
    final File archiveDirectory = new File(dataDir.toFile(), VersionedDatabaseFactory.ARCHIVE_PATH);
    assertThat(dbDirectory).exists();
    assertThat(archiveDirectory).exists();
  }

  @Test
  public void createDatabase_asV5Database() throws Exception {
    final DatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            DATA_STORAGE_MODE,
            DatabaseVersion.V5,
            1L,
            eth1Address);
    try (final Database db = dbFactory.createDatabase()) {
      assertThat(db).isNotNull();
      assertDbVersionSaved(dataDir, DatabaseVersion.V5);
    }
    final File dbDirectory = new File(dataDir.toFile(), VersionedDatabaseFactory.DB_PATH);
    final File archiveDirectory = new File(dataDir.toFile(), VersionedDatabaseFactory.ARCHIVE_PATH);
    final File metadataFile =
        new File(dataDir.toFile(), VersionedDatabaseFactory.METADATA_FILENAME);
    assertThat(dbDirectory).exists();
    assertThat(archiveDirectory).exists();
    assertThat(metadataFile).exists();
  }

  @Test
  public void createDatabase_asV6DatabaseSingle() throws Exception {
    final DatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            DATA_STORAGE_MODE,
            DatabaseVersion.V6,
            1L,
            eth1Address);
    try (final Database db = dbFactory.createDatabase()) {
      assertThat(db).isNotNull();
      assertDbVersionSaved(dataDir, DatabaseVersion.V6);
    }
    final File dbDirectory = new File(dataDir.toFile(), VersionedDatabaseFactory.DB_PATH);
    final File metadataFile =
        new File(dataDir.toFile(), VersionedDatabaseFactory.METADATA_FILENAME);
    assertThat(dbDirectory).exists();
    assertThat(metadataFile).exists();
  }

  @Test
  public void createDatabase_asV6DatabaseSeparate() throws Exception {
    Path mainDataDir = dataDir.resolve("hot");
    Path coldDataDir = dataDir.resolve("cold");
    final DatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            mainDataDir,
            Optional.of(coldDataDir),
            DATA_STORAGE_MODE,
            DatabaseVersion.V6,
            1L,
            eth1Address);

    try (final Database db = dbFactory.createDatabase()) {
      assertThat(db).isNotNull();
      assertDbVersionSaved(mainDataDir, DatabaseVersion.V6);
    }
    final File dbDirectory = new File(mainDataDir.toFile(), VersionedDatabaseFactory.DB_PATH);
    final File coldDirectory =
        new File(coldDataDir.toFile(), VersionedDatabaseFactory.ARCHIVE_PATH);
    final File metadataFile =
        new File(mainDataDir.toFile(), VersionedDatabaseFactory.METADATA_FILENAME);
    assertThat(dbDirectory).exists();
    assertThat(coldDirectory).exists();
    assertThat(metadataFile).exists();
  }

  @Test
  public void createDatabase_invalidVersionFile() throws Exception {
    createDbDirectory(dataDir);
    createVersionFile(dataDir, "bla");

    final DatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(), dataDir, DATA_STORAGE_MODE, eth1Address);
    assertThatThrownBy(dbFactory::createDatabase)
        .isInstanceOf(DatabaseStorageException.class)
        .hasMessageContaining("Unrecognized database version: bla");
  }

  @Test
  public void createDatabase_dbExistsButNoVersionIsSaved() throws Exception {
    createDbDirectory(dataDir);

    final DatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(), dataDir, DATA_STORAGE_MODE, eth1Address);
    assertThatThrownBy(dbFactory::createDatabase)
        .isInstanceOf(DatabaseStorageException.class)
        .hasMessageContaining("No database version file was found");
  }

  @Test
  public void createDatabase_shouldAllowV4Database() {
    createDbDirectory(dataDir);
    final VersionedDatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            DATA_STORAGE_MODE,
            DatabaseVersion.V4,
            1L,
            eth1Address);
    assertThat(dbFactory.getDatabaseVersion()).isEqualTo(DatabaseVersion.V4);
  }

  @Test
  public void createDatabase_shouldAllowV5Database() {
    createDbDirectory(dataDir);
    final VersionedDatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            DATA_STORAGE_MODE,
            DatabaseVersion.V5,
            1L,
            eth1Address);
    assertThat(dbFactory.getDatabaseVersion()).isEqualTo(DatabaseVersion.V5);
  }

  @Test
  public void dontAllowToCreateSeparateFromSingle() throws Exception {
    final DatabaseFactory dbFactorySingle =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            Optional.empty(),
            DATA_STORAGE_MODE,
            DatabaseVersion.V6,
            1L,
            eth1Address);
    try (Database db = dbFactorySingle.createDatabase()) {}

    final DatabaseFactory dbFactorySeparate =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            Optional.of(dataDir.resolve("cold")),
            DATA_STORAGE_MODE,
            DatabaseVersion.V6,
            1L,
            eth1Address);

    assertThatThrownBy(
            () -> {
              try (Database db = dbFactorySeparate.createDatabase()) {}
            })
        .isInstanceOf(DatabaseStorageException.class);
  }

  @Test
  public void dontAllowToCreateSingleFromSeparate() throws Exception {
    final DatabaseFactory dbFactorySeparate =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            Optional.of(dataDir.resolve("cold")),
            DATA_STORAGE_MODE,
            DatabaseVersion.V6,
            1L,
            eth1Address);
    try (Database db = dbFactorySeparate.createDatabase()) {}

    final DatabaseFactory dbFactorySingle =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            Optional.empty(),
            DATA_STORAGE_MODE,
            DatabaseVersion.V6,
            1L,
            eth1Address);

    assertThatThrownBy(
            () -> {
              try (Database db = dbFactorySingle.createDatabase()) {}
            })
        .isInstanceOf(DatabaseStorageException.class);
  }

  private void createDbDirectory(final Path dataPath) {
    final File dbDirectory =
        Paths.get(dataPath.toAbsolutePath().toString(), VersionedDatabaseFactory.DB_PATH).toFile();
    dbDirectory.mkdirs();
    assertThat(dbDirectory).isEmptyDirectory();
  }

  private void createVersionFile(final Path dataPath, final DatabaseVersion version)
      throws Exception {
    createVersionFile(dataPath, version.getValue());
    assertDbVersionSaved(dataPath, version);
  }

  private void createVersionFile(final Path dataPath, final String version) throws IOException {
    Path versionPath = dataPath.resolve(VersionedDatabaseFactory.DB_VERSION_PATH);
    Files.writeString(versionPath, version);
  }

  private void assertDbVersionSaved(final Path dataDirectory, final DatabaseVersion defaultVersion)
      throws IOException {
    final String versionValue =
        Files.readString(dataDirectory.resolve(VersionedDatabaseFactory.DB_VERSION_PATH));
    assertThat(versionValue).isEqualTo(defaultVersion.getValue());
  }
}
