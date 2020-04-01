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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Files;
import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.TrackingStorageUpdateChannel;

public abstract class AbstractStorageBackedDatabaseTest extends AbstractDatabaseTest {

  protected abstract Database createDatabase(
      final File tempDir, final StateStorageMode storageMode);

  @Override
  protected Database createDatabase(final StateStorageMode storageMode) {
    final File tmpDir = Files.createTempDir();
    tmpDir.deleteOnExit();
    return createDatabase(tmpDir, storageMode);
  }

  private Database setupDatabase(final File tempDir, final StateStorageMode storageMode) {
    database = createDatabase(tempDir, storageMode);
    storageUpdateChannel = new TrackingStorageUpdateChannel(database);
    return database;
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
    database = setupDatabase(tempDir.toFile(), storageMode);
    database.storeGenesis(store);

    // Create blocks
    final SignedBeaconBlock block1 = blockAtSlot(1, store.getFinalizedCheckpoint().getRoot());
    final SignedBeaconBlock block2 = blockAtSlot(2, block1);
    final SignedBeaconBlock block3 = blockAtSlot(3, block2);
    // Few skipped slots
    final SignedBeaconBlock block7 = blockAtSlot(7, block3);
    final SignedBeaconBlock block8 = blockAtSlot(8, block7);
    final SignedBeaconBlock block9 = blockAtSlot(9, block8);
    // Create some blocks on a different fork
    final SignedBeaconBlock forkBlock6 = blockAtSlot(6, block1);
    final SignedBeaconBlock forkBlock7 = blockAtSlot(7, forkBlock6);
    final SignedBeaconBlock forkBlock8 = blockAtSlot(8, forkBlock7);
    final SignedBeaconBlock forkBlock9 = blockAtSlot(9, forkBlock8);

    // Create States
    final Map<Bytes32, BeaconState> states = new HashMap<>();
    final BeaconState block3State = dataStructureUtil.randomBeaconState(block3.getSlot());
    final BeaconState block7State = dataStructureUtil.randomBeaconState(block7.getSlot());
    final BeaconState forkBlock6State = dataStructureUtil.randomBeaconState(forkBlock6.getSlot());
    final BeaconState forkBlock7State = dataStructureUtil.randomBeaconState(forkBlock7.getSlot());
    // Store states in map
    states.put(block3.getMessage().hash_tree_root(), block3State);
    states.put(block7.getMessage().hash_tree_root(), block7State);
    states.put(forkBlock6.getMessage().hash_tree_root(), forkBlock6State);
    states.put(forkBlock7.getMessage().hash_tree_root(), forkBlock7State);

    add(
        states,
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

    assertStatesAvailable(states);

    assertThat(database.getSignedBlock(block7.getMessage().hash_tree_root())).contains(block7);
    assertLatestUpdateResultPrunedCollectionsAreEmpty();

    finalizeEpoch(UnsignedLong.ONE, block7.getMessage().hash_tree_root());

    // Upon finalization, we should prune data
    final Set<Bytes32> blocksToPrune =
        Set.of(block1, block2, block3, forkBlock6).stream()
            .map(b -> b.getMessage().hash_tree_root())
            .collect(Collectors.toSet());
    blocksToPrune.add(genesisBlock.hash_tree_root());
    final Set<Checkpoint> checkpointsToPrune = Set.of(genesisCheckpoint);
    assertLatestUpdateResultContains(blocksToPrune, checkpointsToPrune);

    // Check data was pruned from store
    assertStoreWasPruned(store, blocksToPrune, checkpointsToPrune);

    // Close and re-read from disk store.
    database.close();
    database = setupDatabase(tempDir.toFile(), storageMode);
    assertOnlyHotBlocks(block7, block8, block9, forkBlock7, forkBlock8, forkBlock9);
    assertBlocksFinalized(block1, block2, block3, block7);
    assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(block1, block2, block3, block7);

    assertHotStatesAvailable(List.of(block7State, forkBlock7State));
    switch (storageMode) {
      case ARCHIVE:
        final Map<Bytes32, BeaconState> expectedStates = new HashMap<>(states);
        // We should've pruned non-canonical states prior to latest finalized slot
        expectedStates.remove(forkBlock6.getMessage().hash_tree_root());
        assertStatesAvailable(expectedStates);
        break;
      case PRUNE:
        assertStatesUnavailableForBlocks(block3, forkBlock6);
        assertStatesAvailable(
            Map.of(
                block7.getMessage().hash_tree_root(),
                block7State,
                forkBlock7.getMessage().hash_tree_root(),
                forkBlock7State));
        break;
    }

    // Should still be able to retrieve finalized blocks by root
    assertThat(database.getSignedBlock(block1.getMessage().hash_tree_root())).contains(block1);
  }
}
