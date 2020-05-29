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

import com.google.common.io.Files;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.storage.Store;
import tech.pegasys.teku.storage.api.TrackingStorageUpdateChannel;
import tech.pegasys.teku.util.config.StateStorageMode;
import tech.pegasys.teku.util.file.FileUtil;

public abstract class AbstractStorageBackedDatabaseTest extends AbstractDatabaseTest {
  private final List<File> tmpDirectories = new ArrayList<>();

  protected abstract Database createDatabase(
      final File tempDir, final StateStorageMode storageMode);

  @Override
  protected Database createDatabase(final StateStorageMode storageMode) {
    final File tmpDir = Files.createTempDir();
    tmpDirectories.add(tmpDir);
    return createDatabase(tmpDir, storageMode);
  }

  @Override
  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
    // Clean up tmp directories
    FileUtil.recursivelyDeleteDirectories(tmpDirectories);
    tmpDirectories.clear();
  }

  protected Database setupDatabase(final File tempDir, final StateStorageMode storageMode) {
    database = createDatabase(tempDir, storageMode);
    databases.add(database);
    storageUpdateChannel = new TrackingStorageUpdateChannel(database);
    return database;
  }

  @Test
  public void shouldRecreateGenesisStateOnRestart_archiveMode(@TempDir final Path tempDir)
      throws Exception {
    testShouldRecreateGenesisStateOnRestart(tempDir, StateStorageMode.ARCHIVE);
  }

  @Test
  public void shouldRecreateGenesisStateOnRestart_pruneMode(@TempDir final Path tempDir)
      throws Exception {
    testShouldRecreateGenesisStateOnRestart(tempDir, StateStorageMode.PRUNE);
  }

  public void testShouldRecreateGenesisStateOnRestart(
      final Path tempDir, final StateStorageMode storageMode) throws Exception {
    // Set up database with genesis state
    database = setupDatabase(tempDir.toFile(), storageMode);
    store = Store.getForkChoiceStore(genesisBlockAndState.getState());
    database.storeGenesis(store);

    // Shutdown and restart
    database.close();
    database = setupDatabase(tempDir.toFile(), storageMode);

    final Store memoryStore = database.createMemoryStore().orElseThrow();
    assertThat(memoryStore).isEqualToIgnoringGivenFields(store, "time", "lock", "readLock");
  }

  @Test
  public void shouldPersistOnDisk_pruneMode(@TempDir final Path tempDir) throws Exception {
    testShouldPersistOnDisk(tempDir, StateStorageMode.PRUNE);
  }

  @Test
  public void shouldPersistOnDisk_archiveMode(@TempDir final Path tempDir) throws Exception {
    testShouldPersistOnDisk(tempDir, StateStorageMode.ARCHIVE);
  }

  private void testShouldPersistOnDisk(
      @TempDir final Path tempDir, final StateStorageMode storageMode) throws Exception {
    Function<StateStorageMode, Database> initializeDatabase =
        mode -> setupDatabase(tempDir.toFile(), mode);
    Function<Database, Database> restartDatabase =
        d -> {
          try {
            d.close();
            return initializeDatabase.apply(storageMode);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        };
    testShouldRecordFinalizedBlocksAndStates(
        storageMode, false, initializeDatabase, restartDatabase);
  }
}
