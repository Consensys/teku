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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBeaconState;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBytes32;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomSignedBeaconBlock;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.Store.Transaction;

class StoreTest {

  private static final int SEED = 12424242;
  private static final Checkpoint INITIAL_JUSTIFIED_CHECKPOINT =
      new Checkpoint(UnsignedLong.valueOf(50), randomBytes32(SEED - 1));
  private static final Checkpoint INITIAL_BEST_JUSTIFIED_CHECKPOINT =
      new Checkpoint(UnsignedLong.valueOf(33), randomBytes32(SEED - 2));
  private static final Checkpoint INITIAL_FINALIZED_CHECKPOINT = new Checkpoint();
  private UnsignedLong INITIAL_GENESIS_TIME = UnsignedLong.ZERO;
  private UnsignedLong INITIAL_TIME = UnsignedLong.ONE;
  private final TransactionPrecommit transactionPrecommit = TransactionPrecommit.memoryOnly();
  private final Store store =
      new Store(
          INITIAL_TIME,
          INITIAL_GENESIS_TIME,
          INITIAL_JUSTIFIED_CHECKPOINT,
          INITIAL_FINALIZED_CHECKPOINT,
          INITIAL_BEST_JUSTIFIED_CHECKPOINT,
          new HashMap<>(),
          new HashMap<>(),
          new HashMap<>(),
          new HashMap<>());

  @Test
  public void shouldApplyChangesWhenTransactionCommits() {
    final Transaction transaction = store.startTransaction(transactionPrecommit);
    final Bytes32 blockRoot = DataStructureUtil.randomBytes32(SEED);
    final Checkpoint justifiedCheckpoint = new Checkpoint(UnsignedLong.valueOf(2), blockRoot);
    final Checkpoint finalizedCheckpoint = new Checkpoint(UnsignedLong.ONE, blockRoot);
    final Checkpoint bestJustifiedCheckpoint = new Checkpoint(UnsignedLong.valueOf(3), blockRoot);
    final SignedBeaconBlock block = randomSignedBeaconBlock(10, 100);
    final BeaconState state = randomBeaconState(100);
    final UnsignedLong genesisTime = UnsignedLong.valueOf(1);
    final UnsignedLong time = UnsignedLong.valueOf(3);
    transaction.putBlock(blockRoot, block);
    transaction.putBlockState(blockRoot, state);
    transaction.setFinalizedCheckpoint(finalizedCheckpoint);
    transaction.setJustifiedCheckpoint(justifiedCheckpoint);
    transaction.setBestJustifiedCheckpoint(bestJustifiedCheckpoint);
    transaction.putCheckpointState(justifiedCheckpoint, state);
    transaction.setTime(time);
    transaction.setGenesis_time(genesisTime);

    assertFalse(store.containsBlock(blockRoot));
    assertFalse(store.containsBlockState(blockRoot));
    assertFalse(store.containsCheckpointState(justifiedCheckpoint));
    assertEquals(INITIAL_TIME, store.getTime());
    assertEquals(INITIAL_GENESIS_TIME, store.getGenesisTime());
    assertEquals(INITIAL_FINALIZED_CHECKPOINT, store.getFinalizedCheckpoint());
    assertEquals(INITIAL_JUSTIFIED_CHECKPOINT, store.getJustifiedCheckpoint());
    assertEquals(INITIAL_BEST_JUSTIFIED_CHECKPOINT, store.getBestJustifiedCheckpoint());

    assertEquals(block, transaction.getSignedBlock(blockRoot));
    assertEquals(state, transaction.getBlockState(blockRoot));
    assertEquals(finalizedCheckpoint, transaction.getFinalizedCheckpoint());
    assertEquals(justifiedCheckpoint, transaction.getJustifiedCheckpoint());
    assertEquals(bestJustifiedCheckpoint, transaction.getBestJustifiedCheckpoint());
    assertEquals(state, transaction.getCheckpointState(justifiedCheckpoint));
    assertEquals(time, transaction.getTime());
    assertEquals(genesisTime, transaction.getGenesisTime());

    assertThat(transaction.commit()).isCompleted();

    assertEquals(block, store.getSignedBlock(blockRoot));
    assertEquals(state, store.getBlockState(blockRoot));
    assertEquals(finalizedCheckpoint, store.getFinalizedCheckpoint());
    assertEquals(justifiedCheckpoint, store.getJustifiedCheckpoint());
    assertEquals(bestJustifiedCheckpoint, store.getBestJustifiedCheckpoint());
    assertEquals(state, store.getCheckpointState(justifiedCheckpoint));
    assertEquals(time, store.getTime());
    assertEquals(genesisTime, store.getGenesisTime());
  }
}
