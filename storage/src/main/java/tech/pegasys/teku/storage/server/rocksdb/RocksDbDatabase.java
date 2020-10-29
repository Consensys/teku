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
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE_FINALIZED_DB;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE_HOT_DB;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.MustBeClosed;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.events.WeakSubjectivityState;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;
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
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.V4FinalizedRocksDbDao;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.V4HotRocksDbDao;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbColumn;
import tech.pegasys.teku.storage.server.rocksdb.schema.SchemaFinalized;
import tech.pegasys.teku.storage.server.rocksdb.schema.SchemaHot;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaFinalized;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaHot;
import tech.pegasys.teku.storage.server.state.StateRootRecorder;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.util.config.StateStorageMode;

public class RocksDbDatabase implements Database {

  private final MetricsSystem metricsSystem;
  private final StateStorageMode stateStorageMode;

  final RocksDbHotDao hotDao;
  final RocksDbFinalizedDao finalizedDao;
  final RocksDbEth1Dao eth1Dao;
  private final RocksDbProtoArrayDao protoArrayDao;

  public static Database createV4(
      final MetricsSystem metricsSystem,
      final RocksDbConfiguration hotConfiguration,
      final RocksDbConfiguration finalizedConfiguration,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency) {
    final RocksDbAccessor hotDb =
        RocksDbInstanceFactory.create(
            metricsSystem, STORAGE_HOT_DB, hotConfiguration, V4SchemaHot.INSTANCE.getAllColumns());
    final RocksDbAccessor finalizedDb =
        RocksDbInstanceFactory.create(
            metricsSystem,
            STORAGE_FINALIZED_DB,
            finalizedConfiguration,
            V4SchemaFinalized.INSTANCE.getAllColumns());
    return createV4(metricsSystem, hotDb, finalizedDb, stateStorageMode, stateStorageFrequency);
  }

  public static Database createV6(
      final MetricsSystem metricsSystem,
      final RocksDbConfiguration hotConfiguration,
      final Optional<RocksDbConfiguration> finalizedConfiguration,
      final SchemaHot schemaHot,
      final SchemaFinalized schemaFinalized,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency) {
    final RocksDbAccessor hotDb;
    final RocksDbAccessor finalizedDb;

    if (finalizedConfiguration.isPresent()) {
      hotDb =
          RocksDbInstanceFactory.create(
              metricsSystem, STORAGE_HOT_DB, hotConfiguration, schemaHot.getAllColumns());
      finalizedDb =
          RocksDbInstanceFactory.create(
              metricsSystem,
              STORAGE_FINALIZED_DB,
              finalizedConfiguration.get(),
              schemaFinalized.getAllColumns());
    } else {

      ArrayList<RocksDbColumn<?, ?>> allColumns = new ArrayList<>(schemaHot.getAllColumns());
      allColumns.addAll(schemaFinalized.getAllColumns());
      finalizedDb =
          RocksDbInstanceFactory.create(metricsSystem, STORAGE, hotConfiguration, allColumns);
      hotDb = finalizedDb;
    }
    return createV6(
        metricsSystem,
        hotDb,
        finalizedDb,
        schemaHot,
        schemaFinalized,
        stateStorageMode,
        stateStorageFrequency);
  }

  static Database createV4(
      final MetricsSystem metricsSystem,
      final RocksDbAccessor hotDb,
      final RocksDbAccessor finalizedDb,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency) {
    final V4HotRocksDbDao dao = new V4HotRocksDbDao(hotDb, V4SchemaHot.INSTANCE);
    final V4FinalizedRocksDbDao finalizedDbDao =
        new V4FinalizedRocksDbDao(finalizedDb, V4SchemaFinalized.INSTANCE, stateStorageFrequency);
    return new RocksDbDatabase(metricsSystem, dao, finalizedDbDao, dao, dao, stateStorageMode);
  }

  static Database createV6(
      final MetricsSystem metricsSystem,
      final RocksDbAccessor hotDb,
      final RocksDbAccessor finalizedDb,
      final SchemaHot schemaHot,
      final SchemaFinalized schemaFinalized,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency) {
    final V4HotRocksDbDao dao = new V4HotRocksDbDao(hotDb, schemaHot);
    final V4FinalizedRocksDbDao finalizedDbDao =
        new V4FinalizedRocksDbDao(finalizedDb, schemaFinalized, stateStorageFrequency);
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
  public void storeInitialAnchor(final AnchorPoint anchor) {
    try (final HotUpdater hotUpdater = hotDao.hotUpdater();
        final FinalizedUpdater finalizedUpdater = finalizedDao.finalizedUpdater()) {
      // We should only have a single block / state / checkpoint at anchorpoint initialization
      final Checkpoint anchorCheckpoint = anchor.getCheckpoint();
      final Bytes32 anchorRoot = anchorCheckpoint.getRoot();
      final BeaconState anchorState = anchor.getState();
      final SignedBeaconBlock anchorBlock = anchor.getBlock();

      hotUpdater.setAnchor(anchor.getCheckpoint());
      hotUpdater.setGenesisTime(anchorState.getGenesis_time());
      hotUpdater.setJustifiedCheckpoint(anchorCheckpoint);
      hotUpdater.setBestJustifiedCheckpoint(anchorCheckpoint);
      hotUpdater.setFinalizedCheckpoint(anchorCheckpoint);
      hotUpdater.setLatestFinalizedState(anchorState);

      // We need to store the anchor block in both hot and cold storage so that on restart
      // we're guaranteed to have at least one block / state to load into RecentChainData.
      // Save to hot storage
      hotUpdater.addHotBlock(anchorBlock);
      // Save to cold storage
      finalizedUpdater.addFinalizedBlock(anchorBlock);
      putFinalizedState(finalizedUpdater, anchorRoot, anchorState);

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
  public void updateWeakSubjectivityState(WeakSubjectivityUpdate weakSubjectivityUpdate) {
    try (final HotUpdater updater = hotDao.hotUpdater()) {
      Optional<Checkpoint> checkpoint = weakSubjectivityUpdate.getWeakSubjectivityCheckpoint();
      checkpoint.ifPresentOrElse(
          updater::setWeakSubjectivityCheckpoint, updater::clearWeakSubjectivityCheckpoint);
      updater.commit();
    }
  }

  @Override
  public Optional<StoreBuilder> createMemoryStore() {
    return createMemoryStore(() -> Instant.now().getEpochSecond());
  }

  @VisibleForTesting
  Optional<StoreBuilder> createMemoryStore(final Supplier<Long> timeSupplier) {
    Optional<UInt64> maybeGenesisTime = hotDao.getGenesisTime();
    if (maybeGenesisTime.isEmpty()) {
      // If genesis time hasn't been set, genesis hasn't happened and we have no data
      return Optional.empty();
    }
    final UInt64 genesisTime = maybeGenesisTime.get();
    final Optional<Checkpoint> anchor = hotDao.getAnchor();
    final Checkpoint justifiedCheckpoint = hotDao.getJustifiedCheckpoint().orElseThrow();
    final Checkpoint finalizedCheckpoint = hotDao.getFinalizedCheckpoint().orElseThrow();
    final Checkpoint bestJustifiedCheckpoint = hotDao.getBestJustifiedCheckpoint().orElseThrow();
    final BeaconState finalizedState = hotDao.getLatestFinalizedState().orElseThrow();

    final Map<UInt64, VoteTracker> votes = hotDao.getVotes();

    // Build maps with block information
    final Map<Bytes32, Bytes32> childToParentLookup = new HashMap<>();
    final Map<Bytes32, UInt64> rootToSlot = new HashMap<>();
    try (final Stream<SignedBeaconBlock> hotBlocks = hotDao.streamHotBlocks()) {
      hotBlocks.forEach(
          b -> {
            childToParentLookup.put(b.getRoot(), b.getParent_root());
            rootToSlot.put(b.getRoot(), b.getSlot());
          });
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

    // Make sure time is set to a reasonable value in the case where we start up before genesis when
    // the clock time would be prior to genesis
    final long clockTime = timeSupplier.get();
    final UInt64 slotTime = genesisTime.plus(finalizedState.getSlot().times(SECONDS_PER_SLOT));
    final UInt64 time = slotTime.max(clockTime);

    return Optional.of(
        StoreBuilder.create()
            .metricsSystem(metricsSystem)
            .time(time)
            .anchor(anchor)
            .genesisTime(genesisTime)
            .finalizedCheckpoint(finalizedCheckpoint)
            .justifiedCheckpoint(justifiedCheckpoint)
            .bestJustifiedCheckpoint(bestJustifiedCheckpoint)
            .childToParentMap(childToParentLookup)
            .rootToSlotMap(rootToSlot)
            .latestFinalized(latestFinalized)
            .votes(votes));
  }

  @Override
  public WeakSubjectivityState getWeakSubjectivityState() {
    return WeakSubjectivityState.create(hotDao.getWeakSubjectivityCheckpoint());
  }

  @Override
  public Map<UInt64, VoteTracker> getVotes() {
    return hotDao.getVotes();
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return finalizedDao.getSlotForFinalizedBlockRoot(blockRoot);
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    return finalizedDao.getSlotForFinalizedStateRoot(stateRoot);
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {
    return finalizedDao.getFinalizedBlockAtSlot(slot);
  }

  @Override
  public Optional<UInt64> getEarliestAvailableBlockSlot() {
    return finalizedDao.getEarliestFinalizedBlockSlot();
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return finalizedDao.getLatestFinalizedBlockAtSlot(slot);
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return finalizedDao.getLatestAvailableFinalizedState(maxSlot);
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return hotDao.getHotBlock(root).or(() -> finalizedDao.getFinalizedBlock(root));
  }

  @Override
  public Optional<BeaconState> getHotState(final Bytes32 root) {
    return hotDao.getHotState(root);
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots) {
    return blockRoots.stream()
        .flatMap(root -> hotDao.getHotBlock(root).stream())
        .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 blockRoot) {
    return hotDao.getHotBlock(blockRoot);
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return finalizedDao.streamFinalizedBlocks(startSlot, endSlot);
  }

  @Override
  public List<Bytes32> getStateRootsBeforeSlot(final UInt64 slot) {
    return hotDao.getStateRootsBeforeSlot(slot);
  }

  @Override
  public void addHotStateRoots(
      final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap) {
    try (final HotUpdater updater = hotDao.hotUpdater()) {
      updater.addHotStateRoots(stateRootToSlotAndBlockRootMap);
      updater.commit();
    }
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    Optional<SlotAndBlockRoot> maybeSlotAndBlockRoot =
        hotDao.getSlotAndBlockRootFromStateRoot(stateRoot);
    if (maybeSlotAndBlockRoot.isPresent()) {
      return maybeSlotAndBlockRoot;
    }
    return finalizedDao.getSlotAndBlockRootForFinalizedStateRoot(stateRoot);
  }

  @Override
  public void pruneHotStateRoots(final List<Bytes32> stateRoots) {
    try (final HotUpdater updater = hotDao.hotUpdater()) {
      updater.pruneHotStateRoots(stateRoots);
      updater.commit();
    }
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
      update
          .getFinalizedCheckpoint()
          .ifPresent(
              checkpoint -> {
                updater.setFinalizedCheckpoint(checkpoint);
                UInt64 finalizedSlot = checkpoint.getEpochStartSlot().plus(SLOTS_PER_EPOCH);
                updater.pruneHotStateRoots(hotDao.getStateRootsBeforeSlot(finalizedSlot));
                updater.deleteHotState(checkpoint.getRoot());
              });

      update.getJustifiedCheckpoint().ifPresent(updater::setJustifiedCheckpoint);
      update.getBestJustifiedCheckpoint().ifPresent(updater::setBestJustifiedCheckpoint);
      update.getLatestFinalizedState().ifPresent(updater::setLatestFinalizedState);

      updater.addHotBlocks(update.getHotBlocks());
      updater.addHotStates(update.getHotStates());

      if (update.getStateRoots().size() > 0) {
        updater.addHotStateRoots(update.getStateRoots());
      }
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
          final SignedBeaconBlock baseBlock = getFinalizedBlock();

          final HashTree blockTree =
              HashTree.builder()
                  .rootHash(baseBlock.getRoot())
                  .childAndParentRoots(finalizedChildToParentMap)
                  .build();

          final StateRootRecorder recorder =
              new StateRootRecorder(baseBlock.getSlot(), updater::addFinalizedStateRoot);

          blockTree
              .preOrderStream()
              .forEach(
                  blockRoot -> {
                    updater.addFinalizedBlock(
                        blockProvider
                            .getBlock(blockRoot)
                            .join()
                            .orElseThrow(
                                () -> new IllegalStateException("Missing finalized block")));
                    Optional.ofNullable(finalizedStates.get(blockRoot))
                        .or(() -> getHotState(blockRoot))
                        .ifPresent(
                            state -> {
                              updater.addFinalizedState(blockRoot, state);
                              recorder.acceptNextState(state);
                            });
                  });
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

  private SignedBeaconBlock getFinalizedBlock() {
    final Bytes32 baseBlockRoot = hotDao.getFinalizedCheckpoint().orElseThrow().getRoot();
    return finalizedDao.getFinalizedBlock(baseBlockRoot).orElseThrow();
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
