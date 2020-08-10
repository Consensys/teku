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

package tech.pegasys.teku.storage.server.rocksdb;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.storage.server.AbstractStorageBackedDatabaseTest;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbEth1Dao;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbFinalizedDao;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbHotDao;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public abstract class AbstractRocksDbDatabaseTest extends AbstractStorageBackedDatabaseTest {

  @Test
  public void shouldThrowIfClosedDatabaseIsModified_setGenesis() throws Exception {
    database.close();
    assertThatThrownBy(() -> database.storeGenesis(genesisAnchor))
        .isInstanceOf(ShuttingDownException.class);
  }

  @Test
  public void shouldThrowIfClosedDatabaseIsModified_update() throws Exception {
    database.storeGenesis(genesisAnchor);
    database.close();

    final SignedBlockAndState newValue = chainBuilder.generateBlockAtSlot(1);
    // Sanity check
    assertThatSafeFuture(store.retrieveBlockState(newValue.getRoot()))
        .isCompletedWithEmptyOptional();
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.putBlockAndState(newValue);

    final SafeFuture<Void> result = transaction.commit();
    assertThatThrownBy(result::get).hasCauseInstanceOf(ShuttingDownException.class);
  }

  @Test
  public void shouldThrowIfClosedDatabaseIsRead_createMemoryStore() throws Exception {
    database.storeGenesis(genesisAnchor);
    database.close();

    assertThatThrownBy(database::createMemoryStore).isInstanceOf(ShuttingDownException.class);
  }

  @Test
  public void shouldThrowIfClosedDatabaseIsRead_getSlotForFinalizedBlockRoot() throws Exception {
    database.storeGenesis(genesisAnchor);
    database.close();

    assertThatThrownBy(() -> database.getSlotForFinalizedBlockRoot(Bytes32.ZERO))
        .isInstanceOf(ShuttingDownException.class);
  }

  @Test
  public void shouldThrowIfClosedDatabaseIsRead_getSignedBlock() throws Exception {
    database.storeGenesis(genesisAnchor);
    database.close();

    assertThatThrownBy(() -> database.getSignedBlock(genesisCheckpoint.getRoot()))
        .isInstanceOf(ShuttingDownException.class);
  }

  @Test
  public void shouldThrowIfClosedDatabaseIsRead_streamFinalizedBlocks() throws Exception {
    database.storeGenesis(genesisAnchor);
    database.close();

    assertThatThrownBy(() -> database.streamFinalizedBlocks(UInt64.ZERO, UInt64.ONE))
        .isInstanceOf(ShuttingDownException.class);
  }

  @Test
  public void shouldThrowIfClosedDatabaseIsRead_streamFinalizedBlocksShuttingDown()
      throws Exception {
    database.storeGenesis(genesisAnchor);
    try (final Stream<SignedBeaconBlock> stream =
        database.streamFinalizedBlocks(UInt64.ZERO, UInt64.valueOf(1000L))) {
      database.close();
      assertThatThrownBy(stream::findAny).isInstanceOf(ShuttingDownException.class);
    }
  }

  @Test
  public void shouldThrowIfTransactionModifiedAfterDatabaseIsClosed_updateHotDao()
      throws Exception {
    database.storeGenesis(genesisAnchor);

    try (final RocksDbHotDao.HotUpdater updater =
        ((RocksDbDatabase) database).hotDao.hotUpdater()) {
      SignedBlockAndState newBlock = chainBuilder.generateNextBlock();
      database.close();
      assertThatThrownBy(() -> updater.addHotBlock(newBlock.getBlock()))
          .isInstanceOf(ShuttingDownException.class);
    }
  }

  @Test
  public void shouldThrowIfTransactionModifiedAfterDatabaseIsClosed_updateFinalizedDao()
      throws Exception {
    database.storeGenesis(genesisAnchor);

    try (final RocksDbFinalizedDao.FinalizedUpdater updater =
        ((RocksDbDatabase) database).finalizedDao.finalizedUpdater()) {
      SignedBlockAndState newBlock = chainBuilder.generateNextBlock();
      database.close();
      assertThatThrownBy(() -> updater.addFinalizedBlock(newBlock.getBlock()))
          .isInstanceOf(ShuttingDownException.class);
    }
  }

  @Test
  public void shouldThrowIfTransactionModifiedAfterDatabaseIsClosed_updateEth1Dao()
      throws Exception {
    database.storeGenesis(genesisAnchor);

    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    try (final RocksDbEth1Dao.Eth1Updater updater =
        ((RocksDbDatabase) database).eth1Dao.eth1Updater()) {
      final MinGenesisTimeBlockEvent genesisTimeBlockEvent =
          dataStructureUtil.randomMinGenesisTimeBlockEvent(1);
      database.close();
      assertThatThrownBy(() -> updater.addMinGenesisTimeBlock(genesisTimeBlockEvent))
          .isInstanceOf(ShuttingDownException.class);
    }
  }

  @Test
  public void shouldThrowIfClosedDatabaseIsRead_getHistoricalState() throws Exception {
    // Store genesis
    database.storeGenesis(genesisAnchor);
    // Add a new finalized block to supersede genesis
    final SignedBlockAndState newBlock = chainBuilder.generateBlockAtSlot(1);
    final Checkpoint newCheckpoint = getCheckpointForBlock(newBlock.getBlock());
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.putBlockAndState(newBlock);
    transaction.setFinalizedCheckpoint(newCheckpoint);
    transaction.commit().reportExceptions();
    // Close db
    database.close();

    assertThatThrownBy(
            () -> database.getLatestAvailableFinalizedState(genesisCheckpoint.getEpochStartSlot()))
        .isInstanceOf(ShuttingDownException.class);
  }
}
