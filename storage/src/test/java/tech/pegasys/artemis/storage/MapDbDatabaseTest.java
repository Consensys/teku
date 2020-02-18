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

package tech.pegasys.artemis.storage;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.TempDirectory;
import org.apache.tuweni.junit.TempDirectoryExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconStateRead;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.Store.Transaction;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSSignature;

@ExtendWith(TempDirectoryExtension.class)
class MapDbDatabaseTest {
  private static final BeaconStateRead GENESIS_STATE =
      DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, 1);
  private static final Checkpoint CHECKPOINT1 =
      new Checkpoint(UnsignedLong.valueOf(6), Bytes32.fromHexString("0x1234"));
  private static final Checkpoint CHECKPOINT2 =
      new Checkpoint(UnsignedLong.valueOf(7), Bytes32.fromHexString("0x5678"));
  private static final Checkpoint CHECKPOINT3 =
      new Checkpoint(UnsignedLong.valueOf(8), Bytes32.fromHexString("0x9012"));

  private Database database = MapDbDatabase.createInMemory();
  private final TransactionPrecommit databaseTransactionPrecommit =
      updateEvent -> {
        database.insert(updateEvent);
        return SafeFuture.completedFuture(null);
      };
  private final Store store = Store.get_genesis_store(GENESIS_STATE);

  private int seed = 498242;

  @BeforeEach
  public void recordGenesis() {
    database.storeGenesis(store);
  }

  @Test
  public void shouldRecreateOriginalGenesisStore() {
    final Store memoryStore = database.createMemoryStore();
    assertThat(memoryStore).isEqualToIgnoringGivenFields(store, "lock", "readLock");
  }

  @Test
  public void shouldGetHotBlockByRoot() {
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
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

  private void commit(final Transaction transaction) {
    assertThat(transaction.commit()).isCompleted();
  }

  @Test
  public void shouldGetHotStateByRoot() {
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    final BeaconStateRead state1 = DataStructureUtil.randomBeaconState(seed++);
    final BeaconStateRead state2 = DataStructureUtil.randomBeaconState(seed++);
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
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    transaction.setGenesis_time(UnsignedLong.valueOf(3));
    transaction.setTime(UnsignedLong.valueOf(5));
    transaction.setFinalizedCheckpoint(CHECKPOINT1);
    transaction.setJustifiedCheckpoint(CHECKPOINT2);
    transaction.setBestJustifiedCheckpoint(CHECKPOINT3);

    commit(transaction);

    final Store result = database.createMemoryStore();

    assertThat(result.getTime()).isEqualTo(transaction.getTime());
    assertThat(result.getGenesisTime()).isEqualTo(transaction.getGenesisTime());
    assertThat(result.getFinalizedCheckpoint()).isEqualTo(transaction.getFinalizedCheckpoint());
    assertThat(result.getJustifiedCheckpoint()).isEqualTo(transaction.getJustifiedCheckpoint());
    assertThat(result.getBestJustifiedCheckpoint())
        .isEqualTo(transaction.getBestJustifiedCheckpoint());
  }

  @Test
  public void shouldStoreLatestMessageFromEachValidator() {
    final UnsignedLong validator1 = UnsignedLong.valueOf(1);
    final UnsignedLong validator2 = UnsignedLong.valueOf(2);
    final UnsignedLong validator3 = UnsignedLong.valueOf(3);

    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    transaction.putLatestMessage(validator1, CHECKPOINT1);
    transaction.putLatestMessage(validator2, CHECKPOINT2);
    transaction.putLatestMessage(validator3, CHECKPOINT1);
    commit(transaction);

    final Store result1 = database.createMemoryStore();
    assertThat(result1.getLatestMessage(validator1)).isEqualTo(CHECKPOINT1);
    assertThat(result1.getLatestMessage(validator2)).isEqualTo(CHECKPOINT2);
    assertThat(result1.getLatestMessage(validator3)).isEqualTo(CHECKPOINT1);

    // Should overwrite when later changes are made.
    final Transaction transaction2 = store.startTransaction(databaseTransactionPrecommit);
    transaction2.putLatestMessage(validator3, CHECKPOINT2);
    commit(transaction2);

    final Store result2 = database.createMemoryStore();
    assertThat(result2.getLatestMessage(validator1)).isEqualTo(CHECKPOINT1);
    assertThat(result2.getLatestMessage(validator2)).isEqualTo(CHECKPOINT2);
    assertThat(result2.getLatestMessage(validator3)).isEqualTo(CHECKPOINT2);
  }

  @Test
  public void shouldStoreCheckpointStates() {
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);

    final Checkpoint forkCheckpoint =
        new Checkpoint(CHECKPOINT1.getEpoch(), Bytes32.fromHexString("0x88677727"));
    transaction.putCheckpointState(CHECKPOINT1, GENESIS_STATE);
    transaction.putCheckpointState(CHECKPOINT2, DataStructureUtil.randomBeaconState(seed++));
    transaction.putCheckpointState(forkCheckpoint, DataStructureUtil.randomBeaconState(seed++));

    commit(transaction);

    final Store result = database.createMemoryStore();
    assertThat(result.getCheckpointState(CHECKPOINT1))
        .isEqualTo(transaction.getCheckpointState(CHECKPOINT1));
    assertThat(result.getCheckpointState(CHECKPOINT2))
        .isEqualTo(transaction.getCheckpointState(CHECKPOINT2));
    assertThat(result.getCheckpointState(forkCheckpoint))
        .isEqualTo(transaction.getCheckpointState(forkCheckpoint));
  }

  @Test
  public void shouldRemoveCheckpointStatesPriorToFinalizedCheckpoint() {
    final Checkpoint earlyCheckpoint =
        new Checkpoint(UnsignedLong.ONE, Bytes32.fromHexString("0x01"));
    final Checkpoint middleCheckpoint =
        new Checkpoint(UnsignedLong.valueOf(2), Bytes32.fromHexString("0x02"));
    final Checkpoint laterCheckpoint =
        new Checkpoint(UnsignedLong.valueOf(3), Bytes32.fromHexString("0x03"));

    // First store the initial checkpoints.
    final Transaction transaction1 = store.startTransaction(databaseTransactionPrecommit);
    transaction1.putCheckpointState(earlyCheckpoint, DataStructureUtil.randomBeaconState(seed++));
    transaction1.putCheckpointState(middleCheckpoint, DataStructureUtil.randomBeaconState(seed++));
    transaction1.putCheckpointState(laterCheckpoint, DataStructureUtil.randomBeaconState(seed++));
    commit(transaction1);

    // Now update the finalized checkpoint
    final Transaction transaction2 = store.startTransaction(databaseTransactionPrecommit);
    transaction2.setFinalizedCheckpoint(middleCheckpoint);
    commit(transaction2);

    final Store result = database.createMemoryStore();
    assertThat(result.getCheckpointState(earlyCheckpoint)).isNull();
    assertThat(result.getCheckpointState(middleCheckpoint))
        .isEqualTo(transaction1.getCheckpointState(middleCheckpoint));
    assertThat(result.getCheckpointState(laterCheckpoint))
        .isEqualTo(transaction1.getCheckpointState(laterCheckpoint));
  }

  @Test
  public void shouldLoadHotBlocksAndStatesIntoMemoryStore() {
    final Bytes32 genesisRoot = store.getFinalizedCheckpoint().getRoot();
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    final SignedBeaconBlock block1 = blockAtSlot(1);
    final SignedBeaconBlock block2 = blockAtSlot(2);
    final BeaconStateRead state1 = DataStructureUtil.randomBeaconState(seed++);
    final BeaconStateRead state2 = DataStructureUtil.randomBeaconState(seed++);
    final Bytes32 block1Root = block1.getMessage().hash_tree_root();
    final Bytes32 block2Root = block2.getMessage().hash_tree_root();
    transaction.putBlock(block1Root, block1);
    transaction.putBlock(block2Root, block2);
    transaction.putBlockState(block1Root, state1);
    transaction.putBlockState(block2Root, state2);

    commit(transaction);

    final Store result = database.createMemoryStore();
    assertThat(result.getSignedBlock(genesisRoot)).isEqualTo(store.getSignedBlock(genesisRoot));
    assertThat(result.getSignedBlock(block1Root)).isEqualTo(block1);
    assertThat(result.getSignedBlock(block2Root)).isEqualTo(block2);
    assertThat(result.getBlockState(block1Root)).isEqualTo(state1);
    assertThat(result.getBlockState(block2Root)).isEqualTo(state2);
    assertThat(result.getBlockRoots()).containsOnly(genesisRoot, block1Root, block2Root);
  }

  @Test
  public void shouldRemoveHotBlocksAndStatesOnceEpochIsFinalized() {
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    final SignedBeaconBlock block1 = blockAtSlot(1);
    final SignedBeaconBlock block2 = blockAtSlot(2);
    final SignedBeaconBlock unfinalizedBlock =
        blockAtSlot(compute_start_slot_at_epoch(UnsignedLong.valueOf(2)).longValue());
    final BeaconStateRead state1 = DataStructureUtil.randomBeaconState(UnsignedLong.valueOf(1), seed++);
    final BeaconStateRead state2 = DataStructureUtil.randomBeaconState(UnsignedLong.valueOf(2), seed++);
    final BeaconStateRead unfinalizedState =
        DataStructureUtil.randomBeaconState(
            compute_start_slot_at_epoch(UnsignedLong.valueOf(2)), seed++);
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

    final Store result = database.createMemoryStore();
    assertThat(result.getSignedBlock(block1Root)).isNull();
    assertThat(result.getSignedBlock(block2Root)).isNull();
    assertThat(result.getSignedBlock(unfinalizedBlockRoot)).isEqualTo(unfinalizedBlock);
    assertThat(result.getBlockState(block1Root)).isNull();
    assertThat(result.getBlockState(block2Root)).isNull();
    assertThat(result.getBlockState(unfinalizedBlockRoot)).isEqualTo(unfinalizedState);
    assertThat(result.getBlockRoots()).containsOnly(unfinalizedBlockRoot);
  }

  @Test
  public void shouldRecordFinalizedBlocksAndStates() {
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

    addBlocks(
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

    finalizeEpoch(UnsignedLong.ONE, block7.getMessage().hash_tree_root());

    assertOnlyHotBlocks(block8, block9, forkBlock8, forkBlock9);
    assertBlocksFinalized(block1, block2, block3, block7);

    // Should still be able to retrieve finalized blocks by root
    assertThat(database.getSignedBlock(block1.getMessage().hash_tree_root())).contains(block1);
  }

  @Test
  public void shouldPersistOnDisk(@TempDirectory final Path tempDir) throws Exception {
    try {
      database = MapDbDatabase.createOnDisk(tempDir.toFile(), false);
      database.storeGenesis(store);

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

      addBlocks(
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

      finalizeEpoch(UnsignedLong.ONE, block7.getMessage().hash_tree_root());

      // Close and re-read from disk store.
      database.close();
      database = MapDbDatabase.createOnDisk(tempDir.toFile(), true);
      assertOnlyHotBlocks(block8, block9, forkBlock8, forkBlock9);
      assertBlocksFinalized(block1, block2, block3, block7);

      // Should still be able to retrieve finalized blocks by root
      assertThat(database.getSignedBlock(block1.getMessage().hash_tree_root())).contains(block1);
    } finally {
      // Close and re-read from disk store.
      database.close();
      database = MapDbDatabase.createInMemory();
    }
  }

  private void assertBlocksFinalized(final SignedBeaconBlock... blocks) {
    for (SignedBeaconBlock block : blocks) {
      assertThat(database.getFinalizedRootAtSlot(block.getSlot()))
          .describedAs("Block root at slot %s", block.getSlot())
          .contains(block.getMessage().hash_tree_root());
    }
  }

  private void assertOnlyHotBlocks(final SignedBeaconBlock... blocks) {
    final Store memoryStore = database.createMemoryStore();
    assertThat(memoryStore.getBlockRoots())
        .hasSameElementsAs(
            Stream.of(blocks).map(block -> block.getMessage().hash_tree_root()).collect(toList()));
  }

  private void addBlocks(final SignedBeaconBlock... blocks) {
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    for (SignedBeaconBlock block : blocks) {
      transaction.putBlock(block.getMessage().hash_tree_root(), block);
    }
    commit(transaction);
  }

  private void finalizeEpoch(final UnsignedLong epoch, final Bytes32 root) {
    final Transaction transaction = store.startTransaction(databaseTransactionPrecommit);
    transaction.setFinalizedCheckpoint(new Checkpoint(epoch, root));
    commit(transaction);
  }

  private SignedBeaconBlock blockAtSlot(final long slot) {
    return blockAtSlot(slot, store.getFinalizedCheckpoint().getRoot());
  }

  private SignedBeaconBlock blockAtSlot(final long slot, final SignedBeaconBlock parent) {
    return blockAtSlot(slot, parent.getMessage().hash_tree_root());
  }

  private SignedBeaconBlock blockAtSlot(final long slot, final Bytes32 parentRoot) {
    return new SignedBeaconBlock(
        new BeaconBlock(
            UnsignedLong.valueOf(slot), parentRoot, Bytes32.ZERO, new BeaconBlockBody()),
        BLSSignature.empty());
  }
}
