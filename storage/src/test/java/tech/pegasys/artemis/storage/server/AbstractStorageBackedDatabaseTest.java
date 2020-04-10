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

import com.google.common.io.Files;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

  private Database setupDatabase(final File tempDir, final StateStorageMode storageMode) {
    database = createDatabase(tempDir, storageMode);
    databases.add(database);
    storageUpdateChannel = new TrackingStorageUpdateChannel(database);
    return database;
  }

  @Test
  public void shouldPersistOnDisk_pruneMode(@TempDir final Path tempDir) throws Exception {
    testShouldPersistOnDisk(tempDir, StateStorageMode.PRUNE);
  }

  @Test
  public void shouldPersistOnDisk_archiveMode(@TempDir final Path tempDir) throws Exception {
    //    database.close();
    //    database = setupDatabase(tempDir.toFile(), storageMode);
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
    final Checkpoint genesisCheckpoint = getCheckpointForBlock(genesis.getBlock());
    store = Store.get_genesis_store(genesis.getState());
    database.storeGenesis(store);

    // Create blocks
    final SignedBlockAndState block1 = primaryChain.getBlockAtSlot(1);
    final SignedBlockAndState block2 = primaryChain.getBlockAtSlot(2);
    final SignedBlockAndState block3 = primaryChain.getBlockAtSlot(3);
    // Few skipped slots
    final SignedBlockAndState block7 = primaryChain.getBlockAtSlot(7);
    final SignedBlockAndState block8 = primaryChain.getBlockAtSlot(8);
    final SignedBlockAndState block9 = primaryChain.getBlockAtSlot(9);
    // Create some blocks on a different fork
    final SignedBlockAndState forkBlock6 = forkChain.getBlockAtSlot(6);
    final SignedBlockAndState forkBlock7 = forkChain.getBlockAtSlot(7);
    final SignedBlockAndState forkBlock8 = forkChain.getBlockAtSlot(8);
    final SignedBlockAndState forkBlock9 = forkChain.getBlockAtSlot(9);

    final List<SignedBlockAndState> allBlocks =
        List.of(
            block1,
            block2,
            block3,
            block7,
            block8,
            block9,
            forkBlock6,
            forkBlock7,
            forkBlock8,
            forkBlock9);
    add(allBlocks);

    assertThat(database.getSignedBlock(block7.getRoot())).contains(block7.getBlock());
    assertLatestUpdateResultPrunedCollectionsAreEmpty();
    final Map<Bytes32, BeaconState> allStatesByRoot =
        allBlocks.stream()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));
    assertStatesAvailable(allStatesByRoot);

    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(block7.getBlock());
    finalizeCheckpoint(finalizedCheckpoint);

    // Upon finalization, we should prune data
    final Set<Bytes32> blocksToPrune =
        Set.of(block1, block2, block3, forkBlock6).stream()
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toSet());
    blocksToPrune.add(genesis.getRoot());
    final Set<Checkpoint> checkpointsToPrune = Set.of(genesisCheckpoint);
    assertLatestUpdateResultContains(blocksToPrune, checkpointsToPrune);

    // Check data was pruned from store
    assertStoreWasPruned(store, blocksToPrune, checkpointsToPrune);

    // Close database and rebuild from disk
    database.close();
    database = setupDatabase(tempDir.toFile(), storageMode);

    assertHotBlocksAndStates(block7, block8, block9, forkBlock7, forkBlock8, forkBlock9);
    final List<SignedBeaconBlock> expectedFinalizedBlocks =
        Stream.of(block1, block2, block3, block7)
            .map(SignedBlockAndState::getBlock)
            .collect(toList());
    assertBlocksFinalized(expectedFinalizedBlocks);
    assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(expectedFinalizedBlocks);

    // Should still be able to retrieve finalized blocks by root
    assertThat(database.getSignedBlock(block1.getRoot())).contains(block1.getBlock());

    switch (storageMode) {
      case ARCHIVE:
        final Map<Bytes32, BeaconState> expectedStates = new HashMap<>(allStatesByRoot);
        // We should've pruned non-canonical states prior to latest finalized slot
        expectedStates.remove(forkBlock6.getRoot());
        assertStatesAvailable(expectedStates);
        break;
      case PRUNE:
        assertStatesUnavailableForBlocks(block3.getBlock(), forkBlock6.getBlock());
        assertStatesAvailable(
            Map.of(
                block7.getRoot(), block7.getState(), forkBlock7.getRoot(), forkBlock7.getState()));
        break;
    }
  }
}
