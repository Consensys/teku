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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.core.StateGenerator;
import tech.pegasys.teku.datastructures.blocks.BlockTree;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbInstanceFactory;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbEth1Dao;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbEth1Dao.Eth1Updater;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbFinalizedDao;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbFinalizedDao.FinalizedUpdater;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbHotDao;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbHotDao.HotUpdater;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.V3RocksDbDao;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.V4FinalizedRocksDbDao;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.V4HotRocksDbDao;
import tech.pegasys.teku.storage.server.rocksdb.schema.V3Schema;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaFinalized;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaHot;
import tech.pegasys.teku.storage.store.StoreFactory;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.config.StateStorageMode;

public class RocksDbDatabase implements Database {

  private final MetricsSystem metricsSystem;
  private final StateStorageMode stateStorageMode;

  private final RocksDbHotDao hotDao;
  private final RocksDbFinalizedDao finalizedDao;
  private final RocksDbEth1Dao eth1Dao;

  public static Database createV3(
      final MetricsSystem metricsSystem,
      final RocksDbConfiguration configuration,
      final StateStorageMode stateStorageMode) {
    final RocksDbAccessor db = RocksDbInstanceFactory.create(configuration, V3Schema.class);
    return createV3(metricsSystem, db, stateStorageMode);
  }

  public static Database createV4(
      final MetricsSystem metricsSystem,
      final RocksDbConfiguration hotConfiguration,
      final RocksDbConfiguration finalizedConfiguration,
      final StateStorageMode stateStorageMode) {
    final RocksDbAccessor hotDb =
        RocksDbInstanceFactory.create(hotConfiguration, V4SchemaHot.class);
    final RocksDbAccessor finalizedDb =
        RocksDbInstanceFactory.create(finalizedConfiguration, V4SchemaFinalized.class);
    return createV4(metricsSystem, hotDb, finalizedDb, stateStorageMode);
  }

  static Database createV3(
      final MetricsSystem metricsSystem,
      final RocksDbAccessor db,
      final StateStorageMode stateStorageMode) {
    final V3RocksDbDao dao = new V3RocksDbDao(db);
    return new RocksDbDatabase(metricsSystem, dao, dao, dao, stateStorageMode);
  }

  static Database createV4(
      final MetricsSystem metricsSystem,
      final RocksDbAccessor hotDb,
      final RocksDbAccessor finalizedDb,
      final StateStorageMode stateStorageMode) {
    final V4HotRocksDbDao dao = new V4HotRocksDbDao(hotDb);
    final V4FinalizedRocksDbDao finalizedDbDao = new V4FinalizedRocksDbDao(finalizedDb);
    return new RocksDbDatabase(metricsSystem, dao, finalizedDbDao, dao, stateStorageMode);
  }

  private RocksDbDatabase(
      final MetricsSystem metricsSystem,
      final RocksDbHotDao hotDao,
      final RocksDbFinalizedDao finalizedDao,
      final RocksDbEth1Dao eth1Dao,
      final StateStorageMode stateStorageMode) {
    this.metricsSystem = metricsSystem;
    this.finalizedDao = finalizedDao;
    this.eth1Dao = eth1Dao;
    this.stateStorageMode = stateStorageMode;
    this.hotDao = hotDao;
  }

  @Override
  public void storeGenesis(final UpdatableStore store) {
    try (final HotUpdater hotUpdater = hotDao.hotUpdater();
        final FinalizedUpdater finalizedUpdater = finalizedDao.finalizedUpdater()) {
      hotUpdater.setGenesisTime(store.getGenesisTime());
      hotUpdater.setJustifiedCheckpoint(store.getJustifiedCheckpoint());
      hotUpdater.setBestJustifiedCheckpoint(store.getBestJustifiedCheckpoint());
      hotUpdater.setFinalizedCheckpoint(store.getFinalizedCheckpoint());

      // We should only have a single checkpoint state at genesis
      final BeaconState genesisState =
          store.getBlockState(store.getFinalizedCheckpoint().getRoot());
      hotUpdater.addCheckpointState(store.getFinalizedCheckpoint(), genesisState);
      hotUpdater.setLatestFinalizedState(genesisState);

      for (Bytes32 root : store.getBlockRoots()) {
        // Since we're storing genesis, we should only have 1 root here corresponding to genesis
        final SignedBeaconBlock block = store.getSignedBlock(root);
        final BeaconState state = store.getBlockState(root);

        // We need to store the genesis block in both hot and cold storage so that on restart
        // we're guaranteed to have at least one block / state to load into RecentChainData.
        // Save to hot storage
        hotUpdater.addHotBlock(block);
        // Save to cold storage
        finalizedUpdater.addFinalizedBlock(block);
        putFinalizedState(finalizedUpdater, root, state);
      }

      finalizedUpdater.commit();
      hotUpdater.commit();
    }
  }

  @Override
  public void update(final StorageUpdate event) {
    if (event.isEmpty()) {
      return;
    }
    doUpdate(event);
  }

  @Override
  public Optional<UpdatableStore> createMemoryStore() {
    Optional<UnsignedLong> maybeGenesisTime = hotDao.getGenesisTime();
    if (maybeGenesisTime.isEmpty()) {
      // If genesis time hasn't been set, genesis hasn't happened and we have no data
      return Optional.empty();
    }
    final UnsignedLong genesisTime = maybeGenesisTime.get();
    final Checkpoint justifiedCheckpoint = hotDao.getJustifiedCheckpoint().orElseThrow();
    final Checkpoint finalizedCheckpoint = hotDao.getFinalizedCheckpoint().orElseThrow();
    final Checkpoint bestJustifiedCheckpoint = hotDao.getBestJustifiedCheckpoint().orElseThrow();
    final BeaconState finalizedState = hotDao.getLatestFinalizedState().orElseThrow();

    final Map<Bytes32, SignedBeaconBlock> hotBlocksByRoot = hotDao.getHotBlocks();
    final Map<Checkpoint, BeaconState> checkpointStates = hotDao.getCheckpointStates();
    final Map<UnsignedLong, VoteTracker> votes = hotDao.getVotes();

    // Validate finalized data is consistent and available
    final SignedBeaconBlock finalizedBlock = hotBlocksByRoot.get(finalizedCheckpoint.getRoot());
    checkNotNull(finalizedBlock);
    checkState(
        finalizedBlock.getMessage().getState_root().equals(finalizedState.hash_tree_root()),
        "Latest finalized state does not match latest finalized block");

    return Optional.of(
        StoreFactory.createByRegeneratingHotStates(
            metricsSystem,
            UnsignedLong.valueOf(Instant.now().getEpochSecond()),
            genesisTime,
            justifiedCheckpoint,
            finalizedCheckpoint,
            bestJustifiedCheckpoint,
            hotBlocksByRoot,
            checkpointStates,
            finalizedState,
            votes));
  }

  @Override
  public Optional<Bytes32> getFinalizedRootAtSlot(final UnsignedLong slot) {
    return finalizedDao.getFinalizedRootAtSlot(slot);
  }

  @Override
  public Optional<Bytes32> getLatestFinalizedRootAtSlot(final UnsignedLong slot) {
    return finalizedDao.getLatestFinalizedRootAtSlot(slot);
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return hotDao.getHotBlock(root).or(() -> finalizedDao.getFinalizedBlock(root));
  }

  @Override
  public Optional<BeaconState> getFinalizedState(final Bytes32 root) {
    return finalizedDao.getFinalizedState(root);
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return eth1Dao.getMinGenesisTimeBlock();
  }

  @Override
  @MustBeClosed
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return eth1Dao.streamDepositsFromBlocks();
  }

  @Override
  public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
    try (final Eth1Updater updater = eth1Dao.eth1Updater()) {
      updater.addMinGenesisTimeBlock(event);
      updater.commit();
    }
  }

  @Override
  public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
    try (final Eth1Updater updater = eth1Dao.eth1Updater()) {
      updater.addDepositsFromBlockEvent(event);
      updater.commit();
    }
  }

  @Override
  public void close() throws Exception {
    hotDao.close();
    eth1Dao.close();
    finalizedDao.close();
  }

  private void doUpdate(final StorageUpdate update) {
    try (final FinalizedUpdater updater = finalizedDao.finalizedUpdater()) {
      // Update finalized blocks and states
      putFinalizedStates(updater, update.getFinalizedBlocks(), update.getFinalizedStates());
      update.getFinalizedBlocks().values().forEach(updater::addFinalizedBlock);
      updater.commit();
    }

    try (final HotUpdater updater = hotDao.hotUpdater()) {
      // Store new hot data
      update.getGenesisTime().ifPresent(updater::setGenesisTime);
      update.getFinalizedCheckpoint().ifPresent(updater::setFinalizedCheckpoint);
      update.getJustifiedCheckpoint().ifPresent(updater::setJustifiedCheckpoint);
      update.getBestJustifiedCheckpoint().ifPresent(updater::setBestJustifiedCheckpoint);
      update.getLatestFinalizedState().ifPresent(updater::setLatestFinalizedState);

      updater.addCheckpointStates(update.getCheckpointStates());
      updater.addHotBlocks(update.getHotBlocks());
      updater.addVotes(update.getVotes());

      // Delete finalized data from hot db
      update.getDeletedCheckpointStates().forEach(updater::deleteCheckpointState);
      update.getDeletedHotBlocks().forEach(updater::deleteHotBlock);

      updater.commit();
    }
  }

  private void putFinalizedStates(
      FinalizedUpdater updater,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates) {
    if (finalizedBlocks.isEmpty()) {
      return;
    }

    switch (stateStorageMode) {
      case ARCHIVE:
        // Get previously finalized block to build on top of
        final Bytes32 baseBlockRoot = hotDao.getFinalizedCheckpoint().orElseThrow().getRoot();
        final SignedBeaconBlock baseBlock =
            finalizedDao.getFinalizedBlock(baseBlockRoot).orElseThrow();
        final BeaconState baseState = hotDao.getLatestFinalizedState().orElseThrow();

        final BlockTree blockTree =
            BlockTree.builder().rootBlock(baseBlock).blocks(finalizedBlocks.values()).build();
        final StateGenerator stateGenerator =
            StateGenerator.create(blockTree, baseState, finalizedStates);
        stateGenerator.regenerateAllStates(updater::addFinalizedState);
        break;
      case PRUNE:
        // Don't persist finalized state
        break;
      default:
        throw new UnsupportedOperationException("Unhandled storage mode: " + stateStorageMode);
    }
  }

  private void putFinalizedState(
      FinalizedUpdater updater, final Bytes32 blockRoot, final BeaconState state) {
    switch (stateStorageMode) {
      case ARCHIVE:
        updater.addFinalizedState(blockRoot, state);
        break;
      case PRUNE:
        // Don't persist finalized state
        break;
      default:
        throw new UnsupportedOperationException("Unhandled storage mode: " + stateStorageMode);
    }
  }
}
