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

package tech.pegasys.artemis.storage.server.rocksdb;

import static com.google.common.primitives.UnsignedLong.ZERO;

import com.google.common.collect.Streams;
import com.google.common.primitives.UnsignedLong;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.forkchoice.VoteTracker;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.StorageUpdate;
import tech.pegasys.artemis.storage.events.StorageUpdateResult;
import tech.pegasys.artemis.storage.server.Database;
import tech.pegasys.artemis.storage.server.rocksdb.core.ColumnEntry;
import tech.pegasys.artemis.storage.server.rocksdb.core.RocksDbInstance;
import tech.pegasys.artemis.storage.server.rocksdb.core.RocksDbInstanceFactory;
import tech.pegasys.artemis.storage.server.rocksdb.dataaccess.RocksDbDao;
import tech.pegasys.artemis.storage.server.rocksdb.dataaccess.RocksDbDao.Updater;
import tech.pegasys.artemis.storage.server.rocksdb.dataaccess.V3RocksDbDao;
import tech.pegasys.artemis.storage.server.rocksdb.schema.V3Schema;
import tech.pegasys.artemis.util.config.StateStorageMode;

public class RocksDbDatabase implements Database {

  private static final Logger LOG = LogManager.getLogger();
  private final StateStorageMode stateStorageMode;

  private final RocksDbDao dao;

  public static Database createV3(
      final RocksDbConfiguration configuration, final StateStorageMode stateStorageMode) {
    final RocksDbInstance db = RocksDbInstanceFactory.create(configuration, V3Schema.class);
    final RocksDbDao dao = new V3RocksDbDao(db);
    return new RocksDbDatabase(dao, stateStorageMode);
  }

  private RocksDbDatabase(final RocksDbDao dao, final StateStorageMode stateStorageMode) {
    this.stateStorageMode = stateStorageMode;
    this.dao = dao;
  }

  @Override
  public void storeGenesis(final Store store) {
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
        updater.addHotState(root, state);
        // Save to cold storage
        updater.addFinalizedBlock(block);
        putFinalizedState(updater, root, state);
      }

      updater.commit();
    }
  }

  @Override
  public StorageUpdateResult update(final StorageUpdate event) {
    if (event.isEmpty()) {
      return StorageUpdateResult.successfulWithNothingPruned();
    }
    return doUpdate(event);
  }

  @Override
  public Optional<Store> createMemoryStore() {
    Optional<UnsignedLong> maybeGenesisTime = dao.getGenesisTime();
    if (maybeGenesisTime.isEmpty()) {
      // If genesis time hasn't been set, genesis hasn't happened and we have no data
      return Optional.empty();
    }
    final UnsignedLong genesisTime = maybeGenesisTime.get();
    final Checkpoint justifiedCheckpoint = dao.getJustifiedCheckpoint().orElseThrow();
    final Checkpoint finalizedCheckpoint = dao.getFinalizedCheckpoint().orElseThrow();
    final Checkpoint bestJustifiedCheckpoint = dao.getBestJustifiedCheckpoint().orElseThrow();

    final Map<Bytes32, SignedBeaconBlock> hotBlocksByRoot = dao.getHotBlocks();
    final Map<Bytes32, BeaconState> hotStatesByRoot = dao.getHotStates();
    final Map<Checkpoint, BeaconState> checkpointStates = dao.getCheckpointStates();
    final Map<UnsignedLong, VoteTracker> votes = dao.getVotes();

    return Optional.of(
        new Store(
            UnsignedLong.valueOf(Instant.now().getEpochSecond()),
            genesisTime,
            justifiedCheckpoint,
            finalizedCheckpoint,
            bestJustifiedCheckpoint,
            hotBlocksByRoot,
            hotStatesByRoot,
            checkpointStates,
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
  public Optional<BeaconState> getState(final Bytes32 root) {
    return dao.getHotState(root).or(() -> dao.getFinalizedState(root));
  }

  @Override
  public void close() throws Exception {
    dao.close();
  }

  private Checkpoint getFinalizedCheckpoint() {
    return dao.getFinalizedCheckpoint().orElseThrow();
  }

  private StorageUpdateResult doUpdate(final StorageUpdate update) {
    try (final Updater updater = dao.updater()) {
      final Checkpoint previousFinalizedCheckpoint = getFinalizedCheckpoint();
      final Checkpoint newFinalizedCheckpoint =
          update.getFinalizedCheckpoint().orElse(previousFinalizedCheckpoint);

      update.getGenesisTime().ifPresent(updater::setGenesisTime);
      update.getFinalizedCheckpoint().ifPresent(updater::setFinalizedCheckpoint);
      update.getJustifiedCheckpoint().ifPresent(updater::setJustifiedCheckpoint);
      update.getBestJustifiedCheckpoint().ifPresent(updater::setBestJustifiedCheckpoint);

      updater.addCheckpointStates(update.getCheckpointStates());
      updater.addHotBlocks(update.getBlocks());
      updater.addHotStates(update.getBlockStates());
      updater.addVotes(update.getVotes());

      final StorageUpdateResult result;
      if (previousFinalizedCheckpoint == null
          || !previousFinalizedCheckpoint.equals(newFinalizedCheckpoint)) {
        recordFinalizedBlocks(updater, update, newFinalizedCheckpoint);
        final Set<Checkpoint> prunedCheckpoints =
            pruneCheckpointStates(updater, update, newFinalizedCheckpoint);
        final Set<Bytes32> prunedBlockRoots =
            pruneHotBlocks(updater, update, newFinalizedCheckpoint);
        result = StorageUpdateResult.successful(prunedBlockRoots, prunedCheckpoints);
      } else {
        result = StorageUpdateResult.successfulWithNothingPruned();
      }
      updater.commit();
      return result;
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

  private Set<Checkpoint> pruneCheckpointStates(
      final Updater updater, final StorageUpdate update, final Checkpoint newFinalizedCheckpoint) {
    final Set<Checkpoint> prunedCheckpoints = new HashSet<>();
    try (final Stream<ColumnEntry<Checkpoint, BeaconState>> stream = dao.streamCheckpointStates()) {
      Streams.concat(stream, update.getCheckpointStates().entrySet().stream())
          .filter(e -> e.getKey().getEpoch().compareTo(newFinalizedCheckpoint.getEpoch()) < 0)
          .forEach(
              entry -> {
                updater.deleteCheckpointState(entry.getKey());
                prunedCheckpoints.add(entry.getKey());
              });
    }
    return prunedCheckpoints;
  }

  private Set<Bytes32> pruneHotBlocks(
      final Updater updater, final StorageUpdate update, final Checkpoint newFinalizedCheckpoint) {
    Optional<SignedBeaconBlock> newlyFinalizedBlock =
        getHotBlock(update, newFinalizedCheckpoint.getRoot());
    if (newlyFinalizedBlock.isEmpty()) {
      LOG.error(
          "Missing finalized block {} for epoch {}",
          newFinalizedCheckpoint.getRoot(),
          newFinalizedCheckpoint.getEpoch());
      return Collections.emptySet();
    }
    final UnsignedLong finalizedSlot = newlyFinalizedBlock.get().getSlot();
    return updater.pruneHotBlocksAtSlotsOlderThan(finalizedSlot);
  }

  private void recordFinalizedBlocks(
      Updater updater, final StorageUpdate update, final Checkpoint newFinalizedCheckpoint) {
    LOG.debug(
        "Record finalized blocks for epoch {} starting at block {}",
        newFinalizedCheckpoint.getEpoch(),
        newFinalizedCheckpoint.getRoot());

    final UnsignedLong highestFinalizedSlot = dao.getHighestFinalizedSlot().orElse(ZERO);
    Bytes32 newlyFinalizedBlockRoot = newFinalizedCheckpoint.getRoot();
    Optional<SignedBeaconBlock> newlyFinalizedBlock = getHotBlock(update, newlyFinalizedBlockRoot);
    boolean isLatestFinalizedBlock = true;
    while (newlyFinalizedBlock.isPresent()
        && newlyFinalizedBlock.get().getSlot().compareTo(highestFinalizedSlot) > 0) {
      LOG.debug(
          "Recording finalized block {} at slot {}",
          newlyFinalizedBlockRoot,
          newlyFinalizedBlock.get().getSlot());
      updater.addFinalizedBlock(newlyFinalizedBlock.get());
      final Optional<BeaconState> finalizedState = getHotState(update, newlyFinalizedBlockRoot);
      if (finalizedState.isPresent()) {
        putFinalizedState(updater, newlyFinalizedBlockRoot, finalizedState.get());
        if (isLatestFinalizedBlock) {
          updater.setLatestFinalizedState(finalizedState.get());
          isLatestFinalizedBlock = false;
        }
      } else {
        LOG.error(
            "Missing finalized state {} for epoch {}",
            newlyFinalizedBlockRoot,
            newFinalizedCheckpoint.getEpoch());
      }

      // Update for next round of iteration
      newlyFinalizedBlockRoot = newlyFinalizedBlock.get().getMessage().getParent_root();
      newlyFinalizedBlock = getHotBlock(update, newlyFinalizedBlockRoot);
    }

    if (newlyFinalizedBlock.isEmpty()) {
      LOG.error(
          "Missing finalized block {} for epoch {}",
          newlyFinalizedBlockRoot,
          newFinalizedCheckpoint.getEpoch());
    }
  }

  private Optional<SignedBeaconBlock> getHotBlock(final StorageUpdate update, final Bytes32 root) {
    return Optional.ofNullable(update.getBlocks().get(root)).or(() -> dao.getHotBlock(root));
  }

  private Optional<BeaconState> getHotState(final StorageUpdate update, final Bytes32 root) {
    return Optional.ofNullable(update.getBlockStates().get(root)).or(() -> dao.getHotState(root));
  }
}
