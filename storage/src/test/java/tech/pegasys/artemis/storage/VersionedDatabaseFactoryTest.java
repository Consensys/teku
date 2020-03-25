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

package tech.pegasys.artemis.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class VersionedDatabaseFactoryTest {

  @TempDir Path dataDir;
  ArtemisConfiguration config;

  @BeforeEach
  public void setup() {
    config = createConfig(dataDir);
  }

  @Test
  public void createDatabase_fromEmptyDataDir() throws Exception {
    final VersionedDatabaseFactory dbFactory = new VersionedDatabaseFactory(config);
    try (final Database db = dbFactory.createDatabase()) {
      assertThat(db).isNotNull();

      assertDbVersionSaved(dataDir, DatabaseVersion.DEFAULT_VERSION);
    }
  }

  @Test
  public void createDatabase_fromExistingDataDir() throws Exception {
    createDbDirectory(dataDir);
    createVersionFile(dataDir, DatabaseVersion.V1);

    final VersionedDatabaseFactory dbFactory = new VersionedDatabaseFactory(config);
    try (final Database db = dbFactory.createDatabase()) {
      assertThat(db).isNotNull();
    }
  }

  @Test
  public void createDatabase_invalidVersionFile() throws Exception {
    createDbDirectory(dataDir);
    createVersionFile(dataDir, "bla");

    final VersionedDatabaseFactory dbFactory = new VersionedDatabaseFactory(config);
    assertThatThrownBy(dbFactory::createDatabase)
        .isInstanceOf(DatabaseStorageException.class)
        .hasMessageContaining("Unrecognized database version: bla");
  }

  @Test
  public void createDatabase_dbExistsButNoVersionIsSaved() throws Exception {
    createDbDirectory(dataDir);

    final VersionedDatabaseFactory dbFactory = new VersionedDatabaseFactory(config);
    assertThatThrownBy(dbFactory::createDatabase)
        .isInstanceOf(DatabaseStorageException.class)
        .hasMessageContaining("No database version file was found");
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

  private ArtemisConfiguration createConfig(final Path dataPath) {
    final StringBuilder configBuilder = new StringBuilder();
    String escapedPath = dataPath.toAbsolutePath().toString().replace("\\", "\\\\");
    configBuilder.append(String.format("database.dataPath=\"%s\"", escapedPath));

    return ArtemisConfiguration.fromString(configBuilder.toString());
  }
}
