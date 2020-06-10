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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.HashMap;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.storage.Store;
import tech.pegasys.teku.storage.Store.Transaction;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.events.SuccessfulStorageUpdateResult;
import tech.pegasys.teku.util.async.SafeFuture;

class StoreTest {
  private static final Checkpoint INITIAL_FINALIZED_CHECKPOINT = new Checkpoint();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final Checkpoint initialJustifiedCheckpoint =
      new Checkpoint(UnsignedLong.valueOf(50), dataStructureUtil.randomBytes32());
  private final Checkpoint initialBestJustifiedCheckpoint =
      new Checkpoint(UnsignedLong.valueOf(33), dataStructureUtil.randomBytes32());
  private UnsignedLong INITIAL_GENESIS_TIME = UnsignedLong.ZERO;
  private UnsignedLong INITIAL_TIME = UnsignedLong.ONE;
  private final Store store =
      new Store(
          INITIAL_TIME,
          INITIAL_GENESIS_TIME,
          initialJustifiedCheckpoint,
          INITIAL_FINALIZED_CHECKPOINT,
          initialBestJustifiedCheckpoint,
          new HashMap<>(),
          new HashMap<>(),
          new HashMap<>(),
          new HashMap<>());

  @Test
  public void shouldApplyChangesWhenTransactionCommits() {
    StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);
    when(storageUpdateChannel.onStorageUpdate(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                new SuccessfulStorageUpdateResult(Collections.emptySet(), Collections.emptySet())));
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getRoot();
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final Checkpoint justifiedCheckpoint = new Checkpoint(UnsignedLong.valueOf(2), blockRoot);
    final Checkpoint finalizedCheckpoint = new Checkpoint(UnsignedLong.ONE, blockRoot);
    final Checkpoint bestJustifiedCheckpoint = new Checkpoint(UnsignedLong.valueOf(3), blockRoot);
    final UnsignedLong genesisTime = UnsignedLong.valueOf(1);
    final UnsignedLong time = UnsignedLong.valueOf(3);
    transaction.putBlockAndState(block, state);
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
    assertEquals(initialJustifiedCheckpoint, store.getJustifiedCheckpoint());
    assertEquals(initialBestJustifiedCheckpoint, store.getBestJustifiedCheckpoint());

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
