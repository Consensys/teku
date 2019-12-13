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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.Store.Transaction;

class V2MapDatabaseTest {
  private static final BeaconState GENESIS_STATE =
      DataStructureUtil.randomBeaconState(UnsignedLong.ZERO, 1);
  private static final Checkpoint CHECKPOINT1 =
      new Checkpoint(UnsignedLong.valueOf(6), Bytes32.fromHexString("0x1234"));
  private static final Checkpoint CHECKPOINT2 =
      new Checkpoint(UnsignedLong.valueOf(7), Bytes32.fromHexString("0x5678"));
  private static final Checkpoint CHECKPOINT3 =
      new Checkpoint(UnsignedLong.valueOf(8), Bytes32.fromHexString("0x9012"));
  private final Store store = Store.get_genesis_store(GENESIS_STATE);
  private final Database database = V2MapDatabase.createInMemory();

  private int seed = 498242;

  @Test
  public void shouldGetHotBlockByRoot() {
    final Transaction transaction = store.startTransaction();
    final BeaconBlock block1 = DataStructureUtil.randomBeaconBlock(1, seed++);
    final BeaconBlock block2 = DataStructureUtil.randomBeaconBlock(2, seed++);
    final Bytes32 block1Root = block1.signing_root("signature");
    final Bytes32 block2Root = block2.signing_root("signature");
    transaction.putBlock(block1Root, block1);
    transaction.putBlock(block2Root, block2);

    database.insert(transaction);

    assertThat(database.getBlock(block1Root)).contains(block1);
    assertThat(database.getBlock(block2Root)).contains(block2);
  }

  @Test
  public void shouldGetHotStateByRoot() {
    final Transaction transaction = store.startTransaction();
    final BeaconState state1 = DataStructureUtil.randomBeaconState(seed++);
    final BeaconState state2 = DataStructureUtil.randomBeaconState(seed++);
    final Bytes32 block1Root = Bytes32.fromHexString("0x1234");
    final Bytes32 block2Root = Bytes32.fromHexString("0x5822");
    transaction.putBlockState(block1Root, state1);
    transaction.putBlockState(block2Root, state2);

    database.insert(transaction);

    assertThat(database.getState(block1Root)).contains(state1);
    assertThat(database.getState(block2Root)).contains(state2);
  }

  @Test
  public void shouldStoreSingleValueFields() {
    final Transaction transaction = store.startTransaction();
    transaction.setGenesis_time(UnsignedLong.valueOf(3));
    transaction.setTime(UnsignedLong.valueOf(5));
    transaction.setFinalizedCheckpoint(CHECKPOINT1);
    transaction.setJustifiedCheckpoint(CHECKPOINT2);
    transaction.setBestJustifiedCheckpoint(CHECKPOINT3);

    database.insert(transaction);

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

    final Transaction transaction = store.startTransaction();
    transaction.putLatestMessage(validator1, CHECKPOINT1);
    transaction.putLatestMessage(validator2, CHECKPOINT2);
    transaction.putLatestMessage(validator3, CHECKPOINT1);
    database.insert(transaction);

    final Store result1 = database.createMemoryStore();
    assertThat(result1.getLatestMessage(validator1)).isEqualTo(CHECKPOINT1);
    assertThat(result1.getLatestMessage(validator2)).isEqualTo(CHECKPOINT2);
    assertThat(result1.getLatestMessage(validator3)).isEqualTo(CHECKPOINT1);

    // Should overwrite when later changes are made.
    final Transaction transaction2 = store.startTransaction();
    transaction2.putLatestMessage(validator3, CHECKPOINT2);
    database.insert(transaction2);

    final Store result2 = database.createMemoryStore();
    assertThat(result2.getLatestMessage(validator1)).isEqualTo(CHECKPOINT1);
    assertThat(result2.getLatestMessage(validator2)).isEqualTo(CHECKPOINT2);
    assertThat(result2.getLatestMessage(validator3)).isEqualTo(CHECKPOINT2);
  }

  @Test
  public void shouldStoreCheckpointStates() {
    final Transaction transaction = store.startTransaction();

    final Checkpoint forkCheckpoint =
        new Checkpoint(CHECKPOINT1.getEpoch(), Bytes32.fromHexString("0x88677727"));
    transaction.putCheckpointState(CHECKPOINT1, GENESIS_STATE);
    transaction.putCheckpointState(CHECKPOINT2, DataStructureUtil.randomBeaconState(seed++));
    transaction.putCheckpointState(forkCheckpoint, DataStructureUtil.randomBeaconState(seed++));

    database.insert(transaction);

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
    final Transaction transaction1 = store.startTransaction();
    transaction1.putCheckpointState(earlyCheckpoint, DataStructureUtil.randomBeaconState(seed++));
    transaction1.putCheckpointState(middleCheckpoint, DataStructureUtil.randomBeaconState(seed++));
    transaction1.putCheckpointState(laterCheckpoint, DataStructureUtil.randomBeaconState(seed++));
    transaction1.commit();
    database.insert(transaction1);

    // Now update the finalized checkpoint
    final Transaction transaction2 = store.startTransaction();
    transaction2.setFinalizedCheckpoint(middleCheckpoint);
    database.insert(transaction2);

    final Store result = database.createMemoryStore();
    assertThat(result.getCheckpointState(earlyCheckpoint)).isNull();
    assertThat(result.getCheckpointState(middleCheckpoint))
        .isEqualTo(transaction1.getCheckpointState(middleCheckpoint));
    assertThat(result.getCheckpointState(laterCheckpoint))
        .isEqualTo(transaction1.getCheckpointState(laterCheckpoint));
  }

  @Test
  public void shouldLoadHotBlocksAndStatesIntoMemoryStore() {
    final Transaction transaction = store.startTransaction();
    final BeaconBlock block1 = DataStructureUtil.randomBeaconBlock(1, seed++);
    final BeaconBlock block2 = DataStructureUtil.randomBeaconBlock(2, seed++);
    final BeaconState state1 = DataStructureUtil.randomBeaconState(seed++);
    final BeaconState state2 = DataStructureUtil.randomBeaconState(seed++);
    final Bytes32 block1Root = block1.signing_root("signature");
    final Bytes32 block2Root = block2.signing_root("signature");
    transaction.putBlock(block1Root, block1);
    transaction.putBlock(block2Root, block2);
    transaction.putBlockState(block1Root, state1);
    transaction.putBlockState(block2Root, state2);

    database.insert(transaction);

    final Store result = database.createMemoryStore();
    assertThat(result.getBlock(block1Root)).isEqualTo(block1);
    assertThat(result.getBlock(block2Root)).isEqualTo(block2);
    assertThat(result.getBlockState(block1Root)).isEqualTo(state1);
    assertThat(result.getBlockState(block2Root)).isEqualTo(state2);
    assertThat(result.getBlockRoots()).containsOnly(block1Root, block2Root);
  }

  @Test
  public void shouldRemoveHotBlocksAndStatesOnceEpochIsFinalized() {
    final Transaction transaction = store.startTransaction();
    final BeaconBlock block1 = DataStructureUtil.randomBeaconBlock(1, seed++);
    final BeaconBlock block2 = DataStructureUtil.randomBeaconBlock(2, seed++);
    final BeaconBlock unfinalizedBlock =
        DataStructureUtil.randomBeaconBlock(
            compute_start_slot_at_epoch(UnsignedLong.valueOf(2)).longValue(), seed++);
    final BeaconState state1 = DataStructureUtil.randomBeaconState(UnsignedLong.valueOf(1), seed++);
    final BeaconState state2 = DataStructureUtil.randomBeaconState(UnsignedLong.valueOf(2), seed++);
    final BeaconState unfinalizedState =
        DataStructureUtil.randomBeaconState(
            compute_start_slot_at_epoch(UnsignedLong.valueOf(2)), seed++);
    final Bytes32 block1Root = block1.signing_root("signature");
    final Bytes32 block2Root = block2.signing_root("signature");
    final Bytes32 unfinalizedBlockRoot = unfinalizedBlock.signing_root("signature");
    transaction.putBlock(block1Root, block1);
    transaction.putBlock(block2Root, block2);
    transaction.putBlock(unfinalizedBlockRoot, unfinalizedBlock);
    transaction.putBlockState(block1Root, state1);
    transaction.putBlockState(block2Root, state2);
    transaction.putBlockState(unfinalizedBlockRoot, unfinalizedState);

    database.insert(transaction);

    finalizeEpoch(UnsignedLong.ONE, block2Root);

    final Store result = database.createMemoryStore();
    assertThat(result.getBlock(block1Root)).isNull();
    assertThat(result.getBlock(block2Root)).isNull();
    assertThat(result.getBlock(unfinalizedBlockRoot)).isEqualTo(unfinalizedBlock);
    assertThat(result.getBlockState(block1Root)).isNull();
    assertThat(result.getBlockState(block2Root)).isNull();
    assertThat(result.getBlockState(unfinalizedBlockRoot)).isEqualTo(unfinalizedState);
    assertThat(result.getBlockRoots()).containsOnly(unfinalizedBlockRoot);
  }

  private void finalizeEpoch(final UnsignedLong epoch, final Bytes32 root) {
    final Transaction transaction2 = store.startTransaction();
    transaction2.setFinalizedCheckpoint(new Checkpoint(epoch, root));
    database.insert(transaction2);
  }
}
