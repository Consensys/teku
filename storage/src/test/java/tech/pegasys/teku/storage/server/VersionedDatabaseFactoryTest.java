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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.storage.server.StateStorageMode.PRUNE;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.storage.storageSystem.SupportedDatabaseVersionArgumentsProvider;

public class VersionedDatabaseFactoryTest {

  private static final StateStorageMode DATA_STORAGE_MODE = PRUNE;
  public static final int MAX_KNOWN_NODE_CACHE_SIZE = 10_000;
  private final Eth1Address eth1Address =
      Eth1Address.fromHexString("0x77f7bED277449F51505a4C54550B074030d989bC");

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  @TempDir Path dataDir;

  @Test
  public void createDatabase_fromEmptyDataDir() throws Exception {
    final DatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            Optional.empty(),
            DATA_STORAGE_MODE,
            eth1Address,
            false,
            false,
            false,
            spec);
    try (final Database db = dbFactory.createDatabase()) {
      assertThat(db).isNotNull();

      assertDbVersionSaved(dataDir, DatabaseVersion.DEFAULT_VERSION);
    }
  }

  @Test
  public void createDatabase_fromExistingDataDir() throws Exception {
    final DatabaseVersion nonDefaultDatabaseVersion =
        SupportedDatabaseVersionArgumentsProvider.supportedDatabaseVersions().stream()
            .filter(version -> version != DatabaseVersion.DEFAULT_VERSION)
            .findAny()
            .orElseThrow();
    createDbDirectory(dataDir);
    createVersionFile(dataDir, nonDefaultDatabaseVersion);

    final VersionedDatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            Optional.empty(),
            DATA_STORAGE_MODE,
            eth1Address,
            false,
            false,
            false,
            spec);
    try (final Database db = dbFactory.createDatabase()) {
      assertThat(db).isNotNull();
    }
    assertThat(dbFactory.getDatabaseVersion()).isEqualTo(nonDefaultDatabaseVersion);
  }

  @Test
  public void createDatabase_invalidVersionFile() throws Exception {
    createDbDirectory(dataDir);
    createVersionFile(dataDir, "bla");

    final DatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            Optional.empty(),
            DATA_STORAGE_MODE,
            eth1Address,
            false,
            false,
            false,
            spec);
    assertThatThrownBy(dbFactory::createDatabase)
        .isInstanceOf(DatabaseStorageException.class)
        .hasMessageContaining("Unrecognized database version: bla");
  }

  @Test
  public void createDatabase_dbExistsButNoVersionIsSaved() throws Exception {
    createDbDirectory(dataDir);

    final DatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            Optional.empty(),
            DATA_STORAGE_MODE,
            eth1Address,
            false,
            false,
            false,
            spec);
    assertThatThrownBy(dbFactory::createDatabase)
        .isInstanceOf(DatabaseStorageException.class)
        .hasMessageContaining("No database version file was found");
  }

  @ParameterizedTest
  @ArgumentsSource(SupportedDatabaseVersionArgumentsProvider.class)
  public void createDatabase_shouldAllowAllSupportedDatabases(final DatabaseVersion version) {
    createDbDirectory(dataDir);
    final VersionedDatabaseFactory dbFactory =
        new VersionedDatabaseFactory(
            new StubMetricsSystem(),
            dataDir,
            Optional.empty(),
            DATA_STORAGE_MODE,
            version,
            1L,
            eth1Address,
            false,
            MAX_KNOWN_NODE_CACHE_SIZE,
            false,
            false,
            spec);
    assertThat(dbFactory.getDatabaseVersion()).isEqualTo(version);
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
