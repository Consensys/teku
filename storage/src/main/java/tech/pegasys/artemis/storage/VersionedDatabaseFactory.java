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

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class VersionedDatabaseFactory {
  private static final Logger LOG = LogManager.getLogger();

  @VisibleForTesting static final String DB_PATH = "db";
  @VisibleForTesting static final String DB_VERSION_PATH = "db.version";

  private final ArtemisConfiguration config;
  private final File dataDirectory;
  private final File dbDirectory;
  private final File dbVersionFile;

  public VersionedDatabaseFactory(final ArtemisConfiguration config) {
    this.config = config;

    this.dataDirectory = Paths.get(config.getDataPath()).toFile();
    this.dbDirectory = this.dataDirectory.toPath().resolve(DB_PATH).toFile();
    this.dbVersionFile = this.dataDirectory.toPath().resolve(DB_VERSION_PATH).toFile();
  }

  public Database createDatabase() {
    LOG.info("Data directory set to: {}", dataDirectory.getAbsolutePath());
    validateDataPaths();
    final DatabaseVersion dbVersion = getDatabaseVersion();
    createDirectories();
    saveDatabaseVersion(dbVersion);

    Database database = null;
    switch (dbVersion) {
      case V1:
        database = createV1Database();
        break;
    }
    LOG.trace("Created database ({}) at {}", dbVersion.getValue(), dbDirectory.getAbsolutePath());
    return database;
  }

  private Database createV1Database() {
    final StateStorageMode stateStorageMode =
        StateStorageMode.fromString(config.getDataStorageMode());
    return MapDbDatabase.createOnDisk(dbDirectory, stateStorageMode);
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
    if (!dbDirectory.exists() && !dbDirectory.mkdirs()) {
      throw new DatabaseStorageException(
          String.format(
              "Unable to create the path to store database files at %s",
              dbDirectory.getAbsolutePath()));
    }
  }

  private DatabaseVersion getDatabaseVersion() {
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
    } else {
      return DatabaseVersion.DEFAULT_VERSION;
    }
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
