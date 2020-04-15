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

package tech.pegasys.artemis.storage.server;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Streams;
import com.google.common.io.Files;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.artemis.core.ChainBuilder;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.TrackingStorageUpdateChannel;
import tech.pegasys.artemis.util.file.FileUtil;

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
    // Setup chains
    // Both chains share block up to slot 3
    final ChainBuilder primaryChain = ChainBuilder.create(VALIDATOR_KEYS);
    final SignedBlockAndState genesis = primaryChain.generateGenesis();
    primaryChain.generateBlocksUpToSlot(3);
    final ChainBuilder forkChain = primaryChain.fork();
    // Fork chain's next block is at 6
    forkChain.generateBlockAtSlot(6);
    forkChain.generateBlocksUpToSlot(9);
    // Primary chain's next block is at 7
    primaryChain.generateBlockAtSlot(7);
    primaryChain.generateBlocksUpToSlot(9);

    // Setup database
    database = setupDatabase(tempDir.toFile(), storageMode);
    store = Store.getForkChoiceStore(genesis.getState());
    database.storeGenesis(store);

    final Set<SignedBlockAndState> allBlocksAndStates =
        Streams.concat(primaryChain.streamBlocksAndStates(), forkChain.streamBlocksAndStates())
            .collect(Collectors.toSet());

    final Map<Bytes32, BeaconState> allStatesByRoot =
        allBlocksAndStates.stream()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));

    add(allBlocksAndStates);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(primaryChain.getBlockAtSlot(3));
    finalizeCheckpoint(finalizedCheckpoint);

    // Close database and rebuild from disk
    database.close();
    database = setupDatabase(tempDir.toFile(), storageMode);

    // Check hot data
    final List<SignedBlockAndState> expectedHotBlocksAndStates =
        new ArrayList<>(allBlocksAndStates);
    expectedHotBlocksAndStates.remove(primaryChain.getBlockAndStateAtSlot(0));
    expectedHotBlocksAndStates.remove(primaryChain.getBlockAndStateAtSlot(1));
    expectedHotBlocksAndStates.remove(primaryChain.getBlockAndStateAtSlot(2));
    assertHotBlocksAndStates(expectedHotBlocksAndStates);

    // Check finalized blocks
    final List<SignedBeaconBlock> expectedFinalizedBlocks =
        primaryChain
            .streamBlocksAndStatesUpTo(3)
            .map(SignedBlockAndState::getBlock)
            .collect(toList());
    assertBlocksFinalized(expectedFinalizedBlocks);
    assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(expectedFinalizedBlocks);
    assertBlocksAvailableByRoot(expectedFinalizedBlocks);

    switch (storageMode) {
      case ARCHIVE:
        assertStatesAvailable(allStatesByRoot);
        break;
      case PRUNE:
        // Check states roots that should've been prune
        final List<Bytes32> prunedRoots =
            primaryChain
                .streamBlocksAndStatesUpTo(2)
                .map(SignedBlockAndState::getRoot)
                .collect(Collectors.toList());
        assertStatesUnavailable(prunedRoots);
        // Check hot states
        final Map<Bytes32, BeaconState> expectedHotStates =
            Streams.concat(
                    primaryChain.streamBlocksAndStates(3, 9), forkChain.streamBlocksAndStates(6, 9))
                .collect(
                    Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));
        assertStatesAvailable(expectedHotStates);
        break;
    }
  }
}
