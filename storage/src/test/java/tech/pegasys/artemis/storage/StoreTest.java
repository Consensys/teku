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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBeaconBlock;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBeaconState;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBytes32;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.Store.Transaction;

class StoreTest {

  private static final int SEED = 12424242;
  private static final Checkpoint INITIAL_JUSTIFIED_CHECKPOINT =
      new Checkpoint(UnsignedLong.valueOf(50), randomBytes32());
  private static final Checkpoint INITIAL_FINALIZED_CHECKPOINT = new Checkpoint();
  private final Store store =
      new Store(
          UnsignedLong.ZERO,
          INITIAL_JUSTIFIED_CHECKPOINT,
          INITIAL_FINALIZED_CHECKPOINT,
          new HashMap<>(),
          new HashMap<>(),
          new HashMap<>());

  @Test
  public void shouldApplyChangesWhenTransactionCommits() {
    final Transaction transaction = store.startTransaction();
    final Bytes32 blockRoot = DataStructureUtil.randomBytes32(SEED);
    final Checkpoint justifiedCheckpoint = new Checkpoint(UnsignedLong.valueOf(2), blockRoot);
    final Checkpoint finalizedCheckpoint = new Checkpoint(UnsignedLong.ONE, blockRoot);
    final BeaconBlock block = randomBeaconBlock(10);
    final BeaconState state = randomBeaconState();
    final UnsignedLong time = UnsignedLong.valueOf(3);
    transaction.putBlock(blockRoot, block);
    transaction.putBlockState(blockRoot, state);
    transaction.setFinalized_checkpoint(finalizedCheckpoint);
    transaction.setJustified_checkpoint(justifiedCheckpoint);
    transaction.putCheckpointState(justifiedCheckpoint, state);
    transaction.setTime(time);

    assertFalse(store.containsBlock(blockRoot));
    assertFalse(store.containsBlockState(blockRoot));
    assertFalse(store.containsCheckpointState(justifiedCheckpoint));
    assertEquals(UnsignedLong.ZERO, store.getTime());
    assertEquals(INITIAL_FINALIZED_CHECKPOINT, store.getFinalized_checkpoint());
    assertEquals(INITIAL_JUSTIFIED_CHECKPOINT, store.getJustified_checkpoint());

    transaction.commit();

    assertEquals(block, store.getBlock(blockRoot));
    assertEquals(state, store.getBlockState(blockRoot));
    assertEquals(finalizedCheckpoint, store.getFinalized_checkpoint());
    assertEquals(justifiedCheckpoint, store.getJustified_checkpoint());
    assertEquals(state, store.getCheckpointState(justifiedCheckpoint));
    assertEquals(time, store.getTime());
  }
}
