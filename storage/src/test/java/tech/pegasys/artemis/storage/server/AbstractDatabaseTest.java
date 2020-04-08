/*
 * Copyright 2019 ConsenSys AG.
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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.collect.Streams;
import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.Store.Transaction;
import tech.pegasys.artemis.storage.TrackingStorageUpdateChannel;
import tech.pegasys.artemis.storage.events.StorageUpdateResult;
import tech.pegasys.artemis.util.config.Constants;

public abstract class AbstractDatabaseTest {
  protected static final BeaconState GENESIS_STATE =
      new DataStructureUtil().randomBeaconState(UnsignedLong.ZERO);

  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  protected SignedBeaconBlock checkpoint1Block;
  protected SignedBeaconBlock checkpoint2Block;
  protected SignedBeaconBlock checkpoint3Block;

  protected Checkpoint checkpoint1;
  protected Checkpoint checkpoint2;
  protected Checkpoint checkpoint3;

  protected Database database;
  protected TrackingStorageUpdateChannel storageUpdateChannel;

  protected final Store store = Store.get_genesis_store(GENESIS_STATE);
  protected final BeaconBlock genesisBlock =
      store.getBlockRoots().stream()
          .map(store::getBlock)
          .filter(b -> b.getSlot().equals(UnsignedLong.valueOf(Constants.GENESIS_SLOT)))
          .findFirst()
          .get();
  protected final Checkpoint genesisCheckpoint = store.getFinalizedCheckpoint();

  protected List<Database> databases = new ArrayList<>();

  protected abstract Database createDatabase(final StateStorageMode storageMode);

  protected Database setupDatabase(final StateStorageMode storageMode) {
    database = createDatabase(storageMode);
    databases.add(database);
    storageUpdateChannel = new TrackingStorageUpdateChannel(database);
    return database;
  }

  @BeforeEach
  public void setup(@TempDir final Path tempDir) {
    setupDatabase(StateStorageMode.ARCHIVE);

    final File databaseFile = new File(tempDir.toString(), "teku.db");
    try {
      Files.deleteIfExists(databaseFile.toPath());
    } catch (IOException e) {
      throw new RuntimeException("Failed to clean old database file for new test");
    }

    database.storeGenesis(store);

    checkpoint1Block = blockAtEpoch(6);
    checkpoint2Block = blockAtEpoch(7);
    checkpoint3Block = blockAtEpoch(8);

    checkpoint1 =
        new Checkpoint(UnsignedLong.valueOf(6), checkpoint1Block.getMessage().hash_tree_root());
    checkpoint2 =
        new Checkpoint(UnsignedLong.valueOf(7), checkpoint2Block.getMessage().hash_tree_root());
    checkpoint3 =
        new Checkpoint(UnsignedLong.valueOf(8), checkpoint3Block.getMessage().hash_tree_root());
  }

  @AfterEach
  public void tearDown() throws Exception {
    for (Database db : databases) {
      db.close();
    }
  }

  @Test
  public void createMemoryStoreFromEmptyDatabase() {
    Database database = setupDatabase(StateStorageMode.ARCHIVE);
    assertThat(database.createMemoryStore()).isEmpty();
  }

  @Test
  public void shouldRecreateOriginalGenesisStore() {
    final Store memoryStore = database.createMemoryStore().orElseThrow();
    assertThat(memoryStore).isEqualToIgnoringGivenFields(store, "time", "lock", "readLock");
  }

  @Test
  public void shouldStoreBlockWithLargeSlot() {
    final UnsignedLong slot = UnsignedLong.MAX_VALUE;
    final SignedBeaconBlock newBlock = dataStructureUtil.randomSignedBeaconBlock(slot);
    final Bytes32 root = newBlock.getMessage().hash_tree_root();

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.putBlock(root, newBlock);
    final UnsignedLong epoch = compute_epoch_at_slot(slot);
    transaction.setFinalizedCheckpoint(new Checkpoint(epoch, root));
    transaction.commit().reportExceptions();

    assertThat(database.getSignedBlock(root)).hasValue(newBlock);
    assertThat(database.getFinalizedRootAtSlot(slot)).hasValue(root);
  }

  @Test
  public void shouldGetHotBlockByRoot() {
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    final SignedBeaconBlock block1 = blockAtSlot(1);
    final SignedBeaconBlock block2 = blockAtSlot(2);
    final Bytes32 block1Root = block1.getMessage().hash_tree_root();
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    transaction.putBlock(block1Root, block1);
    transaction.putBlock(block2Root, block2);

    commit(transaction);

    assertThat(database.getSignedBlock(block1Root)).contains(block1);
    assertThat(database.getSignedBlock(block2Root)).contains(block2);
  }

  protected void commit(final Transaction transaction) {
    assertThat(transaction.commit()).isCompleted();
  }

  @Test
  public void shouldGetHotStateByRoot() {
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    final BeaconState state1 = dataStructureUtil.randomBeaconState();
    final BeaconState state2 = dataStructureUtil.randomBeaconState();
    final Bytes32 block1Root = Bytes32.fromHexString("0x1234");
    final Bytes32 block2Root = Bytes32.fromHexString("0x5822");
    transaction.putBlockState(block1Root, state1);
    transaction.putBlockState(block2Root, state2);

    commit(transaction);

    assertThat(database.getState(block1Root)).contains(state1);
    assertThat(database.getState(block2Root)).contains(state2);
  }

  @Test
  public void shouldStoreSingleValueFields() {
    addBlocks(checkpoint1Block, checkpoint2Block, checkpoint3Block);

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.setGenesis_time(UnsignedLong.valueOf(3));
    transaction.setFinalizedCheckpoint(checkpoint1);
    transaction.setJustifiedCheckpoint(checkpoint2);
    transaction.setBestJustifiedCheckpoint(checkpoint3);

    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();

    assertThat(result.getGenesisTime()).isEqualTo(transaction.getGenesisTime());
    assertThat(result.getFinalizedCheckpoint()).isEqualTo(transaction.getFinalizedCheckpoint());
    assertThat(result.getJustifiedCheckpoint()).isEqualTo(transaction.getJustifiedCheckpoint());
    assertThat(result.getBestJustifiedCheckpoint())
        .isEqualTo(transaction.getBestJustifiedCheckpoint());
  }

  @Test
  public void shouldStoreSingleValue_genesisTime() {
    final UnsignedLong newGenesisTime = UnsignedLong.valueOf(3);
    // Sanity check
    assertThat(store.getGenesisTime()).isNotEqualTo(newGenesisTime);

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.setGenesis_time(newGenesisTime);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getGenesisTime()).isEqualTo(transaction.getGenesisTime());
  }

  @Test
  public void shouldStoreSingleValue_justifiedCheckpoint() {
    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getJustifiedCheckpoint()).isNotEqualTo(checkpoint3);

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.setJustifiedCheckpoint(newValue);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getJustifiedCheckpoint()).isEqualTo(newValue);
  }

  @Test
  public void shouldStoreSingleValue_finalizedCheckpoint() {
    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getFinalizedCheckpoint()).isNotEqualTo(checkpoint3);

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.setFinalizedCheckpoint(newValue);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getFinalizedCheckpoint()).isEqualTo(newValue);
  }

  @Test
  public void shouldStoreSingleValue_bestJustifiedCheckpoint() {
    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getBestJustifiedCheckpoint()).isNotEqualTo(checkpoint3);

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.setBestJustifiedCheckpoint(newValue);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getBestJustifiedCheckpoint()).isEqualTo(newValue);
  }

  @Test
  public void shouldStoreSingleValue_singleBlock() {
    final SignedBeaconBlock newBlock = checkpoint3Block;
    final Bytes32 newBlockRoot = newBlock.getMessage().hash_tree_root();
    // Sanity check
    assertThat(store.getBlock(newBlockRoot)).isNull();

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.putBlock(newBlockRoot, newBlock);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getBlock(newBlockRoot)).isEqualTo(newBlock.getMessage());
  }

  @Test
  public void shouldStoreSingleValue_singleBlockState() {
    final BeaconState newState = dataStructureUtil.randomBeaconState();
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    // Sanity check
    assertThat(store.getBlockState(blockRoot)).isNull();

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.putBlockState(blockRoot, newState);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getBlockState(blockRoot)).isEqualTo(newState);
  }

  @Test
  public void shouldStoreSingleValue_singleCheckpointState() {
    final Checkpoint checkpoint = checkpoint3;
    final BeaconState newState = dataStructureUtil.randomBeaconState();
    // Sanity check
    assertThat(store.getCheckpointState(checkpoint)).isNull();

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.putCheckpointState(checkpoint, newState);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getCheckpointState(checkpoint)).isEqualTo(newState);
  }

  @Test
  public void shouldStoreSingleValue_latestMessage() {
    final UnsignedLong validatorIndex = UnsignedLong.valueOf(999);
    final Checkpoint latestMessage = checkpoint3;
    // Sanity check
    assertThat(store.getLatestMessage(validatorIndex)).isNull();

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.putLatestMessage(validatorIndex, latestMessage);
    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getLatestMessage(validatorIndex)).isEqualTo(latestMessage);
  }

  @Test
  public void shouldStoreLatestMessageFromEachValidator() {
    final UnsignedLong validator1 = UnsignedLong.valueOf(1);
    final UnsignedLong validator2 = UnsignedLong.valueOf(2);
    final UnsignedLong validator3 = UnsignedLong.valueOf(3);

    addBlocks(checkpoint1Block, checkpoint2Block, checkpoint3Block);

    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.putLatestMessage(validator1, checkpoint1);
    transaction.putLatestMessage(validator2, checkpoint2);
    transaction.putLatestMessage(validator3, checkpoint1);
    commit(transaction);

    final Store result1 = database.createMemoryStore().orElseThrow();
    assertThat(result1.getLatestMessage(validator1)).isEqualTo(checkpoint1);
    assertThat(result1.getLatestMessage(validator2)).isEqualTo(checkpoint2);
    assertThat(result1.getLatestMessage(validator3)).isEqualTo(checkpoint1);

    // Should overwrite when later changes are made.
    final Transaction transaction2 = store.startTransaction(storageUpdateChannel);
    transaction2.putLatestMessage(validator3, checkpoint2);
    commit(transaction2);

    final Store result2 = database.createMemoryStore().orElseThrow();
    assertThat(result2.getLatestMessage(validator1)).isEqualTo(checkpoint1);
    assertThat(result2.getLatestMessage(validator2)).isEqualTo(checkpoint2);
    assertThat(result2.getLatestMessage(validator3)).isEqualTo(checkpoint2);
  }

  @Test
  public void shouldStoreCheckpointStates() {
    final Transaction transaction = store.startTransaction(storageUpdateChannel);

    addBlocks(checkpoint1Block, checkpoint2Block, checkpoint3Block);

    final Checkpoint forkCheckpoint =
        new Checkpoint(checkpoint1.getEpoch(), Bytes32.fromHexString("0x88677727"));
    transaction.putCheckpointState(checkpoint1, GENESIS_STATE);
    transaction.putCheckpointState(checkpoint2, dataStructureUtil.randomBeaconState());
    transaction.putCheckpointState(forkCheckpoint, dataStructureUtil.randomBeaconState());

    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getCheckpointState(checkpoint1))
        .isEqualTo(transaction.getCheckpointState(checkpoint1));
    assertThat(result.getCheckpointState(checkpoint2))
        .isEqualTo(transaction.getCheckpointState(checkpoint2));
    assertThat(result.getCheckpointState(forkCheckpoint))
        .isEqualTo(transaction.getCheckpointState(forkCheckpoint));
  }

  @Test
  public void shouldRemoveCheckpointStatesPriorToFinalizedCheckpoint() {
    final Checkpoint earlyCheckpoint = createCheckpoint(1);
    final Checkpoint middleCheckpoint = createCheckpoint(2);
    final Checkpoint laterCheckpoint = createCheckpoint(3);

    // First store the initial checkpoints.
    final Transaction transaction1 = store.startTransaction(storageUpdateChannel);
    transaction1.putCheckpointState(earlyCheckpoint, dataStructureUtil.randomBeaconState());
    transaction1.putCheckpointState(middleCheckpoint, dataStructureUtil.randomBeaconState());
    transaction1.putCheckpointState(laterCheckpoint, dataStructureUtil.randomBeaconState());
    commit(transaction1);
    assertLatestUpdateResultPrunedCollectionsAreEmpty();

    // Now update the finalized checkpoint
    final Set<BeaconBlock> blocksToPrune =
        Set.of(genesisBlock, store.getBlock(earlyCheckpoint.getRoot()));
    final Transaction transaction2 = store.startTransaction(storageUpdateChannel);
    transaction2.setFinalizedCheckpoint(middleCheckpoint);
    commit(transaction2);

    final Set<Bytes32> prunedBlocks =
        blocksToPrune.stream().map(BeaconBlock::hash_tree_root).collect(Collectors.toSet());
    final Set<Checkpoint> prunedCheckpoints = Set.of(genesisCheckpoint, earlyCheckpoint);
    assertLatestUpdateResultContains(prunedBlocks, prunedCheckpoints);

    // Check pruned data has been removed from store
    assertStoreWasPruned(store, prunedBlocks, prunedCheckpoints);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getCheckpointState(earlyCheckpoint)).isNull();
    assertThat(result.getCheckpointState(middleCheckpoint))
        .isEqualTo(transaction1.getCheckpointState(middleCheckpoint));
    assertThat(result.getCheckpointState(laterCheckpoint))
        .isEqualTo(transaction1.getCheckpointState(laterCheckpoint));
    assertStoreWasPruned(result, prunedBlocks, prunedCheckpoints);
  }

  @Test
  public void shouldLoadHotBlocksAndStatesIntoMemoryStore() {
    final Bytes32 genesisRoot = store.getFinalizedCheckpoint().getRoot();
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    final SignedBeaconBlock block1 = blockAtSlot(1);
    final SignedBeaconBlock block2 = blockAtSlot(2);
    final BeaconState state1 = dataStructureUtil.randomBeaconState();
    final BeaconState state2 = dataStructureUtil.randomBeaconState();
    final Bytes32 block1Root = block1.getMessage().hash_tree_root();
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    transaction.putBlock(block1Root, block1);
    transaction.putBlock(block2Root, block2);
    transaction.putBlockState(block1Root, state1);
    transaction.putBlockState(block2Root, state2);

    commit(transaction);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getSignedBlock(genesisRoot)).isEqualTo(store.getSignedBlock(genesisRoot));
    assertThat(result.getSignedBlock(block1Root)).isEqualTo(block1);
    assertThat(result.getSignedBlock(block2Root)).isEqualTo(block2);
    assertThat(result.getBlockState(block1Root)).isEqualTo(state1);
    assertThat(result.getBlockState(block2Root)).isEqualTo(state2);
    assertThat(result.getBlockRoots()).containsOnly(genesisRoot, block1Root, block2Root);
  }

  @Test
  public void shouldRemoveHotBlocksAndStatesOnceEpochIsFinalized() {
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    final SignedBeaconBlock block1 = blockAtSlot(1);
    final SignedBeaconBlock block2 = blockAtSlot(2);
    final SignedBeaconBlock unfinalizedBlock =
        blockAtSlot(compute_start_slot_at_epoch(UnsignedLong.valueOf(2)).longValue());

    final BeaconState state1 = dataStructureUtil.randomBeaconState(UnsignedLong.valueOf(1));
    final BeaconState state2 = dataStructureUtil.randomBeaconState(UnsignedLong.valueOf(2));
    final BeaconState unfinalizedState =
        dataStructureUtil.randomBeaconState(compute_start_slot_at_epoch(UnsignedLong.valueOf(2)));

    final Bytes32 block1Root = block1.getMessage().hash_tree_root();
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    final Bytes32 unfinalizedBlockRoot = unfinalizedBlock.getMessage().hash_tree_root();

    transaction.putBlock(block1Root, block1);
    transaction.putBlock(block2Root, block2);
    transaction.putBlock(unfinalizedBlockRoot, unfinalizedBlock);
    transaction.putBlockState(block1Root, state1);
    transaction.putBlockState(block2Root, state2);
    transaction.putBlockState(unfinalizedBlockRoot, unfinalizedState);

    commit(transaction);

    finalizeEpoch(UnsignedLong.ONE, block2Root);

    final Store result = database.createMemoryStore().orElseThrow();
    assertThat(result.getSignedBlock(block1Root)).isNull();
    assertThat(result.getSignedBlock(block2Root)).isEqualTo(block2);
    assertThat(result.getSignedBlock(unfinalizedBlockRoot)).isEqualTo(unfinalizedBlock);
    assertThat(result.getBlockState(block1Root)).isNull();
    assertThat(result.getBlockState(block2Root)).isEqualTo(state2);
    assertThat(result.getBlockState(unfinalizedBlockRoot)).isEqualTo(unfinalizedState);
    assertThat(result.getBlockRoots()).containsOnly(block2Root, unfinalizedBlockRoot);
  }

  @Test
  public void shouldRecordFinalizedBlocksAndStates_pruneMode() {
    testShouldRecordFinalizedBlocksAndStates(StateStorageMode.PRUNE);
  }

  @Test
  public void shouldRecordFinalizedBlocksAndStates_archiveMode() {
    testShouldRecordFinalizedBlocksAndStates(StateStorageMode.ARCHIVE);
  }

  public void testShouldRecordFinalizedBlocksAndStates(final StateStorageMode storageMode) {
    database = setupDatabase(storageMode);
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
    assertThat(database.getSignedBlock(block7.getMessage().hash_tree_root())).contains(block7);
    assertLatestUpdateResultPrunedCollectionsAreEmpty();
    assertStatesAvailable(states);

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

    assertOnlyHotBlocks(block7, block8, block9, forkBlock7, forkBlock8, forkBlock9);
    assertBlocksFinalized(block1, block2, block3, block7);
    assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(block1, block2, block3, block7);

    // Should still be able to retrieve finalized blocks by root
    assertThat(database.getSignedBlock(block1.getMessage().hash_tree_root())).contains(block1);

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
  }

  @Test
  public void testShouldRecordFinalizedBlocksAndStatesInBatchUpdate() {
    database = setupDatabase(StateStorageMode.ARCHIVE);
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

    // Create batch transaction with blocks added and finalized within a single update
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    add(
        transaction,
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
    transaction.setFinalizedCheckpoint(
        new Checkpoint(UnsignedLong.ONE, block7.getMessage().hash_tree_root()));
    transaction.commit().reportExceptions();

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

    assertOnlyHotBlocks(block7, block8, block9, forkBlock7, forkBlock8, forkBlock9);
    assertBlocksFinalized(block1, block2, block3, block7);
    assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(block1, block2, block3, block7);

    // Should still be able to retrieve finalized blocks by root
    assertThat(database.getSignedBlock(block1.getMessage().hash_tree_root())).contains(block1);

    assertHotStatesAvailable(List.of(block7State, forkBlock7State));
    final Map<Bytes32, BeaconState> expectedStates = new HashMap<>(states);
    // We should've pruned non-canonical states prior to latest finalized slot
    expectedStates.remove(forkBlock6.getMessage().hash_tree_root());
    assertStatesAvailable(expectedStates);
  }

  protected void assertBlocksFinalized(final SignedBeaconBlock... blocks) {
    for (SignedBeaconBlock block : blocks) {
      assertThat(database.getFinalizedRootAtSlot(block.getSlot()))
          .describedAs("Block root at slot %s", block.getSlot())
          .contains(block.getMessage().hash_tree_root());
    }
  }

  protected void assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(
      final SignedBeaconBlock... blocks) {
    final UnsignedLong genesisSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
    final Bytes32 genesisRoot = database.getFinalizedRootAtSlot(genesisSlot).get();
    final SignedBeaconBlock genesisBlock = database.getSignedBlock(genesisRoot).get();

    final List<SignedBeaconBlock> finalizedBlocks =
        Streams.concat(Stream.of(genesisBlock), Arrays.stream(blocks))
            .sorted(Comparator.comparing(SignedBeaconBlock::getSlot))
            .collect(toList());

    for (int i = 1; i < finalizedBlocks.size(); i++) {
      final SignedBeaconBlock currentBlock = finalizedBlocks.get(i - 1);
      final SignedBeaconBlock nextBlock = finalizedBlocks.get(i);
      // All slots from the current block up to and excluding the next block should return the
      // current block
      for (long slot = currentBlock.getSlot().longValue();
          slot < nextBlock.getSlot().longValue();
          slot++) {
        assertThat(database.getLatestFinalizedRootAtSlot(UnsignedLong.valueOf(slot)))
            .describedAs("Latest finalized at block root at slot %s", slot)
            .contains(currentBlock.getMessage().hash_tree_root());
      }
    }

    // Check that last block
    final SignedBeaconBlock lastFinalizedBlock = finalizedBlocks.get(finalizedBlocks.size() - 1);
    for (int i = 0; i < 10; i++) {
      final UnsignedLong slot = lastFinalizedBlock.getSlot().plus(UnsignedLong.valueOf(i));
      assertThat(database.getLatestFinalizedRootAtSlot(slot))
          .describedAs("Latest finalized at block root at slot %s", slot)
          .contains(lastFinalizedBlock.getMessage().hash_tree_root());
    }
  }

  protected void assertOnlyHotBlocks(final SignedBeaconBlock... blocks) {
    final Store memoryStore = database.createMemoryStore().orElseThrow();
    assertThat(memoryStore.getBlockRoots())
        .hasSameElementsAs(
            Stream.of(blocks).map(block -> block.getMessage().hash_tree_root()).collect(toList()));
  }

  protected void assertHotStatesAvailable(final List<BeaconState> states) {
    final Store memoryStore = database.createMemoryStore().orElseThrow();
    final List<BeaconState> hotStates =
        memoryStore.getBlockRoots().stream()
            .map(memoryStore::getBlockState)
            .filter(Objects::nonNull)
            .collect(toList());

    assertThat(hotStates).hasSameElementsAs(states);
  }

  protected void assertStatesAvailable(final Map<Bytes32, BeaconState> states) {
    for (Bytes32 root : states.keySet()) {
      assertThat(database.getState(root)).contains(states.get(root));
    }
  }

  protected void assertStatesUnavailableForBlocks(final SignedBeaconBlock... blocks) {
    for (SignedBeaconBlock block : blocks) {
      final Bytes32 root = block.getMessage().hash_tree_root();
      assertThat(database.getState(root)).isEmpty();
    }
  }

  protected void assertLatestUpdateResultContains(
      final Set<Bytes32> blockRoots, final Set<Checkpoint> checkpoints) {
    final StorageUpdateResult latestResult = getLatestUpdateResult();
    assertThat(latestResult.getPrunedBlockRoots()).containsExactlyInAnyOrderElementsOf(blockRoots);
    assertThat(latestResult.getPrunedCheckpoints())
        .containsExactlyInAnyOrderElementsOf(checkpoints);
  }

  protected void assertLatestUpdateResultPrunedCollectionsAreEmpty() {
    final StorageUpdateResult latestResult = getLatestUpdateResult();
    assertThat(latestResult.getPrunedBlockRoots()).isEmpty();
    assertThat(latestResult.getPrunedCheckpoints()).isEmpty();
  }

  protected void assertStoreWasPruned(
      final Store store, final Set<Bytes32> prunedBlocks, final Set<Checkpoint> prunedCheckpoints) {
    // Check pruned data has been removed from store
    for (Bytes32 prunedBlock : prunedBlocks) {
      assertThat(store.getBlock(prunedBlock)).isNull();
      assertThat(store.getBlockState(prunedBlock)).isNull();
    }
    for (Checkpoint prunedCheckpoint : prunedCheckpoints) {
      assertThat(store.getCheckpointState(prunedCheckpoint)).isNull();
    }
  }

  protected void addBlocks(final SignedBeaconBlock... blocks) {
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    for (SignedBeaconBlock block : blocks) {
      transaction.putBlock(block.getMessage().hash_tree_root(), block);
    }
    commit(transaction);
  }

  protected void add(final Map<Bytes32, BeaconState> states, final SignedBeaconBlock... blocks) {
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    add(transaction, states, blocks);
    commit(transaction);
  }

  protected void add(
      final Transaction transaction,
      final Map<Bytes32, BeaconState> states,
      final SignedBeaconBlock... blocks) {
    // Add states
    for (Bytes32 blockRoot : states.keySet()) {
      transaction.putBlockState(blockRoot, states.get(blockRoot));
    }
    // Add blocks
    for (SignedBeaconBlock block : blocks) {
      transaction.putBlock(block.getMessage().hash_tree_root(), block);
    }
  }

  protected void finalizeEpoch(final UnsignedLong epoch, final Bytes32 root) {
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.setFinalizedCheckpoint(new Checkpoint(epoch, root));
    commit(transaction);
  }

  protected Checkpoint createCheckpoint(final long epoch) {
    final SignedBeaconBlock block = blockAtEpoch(epoch);
    addBlocks(block);
    return new Checkpoint(UnsignedLong.valueOf(epoch), block.getMessage().hash_tree_root());
  }

  protected SignedBeaconBlock blockAtEpoch(final long epoch) {
    final UnsignedLong slot = compute_start_slot_at_epoch(UnsignedLong.valueOf(epoch));
    return blockAtSlot(slot.longValue(), dataStructureUtil.randomBytes32());
  }

  protected SignedBeaconBlock blockAtSlot(final long slot) {
    return blockAtSlot(slot, store.getFinalizedCheckpoint().getRoot());
  }

  protected SignedBeaconBlock blockAtSlot(final long slot, final SignedBeaconBlock parent) {
    return blockAtSlot(slot, parent.getMessage().hash_tree_root());
  }

  protected SignedBeaconBlock blockAtSlot(final long slot, final Bytes32 parentRoot) {
    return new SignedBeaconBlock(
        new BeaconBlock(
            UnsignedLong.valueOf(slot), parentRoot, Bytes32.ZERO, new BeaconBlockBody()),
        dataStructureUtil.randomSignature());
  }

  protected StorageUpdateResult getLatestUpdateResult() {
    final List<StorageUpdateResult> updateResults = storageUpdateChannel.getStorageUpdates();
    return updateResults.get(updateResults.size() - 1);
  }
}
