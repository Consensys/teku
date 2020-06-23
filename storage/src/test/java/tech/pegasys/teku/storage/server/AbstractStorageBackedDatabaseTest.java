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

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.storage.store.StoreAssertions.assertStoresMatch;

import com.google.common.io.Files;
import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.config.StateStorageMode;
import tech.pegasys.teku.util.file.FileUtil;

public abstract class AbstractStorageBackedDatabaseTest extends AbstractDatabaseTest {
  private final List<File> tmpDirectories = new ArrayList<>();

  protected abstract StorageSystem createStorageSystem(
      final File tempDir, final StateStorageMode storageMode);

  @Override
  protected StorageSystem createStorageSystemInternal(final StateStorageMode storageMode) {
    final File tmpDir = Files.createTempDir();
    tmpDirectories.add(tmpDir);
    return createStorageSystem(tmpDir, storageMode);
  }

  @Override
  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
    // Clean up tmp directories
    FileUtil.recursivelyDeleteDirectories(tmpDirectories);
    tmpDirectories.clear();
  }

  protected StorageSystem createStorage(final File tempDir, final StateStorageMode storageMode) {
    this.storageMode = storageMode;
    final StorageSystem storage = createStorageSystem(tempDir, storageMode);
    setDefaultStorage(storage);
    return storage;
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
      final Path tempDir, final StateStorageMode storageMode) {
    // Set up database with genesis state
    createStorage(tempDir.toFile(), storageMode);
    initGenesis();

    // Shutdown and restart
    restartStorage();

    final UpdatableStore memoryStore = database.createMemoryStore().orElseThrow();
    assertStoresMatch(memoryStore, store);
  }

  @Test
  public void shouldRecreateStoreOnRestart_withOffEpochBoundaryFinalizedBlock_archiveMode(
      @TempDir final Path tempDir) throws Exception {
    testShouldRecreateStoreOnRestartWithOffEpochBoundaryFinalizedBlock(
        tempDir, StateStorageMode.ARCHIVE);
  }

  @Test
  public void shouldRecreateStoreOnRestart_withOffEpochBoundaryFinalizedBlock_pruneMode(
      @TempDir final Path tempDir) throws Exception {
    testShouldRecreateStoreOnRestartWithOffEpochBoundaryFinalizedBlock(
        tempDir, StateStorageMode.PRUNE);
  }

  public void testShouldRecreateStoreOnRestartWithOffEpochBoundaryFinalizedBlock(
      final Path tempDir, final StateStorageMode storageMode) throws Exception {
    // Set up database with genesis state
    createStorage(tempDir.toFile(), storageMode);
    initGenesis();

    // Create finalized block at slot prior to epoch boundary
    final UnsignedLong finalizedEpoch = UnsignedLong.valueOf(2);
    final UnsignedLong finalizedSlot =
        compute_start_slot_at_epoch(finalizedEpoch).minus(UnsignedLong.ONE);
    chainBuilder.generateBlocksUpToSlot(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainBuilder.getBlockAndStateAtSlot(finalizedSlot);
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(finalizedEpoch);

    // Add some more blocks
    final UnsignedLong firstHotBlockSlot =
        finalizedCheckpoint.getEpochStartSlot().plus(UnsignedLong.ONE);
    chainBuilder.generateBlockAtSlot(firstHotBlockSlot);
    chainBuilder.generateBlocksUpToSlot(firstHotBlockSlot.plus(UnsignedLong.valueOf(10)));

    // Save new blocks and finalized checkpoint
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    chainBuilder.streamBlocksAndStates(1).forEach(tx::putBlockAndState);
    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock, tx);
    tx.commit().join();

    // Shutdown and restart
    restartStorage();

    final UpdatableStore memoryStore = database.createMemoryStore().orElseThrow();
    assertStoresMatch(memoryStore, store);
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
    Consumer<StateStorageMode> initializeDatabase = mode -> createStorage(tempDir.toFile(), mode);

    testShouldRecordFinalizedBlocksAndStates(storageMode, false, initializeDatabase);
  }
}
