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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.storage.store.StoreAssertions.assertStoresMatch;

import com.google.common.io.Files;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.config.StateStorageMode;

public abstract class AbstractStorageBackedDatabaseTest extends AbstractDatabaseTest {
  private final List<File> tmpDirectories = new ArrayList<>();

  protected abstract StorageSystem createStorageSystem(
      final File tempDir, final StateStorageMode storageMode, final StoreConfig storeConfig);

  @Override
  protected StorageSystem createStorageSystemInternal(
      final StateStorageMode storageMode, final StoreConfig storeConfig) {
    final File tmpDir = Files.createTempDir();
    tmpDirectories.add(tmpDir);
    return createStorageSystem(tmpDir, storageMode, storeConfig);
  }

  @Override
  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
    // Clean up tmp directories
    for (File tmpDirectory : tmpDirectories) {
      MoreFiles.deleteRecursively(tmpDirectory.toPath(), RecursiveDeleteOption.ALLOW_INSECURE);
    }
    tmpDirectories.clear();
  }

  protected StorageSystem createStorage(final File tempDir, final StateStorageMode storageMode) {
    this.storageMode = storageMode;
    final StorageSystem storage =
        createStorageSystem(tempDir, storageMode, StoreConfig.createDefault());
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

    final UpdatableStore memoryStore = recreateStore();
    assertStoresMatch(memoryStore, store);
    assertThat(database.getEarliestAvailableBlockSlot()).contains(genesisBlockAndState.getSlot());
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
    final UInt64 finalizedEpoch = UInt64.valueOf(2);
    final UInt64 finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch).minus(UInt64.ONE);
    chainBuilder.generateBlocksUpToSlot(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainBuilder.getBlockAndStateAtSlot(finalizedSlot);
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(finalizedEpoch);

    // Add some more blocks
    final UInt64 firstHotBlockSlot = finalizedCheckpoint.getEpochStartSlot().plus(UInt64.ONE);
    chainBuilder.generateBlockAtSlot(firstHotBlockSlot);
    chainBuilder.generateBlocksUpToSlot(firstHotBlockSlot.plus(10));

    // Save new blocks and finalized checkpoint
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    chainBuilder.streamBlocksAndStates(1).forEach(b -> add(tx, List.of(b)));
    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock, tx);
    tx.commit().join();

    // Shutdown and restart
    restartStorage();

    final UpdatableStore memoryStore = recreateStore();
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

  @Test
  public void shouldRecreateAnchorStoreOnRestart(@TempDir final Path tempDir) {
    // Set up database from an anchor point
    final UInt64 anchorEpoch = UInt64.valueOf(10);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.generateBlockAtSlot(compute_start_slot_at_epoch(anchorEpoch));
    final AnchorPoint anchor =
        AnchorPoint.create(
            new Checkpoint(anchorEpoch, anchorBlockAndState.getRoot()), anchorBlockAndState);
    createStorage(tempDir.toFile(), StateStorageMode.PRUNE);
    initFromAnchor(anchor);

    // Shutdown and restart
    restartStorage();

    final UpdatableStore memoryStore = recreateStore();
    assertStoresMatch(memoryStore, store);
    assertThat(memoryStore.getInitialCheckpoint()).contains(anchor.getCheckpoint());
    assertThat(database.getEarliestAvailableBlockSlot()).contains(anchorBlockAndState.getSlot());
  }
}
