/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.services.chainstorage;

import static java.nio.file.Files.createTempDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;

class EphemeryDatabaseResetTest {

  @Mock private ServiceConfig serviceConfig;

  @Mock private VersionedDatabaseFactory dbFactory;

  @Mock private DataDirLayout dataDirLayout;
  private Path beaconDataDir;
  private Path dbDataDir;
  private Path resolvedSlashProtectionDir;
  private Path networkFilePath;
  @Mock private Database database;

  private EphemeryDatabaseReset ephemeryDatabaseReset;

  @BeforeEach
  void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    ephemeryDatabaseReset = spy(new EphemeryDatabaseReset());
    beaconDataDir = createTempDirectory("beacon");
    dbDataDir = beaconDataDir.resolve("db");
    Files.createDirectory(dbDataDir);
    Path networkFile = beaconDataDir.resolve("network.yml");
    Files.createFile(networkFile);
    networkFilePath = networkFile;

    final Path validatorDataDir = createTempDirectory("validator");
    resolvedSlashProtectionDir = validatorDataDir.resolve("slashprotection");

    when(serviceConfig.getDataDirLayout()).thenReturn(dataDirLayout);
    when(dataDirLayout.getBeaconDataDirectory()).thenReturn(beaconDataDir);
    when(dataDirLayout.getValidatorDataDirectory()).thenReturn(validatorDataDir);
    when(dataDirLayout.getValidatorDataDirectory().resolve("slashprotection"))
        .thenReturn(resolvedSlashProtectionDir);
  }

  @Test
  void shouldResetSpecificDirectoriesAndCreateDatabase() throws IOException {
    final Path kvStoreDir = beaconDataDir.resolve("kvstore");
    Files.createDirectory(kvStoreDir);
    final Path dbVersion = beaconDataDir.resolve("db.version");
    Files.createFile(dbVersion);

    when(dbFactory.createDatabase()).thenReturn(database);

    final Database result = ephemeryDatabaseReset.resetDatabaseAndCreate(serviceConfig, dbFactory);
    verify(ephemeryDatabaseReset).deleteDirectoryRecursively(dbDataDir);
    verify(ephemeryDatabaseReset).deleteDirectoryRecursively(networkFilePath);
    verify(ephemeryDatabaseReset).deleteDirectoryRecursively(resolvedSlashProtectionDir);

    verify(dbFactory).createDatabase();
    verifyNoMoreInteractions(dbFactory);

    assertTrue(Files.exists(kvStoreDir));
    assertTrue(Files.exists(dbVersion));
    assertEquals(database, result);
  }

  @Test
  void shouldThrowInvalidConfigurationExceptionWhenDirectoryDeletionFails() throws IOException {
    doThrow(new IOException("Failed to delete directory"))
        .when(ephemeryDatabaseReset)
        .deleteDirectoryRecursively(dbDataDir);
    final InvalidConfigurationException exception =
        assertThrows(
            InvalidConfigurationException.class,
            () -> {
              ephemeryDatabaseReset.resetDatabaseAndCreate(serviceConfig, dbFactory);
            });
    assertEquals(
        "The existing ephemery database was old, and was unable to reset it.",
        exception.getMessage());
    verify(dbFactory, never()).createDatabase();
    verify(ephemeryDatabaseReset, never()).deleteDirectoryRecursively(resolvedSlashProtectionDir);
  }

  @Test
  void shouldThrowInvalidConfigurationExceptionWhenDatabaseCreationFails() throws IOException {
    doNothing().when(ephemeryDatabaseReset).deleteDirectoryRecursively(dbDataDir);
    doNothing().when(ephemeryDatabaseReset).deleteDirectoryRecursively(resolvedSlashProtectionDir);
    when(dbFactory.createDatabase()).thenThrow(new RuntimeException("Database creation failed"));
    final InvalidConfigurationException exception =
        assertThrows(
            InvalidConfigurationException.class,
            () -> {
              ephemeryDatabaseReset.resetDatabaseAndCreate(serviceConfig, dbFactory);
            });
    assertEquals(
        "The existing ephemery database was old, and was unable to reset it.",
        exception.getMessage());
    verify(ephemeryDatabaseReset).deleteDirectoryRecursively(dbDataDir);
    verify(ephemeryDatabaseReset).deleteDirectoryRecursively(resolvedSlashProtectionDir);
    verify(dbFactory).createDatabase();
  }
}
