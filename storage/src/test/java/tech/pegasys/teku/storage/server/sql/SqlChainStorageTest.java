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

package tech.pegasys.teku.storage.server.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.storage.server.sql.SqlDatabaseFactory.DB_FILENAME;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class SqlChainStorageTest {

  @TempDir static Path templateDir;

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  Path dbDir;

  private HikariDataSource dataSource;
  private SqlChainStorage storage;

  @BeforeAll
  static void createTemplate() {
    // Nice thing about sqlite DBs, you can just copy them and save rerunning the init code...
    // Create the template to be copied here, and copy it to a test-specific dir in setUp
    final HikariDataSource db = SqlDatabaseFactory.initDataSource(templateDir);
    db.close();
  }

  @BeforeEach
  void setUp() throws Exception {
    // Using two @TempDir annotations gives the same directory for both so we have to manually
    // create a test specific temp dir
    dbDir = Files.createTempDirectory(templateDir, "test");
    Files.copy(templateDir.resolve(DB_FILENAME), dbDir.resolve(DB_FILENAME));
    dataSource = SqlDatabaseFactory.createDataSource(dbDir);

    final PlatformTransactionManager transactionManager =
        new DataSourceTransactionManager(dataSource);
    storage = new SqlChainStorage(transactionManager, dataSource);
  }

  @AfterEach
  void tearDown() throws Exception {
    storage.close();
    dataSource.close();
    MoreFiles.deleteRecursively(dbDir, RecursiveDeleteOption.ALLOW_INSECURE);
  }

  @Test
  void shouldStoreHotBlock() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    try (final SqlChainStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeBlock(block, false);
      transaction.commit();
    }

    assertThat(storage.getBlockByBlockRoot(block.getRoot())).contains(block);
    assertThat(storage.getHotBlockByBlockRoot(block.getRoot())).contains(block);
  }

  @Test
  void shouldFinalizeBlock() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    try (final SqlChainStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeBlock(block, false);
      transaction.finalizeBlocks(List.of(block.getRoot()));
      transaction.commit();
    }

    assertThat(storage.getBlockByBlockRoot(block.getRoot())).contains(block);
    assertThat(storage.getHotBlockByBlockRoot(block.getRoot())).isEmpty();
  }

  @Test
  void shouldDeleteHotBlock() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    try (final SqlChainStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeBlock(block, false);
      transaction.deleteHotBlockByBlockRoot(block.getRoot());
      transaction.commit();
    }

    assertThat(storage.getBlockByBlockRoot(block.getRoot())).isEmpty();
    assertThat(storage.getHotBlockByBlockRoot(block.getRoot())).isEmpty();
  }

  @Test
  void shouldNotDeleteFinalizedBlock() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    try (final SqlChainStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeBlock(block, true);
      transaction.deleteHotBlockByBlockRoot(block.getRoot());
      transaction.commit();
    }

    assertThat(storage.getBlockByBlockRoot(block.getRoot())).contains(block);
    assertThat(storage.getHotBlockByBlockRoot(block.getRoot())).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenBlockDoesNotExist() {
    assertThat(storage.getBlockByBlockRoot(dataStructureUtil.randomBytes32())).isEmpty();
    assertThat(storage.getHotBlockByBlockRoot(dataStructureUtil.randomBytes32())).isEmpty();
  }

  @Test
  void shouldStoreCheckpoint() {
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();
    try (final SqlChainStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeCheckpoint(CheckpointType.FINALIZED, checkpoint);
      transaction.commit();
    }

    assertThat(storage.getCheckpoint(CheckpointType.FINALIZED)).contains(checkpoint);
  }

  @Test
  void shouldClearCheckpoint() {
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();
    try (final SqlChainStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeCheckpoint(CheckpointType.FINALIZED, checkpoint);
      transaction.clearCheckpoint(CheckpointType.FINALIZED);
      transaction.commit();
    }

    assertThat(storage.getCheckpoint(CheckpointType.FINALIZED)).isEmpty();
  }

  @Test
  void shouldStoreStateRoot() {
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot();
    try (final SqlChainStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeStateRoots(Map.of(stateRoot, slotAndBlockRoot));
      transaction.commit();
    }

    assertThat(storage.getSlotAndBlockRootByStateRoot(stateRoot)).contains(slotAndBlockRoot);
  }

  @Test
  void shouldStoreState() {
    final SignedBlockAndState blockAndState =
        dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final BeaconState state = blockAndState.getState();
    final Bytes32 blockRoot = blockAndState.getRoot();
    try (final SqlChainStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeBlock(blockAndState.getBlock(), false);
      transaction.storeState(blockRoot, state);
      transaction.commit();
    }

    assertThat(storage.getStateByBlockRoot(blockRoot)).contains(state);
    assertThat(storage.getHotStateByBlockRoot(blockRoot)).contains(state);
    assertThat(storage.getSlotAndBlockRootByStateRoot(state.hashTreeRoot()))
        .contains(new SlotAndBlockRoot(state.getSlot(), blockRoot));
  }

  @Test
  void shouldExcludeFinalizedStateFromGetHotState() {
    final SignedBlockAndState blockAndState =
        dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final BeaconState state = blockAndState.getState();
    final Bytes32 blockRoot = blockAndState.getRoot();
    try (final SqlChainStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeBlock(blockAndState.getBlock(), true);
      transaction.storeState(blockRoot, state);
      transaction.commit();
    }

    assertThat(storage.getStateByBlockRoot(blockRoot)).contains(state);
    assertThat(storage.getHotStateByBlockRoot(blockRoot)).isEmpty();
    assertThat(storage.getSlotAndBlockRootByStateRoot(state.hashTreeRoot()))
        .contains(new SlotAndBlockRoot(state.getSlot(), blockRoot));
  }

  @Test
  void shouldDeleteStatesWhenBlockIsDeleted() {
    final SignedBlockAndState blockAndState =
        dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final BeaconState state = blockAndState.getState();
    final Bytes32 blockRoot = blockAndState.getRoot();

    try (final SqlChainStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeBlock(blockAndState.getBlock(), false);
      transaction.storeState(blockRoot, state);
      transaction.deleteHotBlockByBlockRoot(blockAndState.getRoot());
      transaction.commit();
    }

    assertThat(storage.getStateByBlockRoot(blockRoot)).isEmpty();
  }

  @Test
  void shouldNotDeleteStateWhenBlockIsNotDeletedBecauseItIsFinalised() {
    final SignedBlockAndState blockAndState =
        dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final BeaconState state = blockAndState.getState();
    final Bytes32 blockRoot = blockAndState.getRoot();

    try (final SqlChainStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeBlock(blockAndState.getBlock(), true);
      transaction.storeState(blockRoot, state);
      transaction.deleteHotBlockByBlockRoot(blockAndState.getRoot());
      transaction.commit();
    }

    assertThat(storage.getStateByBlockRoot(blockRoot)).contains(state);
  }

  @Test
  void shouldPruneFinalizedStates() {
    final SignedBlockAndState blockAndState1 =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(1));
    final SignedBlockAndState blockAndState2 =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(2));
    final SignedBlockAndState blockAndState3 =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(3));
    try (final SqlChainStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeBlock(blockAndState1.getBlock(), true);
      transaction.storeBlock(blockAndState2.getBlock(), true);
      transaction.storeBlock(blockAndState3.getBlock(), true);
      transaction.storeState(blockAndState1.getRoot(), blockAndState1.getState());
      transaction.storeState(blockAndState2.getRoot(), blockAndState2.getState());
      transaction.storeState(blockAndState3.getRoot(), blockAndState3.getState());
      transaction.pruneFinalizedStates();
      transaction.commit();
    }

    assertThat(storage.getStateByBlockRoot(blockAndState1.getRoot())).isEmpty();
    assertThat(storage.getStateByBlockRoot(blockAndState2.getRoot())).isEmpty();

    // Latest finalized state must always be available.
    assertThat(storage.getStateByBlockRoot(blockAndState3.getRoot()))
        .contains(blockAndState3.getState());
  }

  @Test
  void shouldTrimFinalizedStates() {
    final SignedBlockAndState blockAndState1 =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(1));
    final SignedBlockAndState blockAndState2 =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(2));
    final SignedBlockAndState blockAndState3 =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(3));
    final SignedBlockAndState blockAndState6 =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(6));
    final SignedBlockAndState blockAndState10 =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(10));
    final SignedBlockAndState blockAndState11 =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(11));
    final SignedBlockAndState blockAndState13 =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(13));
    try (final SqlChainStorage.Transaction transaction = storage.startTransaction()) {
      for (SignedBlockAndState blockAndState :
          List.of(
              blockAndState1,
              blockAndState2,
              blockAndState3,
              blockAndState6,
              blockAndState10,
              blockAndState11,
              blockAndState13)) {
        transaction.storeBlock(blockAndState.getBlock(), true);
        transaction.storeState(blockAndState.getRoot(), blockAndState.getState());
      }
      transaction.trimFinalizedStates(UInt64.valueOf(2), UInt64.valueOf(12), UInt64.valueOf(3));
      transaction.commit();
    }

    assertThat(storage.getStateByBlockRoot(blockAndState1.getRoot())).isPresent();
    assertThat(storage.getStateByBlockRoot(blockAndState2.getRoot())).isPresent();
    assertThat(storage.getStateByBlockRoot(blockAndState3.getRoot())).isEmpty();
    assertThat(storage.getStateByBlockRoot(blockAndState6.getRoot())).isPresent();
    assertThat(storage.getStateByBlockRoot(blockAndState10.getRoot())).isPresent();
    assertThat(storage.getStateByBlockRoot(blockAndState11.getRoot())).isEmpty();
    assertThat(storage.getStateByBlockRoot(blockAndState13.getRoot())).isPresent();

    // Latest finalized state must always be available.
    assertThat(storage.getStateByBlockRoot(blockAndState13.getRoot()))
        .contains(blockAndState13.getState());
  }

  @Test
  void shouldRoundTripVotes() {
    final Map<UInt64, VoteTracker> votes =
        Map.of(
            UInt64.valueOf(10), dataStructureUtil.randomVoteTracker(),
            UInt64.valueOf(15), dataStructureUtil.randomVoteTracker(),
            UInt64.valueOf(232), dataStructureUtil.randomVoteTracker(),
            UInt64.valueOf(23234), dataStructureUtil.randomVoteTracker());
    try (final SqlChainStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeVotes(votes);
      transaction.commit();
    }

    assertThat(storage.getVotes()).isEqualTo(votes);
  }
}
