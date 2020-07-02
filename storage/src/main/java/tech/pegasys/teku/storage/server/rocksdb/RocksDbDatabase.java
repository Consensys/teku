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
import static tech.pegasys.teku.metrics.TekuMetricCategory.STORAGE_FINALIZED_DB;
import static tech.pegasys.teku.metrics.TekuMetricCategory.STORAGE_HOT_DB;

import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.stategenerator.StateGenerator;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
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
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbProtoArrayDao;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.V3RocksDbDao;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.V4FinalizedRocksDbDao;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.V4HotRocksDbDao;
import tech.pegasys.teku.storage.server.rocksdb.schema.V3Schema;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaFinalized;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaHot;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.StateStorageMode;

public class RocksDbDatabase implements Database {

  private final MetricsSystem metricsSystem;
  private final StateStorageMode stateStorageMode;

  final RocksDbHotDao hotDao;
  final RocksDbFinalizedDao finalizedDao;
  final RocksDbEth1Dao eth1Dao;
  private final RocksDbProtoArrayDao protoArrayDao;

  public static Database createV3(
      final MetricsSystem metricsSystem,
      final RocksDbConfiguration configuration,
      final StateStorageMode stateStorageMode) {
    final RocksDbAccessor db =
        RocksDbInstanceFactory.create(metricsSystem, STORAGE_HOT_DB, configuration, V3Schema.class);
    return createV3(metricsSystem, db, stateStorageMode);
  }

  public static Database createV4(
      final MetricsSystem metricsSystem,
      final RocksDbConfiguration hotConfiguration,
      final RocksDbConfiguration finalizedConfiguration,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency) {
    final RocksDbAccessor hotDb =
        RocksDbInstanceFactory.create(
            metricsSystem, STORAGE_HOT_DB, hotConfiguration, V4SchemaHot.class);
    final RocksDbAccessor finalizedDb =
        RocksDbInstanceFactory.create(
            metricsSystem, STORAGE_FINALIZED_DB, finalizedConfiguration, V4SchemaFinalized.class);
    return createV4(metricsSystem, hotDb, finalizedDb, stateStorageMode, stateStorageFrequency);
  }

  static Database createV3(
      final MetricsSystem metricsSystem,
      final RocksDbAccessor db,
      final StateStorageMode stateStorageMode) {
    final V3RocksDbDao dao = new V3RocksDbDao(db);
    return new RocksDbDatabase(metricsSystem, dao, dao, dao, dao, stateStorageMode);
  }

  static Database createV4(
      final MetricsSystem metricsSystem,
      final RocksDbAccessor hotDb,
      final RocksDbAccessor finalizedDb,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency) {
    final V4HotRocksDbDao dao = new V4HotRocksDbDao(hotDb);
    final V4FinalizedRocksDbDao finalizedDbDao =
        new V4FinalizedRocksDbDao(finalizedDb, stateStorageFrequency);
    return new RocksDbDatabase(metricsSystem, dao, finalizedDbDao, dao, dao, stateStorageMode);
  }

  private RocksDbDatabase(
      final MetricsSystem metricsSystem,
      final RocksDbHotDao hotDao,
      final RocksDbFinalizedDao finalizedDao,
      final RocksDbEth1Dao eth1Dao,
      final RocksDbProtoArrayDao protoArrayDao,
      final StateStorageMode stateStorageMode) {
    this.metricsSystem = metricsSystem;
    this.finalizedDao = finalizedDao;
    this.eth1Dao = eth1Dao;
    this.protoArrayDao = protoArrayDao;
    this.stateStorageMode = stateStorageMode;
    this.hotDao = hotDao;
  }

  @Override
  public void storeGenesis(final UpdatableStore store) {
    try (final HotUpdater hotUpdater = hotDao.hotUpdater();
        final FinalizedUpdater finalizedUpdater = finalizedDao.finalizedUpdater()) {
      // We should only have a single block / state / checkpoint at genesis
      final Checkpoint genesisCheckpoint = store.getFinalizedCheckpoint();
      final Bytes32 genesisRoot = genesisCheckpoint.getRoot();
      final BeaconState genesisState = store.getBlockState(genesisRoot);
      final SignedBeaconBlock genesisBlock = store.getSignedBlock(genesisRoot);

      hotUpdater.setGenesisTime(store.getGenesisTime());
      hotUpdater.setJustifiedCheckpoint(genesisCheckpoint);
      hotUpdater.setBestJustifiedCheckpoint(genesisCheckpoint);
      hotUpdater.setFinalizedCheckpoint(genesisCheckpoint);
      hotUpdater.setLatestFinalizedState(genesisState);

      // We need to store the genesis block in both hot and cold storage so that on restart
      // we're guaranteed to have at least one block / state to load into RecentChainData.
      // Save to hot storage
      hotUpdater.addHotBlock(genesisBlock);
      // Save to cold storage
      finalizedUpdater.addFinalizedBlock(genesisBlock);
      putFinalizedState(finalizedUpdater, genesisRoot, genesisState);

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
  public Optional<StoreBuilder> createMemoryStore() {
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

    final Map<UnsignedLong, VoteTracker> votes = hotDao.getVotes();

    // Build child-parent lookup
    final Map<Bytes32, Bytes32> childToParentLookup = new HashMap<>();
    try (final Stream<SignedBeaconBlock> hotBlocks = hotDao.streamHotBlocks()) {
      hotBlocks.forEach(b -> childToParentLookup.put(b.getRoot(), b.getParent_root()));
    }

    // Validate finalized data is consistent and available
    final SignedBeaconBlock finalizedBlock =
        hotDao.getHotBlock(finalizedCheckpoint.getRoot()).orElse(null);
    checkNotNull(finalizedBlock);
    checkState(
        finalizedBlock.getMessage().getState_root().equals(finalizedState.hash_tree_root()),
        "Latest finalized state does not match latest finalized block");
    final SignedBlockAndState latestFinalized =
        new SignedBlockAndState(finalizedBlock, finalizedState);

    return Optional.of(
        StoreBuilder.create()
            .metricsSystem(metricsSystem)
            .time(UnsignedLong.valueOf(Instant.now().getEpochSecond()))
            .genesisTime(genesisTime)
            .finalizedCheckpoint(finalizedCheckpoint)
            .justifiedCheckpoint(justifiedCheckpoint)
            .bestJustifiedCheckpoint(bestJustifiedCheckpoint)
            .childToParentMap(childToParentLookup)
            .latestFinalized(latestFinalized)
            .votes(votes));
  }

  @Override
  public Optional<UnsignedLong> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return finalizedDao.getSlotForFinalizedBlockRoot(blockRoot);
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UnsignedLong slot) {
    return finalizedDao.getFinalizedBlockAtSlot(slot);
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UnsignedLong slot) {
    return finalizedDao.getLatestFinalizedBlockAtSlot(slot);
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UnsignedLong maxSlot) {
    return finalizedDao.getLatestAvailableFinalizedState(maxSlot);
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return hotDao.getHotBlock(root).or(() -> finalizedDao.getFinalizedBlock(root));
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots) {
    return blockRoots.stream()
        .flatMap(root -> hotDao.getHotBlock(root).stream())
        .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UnsignedLong startSlot, final UnsignedLong endSlot) {
    return finalizedDao.streamFinalizedBlocks(startSlot, endSlot);
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
  public Optional<ProtoArraySnapshot> getProtoArraySnapshot() {
    return protoArrayDao.getProtoArraySnapshot();
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
  public void putProtoArraySnapshot(final ProtoArraySnapshot protoArraySnapshot) {
    try (final RocksDbProtoArrayDao.ProtoArrayUpdater updater = protoArrayDao.protoArrayUpdater()) {
      updater.putProtoArraySnapshot(protoArraySnapshot);
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
    // Update finalized blocks and states
    updateFinalizedData(
        update.getFinalizedChildToParentMap(),
        update.getFinalizedBlocks(),
        update.getFinalizedStates());

    try (final HotUpdater updater = hotDao.hotUpdater()) {
      // Store new hot data
      update.getGenesisTime().ifPresent(updater::setGenesisTime);
      update.getFinalizedCheckpoint().ifPresent(updater::setFinalizedCheckpoint);
      update.getJustifiedCheckpoint().ifPresent(updater::setJustifiedCheckpoint);
      update.getBestJustifiedCheckpoint().ifPresent(updater::setBestJustifiedCheckpoint);
      update.getLatestFinalizedState().ifPresent(updater::setLatestFinalizedState);

      updater.addHotBlocks(update.getHotBlocks());
      updater.addVotes(update.getVotes());

      // Delete finalized data from hot db
      update.getDeletedHotBlocks().forEach(updater::deleteHotBlock);

      updater.commit();
    }
  }

  private void updateFinalizedData(
      Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates) {
    if (finalizedChildToParentMap.isEmpty()) {
      // Nothing to do
      return;
    }

    try (final FinalizedUpdater updater = finalizedDao.finalizedUpdater()) {
      final BlockProvider blockProvider =
          BlockProvider.withKnownBlocks(
              roots -> SafeFuture.completedFuture(getHotBlocks(roots)), finalizedBlocks);

      switch (stateStorageMode) {
        case ARCHIVE:
          // Get previously finalized block to build on top of
          final SignedBlockAndState baseBlock = getFinalizedBlockAndState();

          final HashTree blockTree =
              HashTree.builder()
                  .rootHash(baseBlock.getRoot())
                  .childAndParentRoots(finalizedChildToParentMap)
                  .build();

          final StateGenerator stateGenerator =
              StateGenerator.create(blockTree, baseBlock, blockProvider, finalizedStates);
          // TODO - don't join, create synchronous API for synchronous blockProvider
          stateGenerator
              .regenerateAllStates(
                  (block, state) -> {
                    updater.addFinalizedBlock(block);
                    updater.addFinalizedState(block.getRoot(), state);
                  })
              .join();
          break;
        case PRUNE:
          for (Bytes32 root : finalizedChildToParentMap.keySet()) {
            SignedBeaconBlock block =
                blockProvider
                    .getBlock(root)
                    .join()
                    .orElseThrow(() -> new IllegalStateException("Missing finalized block"));
            updater.addFinalizedBlock(block);
          }
          break;
        default:
          throw new UnsupportedOperationException("Unhandled storage mode: " + stateStorageMode);
      }

      updater.commit();
    }
  }

  private SignedBlockAndState getFinalizedBlockAndState() {
    final Bytes32 baseBlockRoot = hotDao.getFinalizedCheckpoint().orElseThrow().getRoot();
    final SignedBeaconBlock baseBlock = finalizedDao.getFinalizedBlock(baseBlockRoot).orElseThrow();
    final BeaconState baseState = hotDao.getLatestFinalizedState().orElseThrow();
    return new SignedBlockAndState(baseBlock, baseState);
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
