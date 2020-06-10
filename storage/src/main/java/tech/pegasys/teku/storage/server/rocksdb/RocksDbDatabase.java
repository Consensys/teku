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
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
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
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbDao;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbDao.Updater;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.V3RocksDbDao;
import tech.pegasys.teku.storage.server.rocksdb.schema.V3Schema;
import tech.pegasys.teku.storage.store.StoreFactory;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.config.StateStorageMode;

public class RocksDbDatabase implements Database {

  private final StateStorageMode stateStorageMode;

  private final RocksDbDao dao;

  public static Database createV3(
      final RocksDbConfiguration configuration, final StateStorageMode stateStorageMode) {
    final RocksDbAccessor db = RocksDbInstanceFactory.create(configuration, V3Schema.class);
    return createV3(db, stateStorageMode);
  }

  static Database createV3(final RocksDbAccessor db, final StateStorageMode stateStorageMode) {
    final RocksDbDao dao = new V3RocksDbDao(db);
    return new RocksDbDatabase(dao, stateStorageMode);
  }

  private RocksDbDatabase(final RocksDbDao dao, final StateStorageMode stateStorageMode) {
    this.stateStorageMode = stateStorageMode;
    this.dao = dao;
  }

  @Override
  public void storeGenesis(final UpdatableStore store) {
    try (final Updater updater = dao.updater()) {
      updater.setGenesisTime(store.getGenesisTime());
      updater.setJustifiedCheckpoint(store.getJustifiedCheckpoint());
      updater.setBestJustifiedCheckpoint(store.getBestJustifiedCheckpoint());
      updater.setFinalizedCheckpoint(store.getFinalizedCheckpoint());

      // We should only have a single checkpoint state at genesis
      final BeaconState genesisState =
          store.getBlockState(store.getFinalizedCheckpoint().getRoot());
      updater.addCheckpointState(store.getFinalizedCheckpoint(), genesisState);
      updater.setLatestFinalizedState(genesisState);

      for (Bytes32 root : store.getBlockRoots()) {
        // Since we're storing genesis, we should only have 1 root here corresponding to genesis
        final SignedBeaconBlock block = store.getSignedBlock(root);
        final BeaconState state = store.getBlockState(root);

        // We need to store the genesis block in both hot and cold storage so that on restart
        // we're guaranteed to have at least one block / state to load into RecentChainData.
        // Save to hot storage
        updater.addHotBlock(block);
        // Save to cold storage
        updater.addFinalizedBlock(block);
        putFinalizedState(updater, root, state);
      }

      updater.commit();
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
    Optional<UnsignedLong> maybeGenesisTime = dao.getGenesisTime();
    if (maybeGenesisTime.isEmpty()) {
      // If genesis time hasn't been set, genesis hasn't happened and we have no data
      return Optional.empty();
    }
    final UnsignedLong genesisTime = maybeGenesisTime.get();
    final Checkpoint justifiedCheckpoint = dao.getJustifiedCheckpoint().orElseThrow();
    final Checkpoint finalizedCheckpoint = dao.getFinalizedCheckpoint().orElseThrow();
    final Checkpoint bestJustifiedCheckpoint = dao.getBestJustifiedCheckpoint().orElseThrow();
    final BeaconState finalizedState = dao.getLatestFinalizedState().orElseThrow();

    final Map<Bytes32, SignedBeaconBlock> hotBlocksByRoot = dao.getHotBlocks();
    final Map<Checkpoint, BeaconState> checkpointStates = dao.getCheckpointStates();
    final Map<UnsignedLong, VoteTracker> votes = dao.getVotes();

    // Validate finalized data is consistent and available
    final SignedBeaconBlock finalizedBlock = hotBlocksByRoot.get(finalizedCheckpoint.getRoot());
    checkNotNull(finalizedBlock);
    checkState(
        finalizedBlock.getMessage().getState_root().equals(finalizedState.hash_tree_root()),
        "Latest finalized state does not match latest finalized block");

    return Optional.of(
        StoreFactory.createByRegeneratingHotStates(
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
    return dao.getFinalizedRootAtSlot(slot);
  }

  @Override
  public Optional<Bytes32> getLatestFinalizedRootAtSlot(final UnsignedLong slot) {
    return dao.getLatestFinalizedRootAtSlot(slot);
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return dao.getHotBlock(root).or(() -> dao.getFinalizedBlock(root));
  }

  @Override
  public Optional<BeaconState> getFinalizedState(final Bytes32 root) {
    return dao.getFinalizedState(root);
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return dao.getMinGenesisTimeBlock();
  }

  @Override
  @MustBeClosed
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return dao.streamDepositsFromBlocks();
  }

  @Override
  public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
    try (final Updater updater = dao.updater()) {
      updater.addMinGenesisTimeBlock(event);
    }
  }

  @Override
  public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
    try (final Updater updater = dao.updater()) {
      updater.addDepositsFromBlockEvent(event);
    }
  }

  @Override
  public void close() throws Exception {
    dao.close();
  }

  private void doUpdate(final StorageUpdate update) {
    try (final Updater updater = dao.updater()) {
      update.getGenesisTime().ifPresent(updater::setGenesisTime);
      update.getFinalizedCheckpoint().ifPresent(updater::setFinalizedCheckpoint);
      update.getJustifiedCheckpoint().ifPresent(updater::setJustifiedCheckpoint);
      update.getBestJustifiedCheckpoint().ifPresent(updater::setBestJustifiedCheckpoint);

      updater.addCheckpointStates(update.getCheckpointStates());
      updater.addHotBlocks(update.getHotBlocks());
      updater.addVotes(update.getVotes());

      // Update finalized blocks and states
      putFinalizedStates(updater, update.getFinalizedBlocks(), update.getFinalizedStates());
      update.getFinalizedBlocks().forEach(updater::addFinalizedBlock);
      update.getLatestFinalizedState().ifPresent(updater::setLatestFinalizedState);

      // Delete data
      update.getDeletedCheckpointStates().forEach(updater::deleteCheckpointState);
      update.getDeletedHotBlocks().forEach(updater::deleteHotBlock);

      updater.commit();
    }
  }

  private void putFinalizedStates(
      Updater updater,
      final Collection<SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates) {
    if (finalizedBlocks.isEmpty()) {
      return;
    }

    switch (stateStorageMode) {
      case ARCHIVE:
        final Bytes32 finalizedRoot = dao.getFinalizedCheckpoint().get().getRoot();
        final SignedBeaconBlock latestFinalizedBlock =
            dao.getFinalizedBlock(finalizedRoot).orElseThrow();
        final BeaconState latestFinalizedState =
            dao.getLatestFinalizedState()
                .orElseThrow(() -> new IllegalStateException("Missing latest finalized state"));

        final BlockTree blockTree =
            BlockTree.builder().rootBlock(latestFinalizedBlock).blocks(finalizedBlocks).build();
        final StateGenerator stateGenerator =
            StateGenerator.create(blockTree, latestFinalizedState, finalizedStates);
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
      Updater updater, final Bytes32 blockRoot, final BeaconState state) {
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
