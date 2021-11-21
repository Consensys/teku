/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.storage.server.kvstore;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.MustBeClosed;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.StoredBlockMetadata;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.spec.datastructures.blocks.CheckpointEpochs;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.hashtree.HashTree;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.events.WeakSubjectivityState;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreEth1Dao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreEth1Dao.Eth1Updater;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreFinalizedDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreFinalizedDao.FinalizedUpdater;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreHotDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreHotDao.HotUpdater;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedKvStoreDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedStateSnapshotStorageLogic;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedStateStorageLogic;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedStateTreeStorageLogic;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4HotKvStoreDao;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalized;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotState;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedTreeState;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaHot;
import tech.pegasys.teku.storage.server.kvstore.schema.V4SchemaFinalized;
import tech.pegasys.teku.storage.server.kvstore.schema.V4SchemaHot;
import tech.pegasys.teku.storage.server.state.StateRootRecorder;
import tech.pegasys.teku.storage.store.StoreBuilder;

public class KvStoreDatabase implements Database {

  private static final Logger LOG = LogManager.getLogger();

  private static final int TX_BATCH_SIZE = 500;

  private final MetricsSystem metricsSystem;
  private final StateStorageMode stateStorageMode;

  final KvStoreHotDao hotDao;
  final KvStoreFinalizedDao finalizedDao;
  final KvStoreEth1Dao eth1Dao;

  private final Spec spec;
  private final boolean storeNonCanonicalBlocks;

  public static Database createV4(
      final MetricsSystem metricsSystem,
      final KvStoreAccessor hotDb,
      final KvStoreAccessor finalizedDb,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    return createWithStateSnapshots(
        metricsSystem,
        hotDb,
        finalizedDb,
        new V4SchemaHot(spec),
        new V4SchemaFinalized(spec),
        stateStorageMode,
        stateStorageFrequency,
        storeNonCanonicalBlocks,
        spec);
  }

  public static Database createWithStateSnapshots(
      final MetricsSystem metricsSystem,
      final KvStoreAccessor hotDb,
      final KvStoreAccessor finalizedDb,
      final SchemaHot schemaHot,
      final SchemaFinalizedSnapshotState schemaFinalized,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    final V4FinalizedStateSnapshotStorageLogic finalizedStateStorageLogic =
        new V4FinalizedStateSnapshotStorageLogic(stateStorageFrequency);
    return create(
        metricsSystem,
        hotDb,
        finalizedDb,
        schemaHot,
        schemaFinalized,
        stateStorageMode,
        storeNonCanonicalBlocks,
        spec,
        finalizedStateStorageLogic);
  }

  public static Database createWithStateTree(
      final MetricsSystem metricsSystem,
      final KvStoreAccessor db,
      final SchemaHot schemaHot,
      final SchemaFinalizedTreeState schemaFinalized,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final int maxKnownNodeCacheSize,
      final Spec spec) {
    final V4FinalizedStateStorageLogic<SchemaFinalizedTreeState> finalizedStateStorageLogic =
        new V4FinalizedStateTreeStorageLogic(metricsSystem, spec, maxKnownNodeCacheSize);
    return create(
        metricsSystem,
        db,
        db,
        schemaHot,
        schemaFinalized,
        stateStorageMode,
        storeNonCanonicalBlocks,
        spec,
        finalizedStateStorageLogic);
  }

  private static <S extends SchemaFinalized> KvStoreDatabase create(
      final MetricsSystem metricsSystem,
      final KvStoreAccessor hotDb,
      final KvStoreAccessor finalizedDb,
      final SchemaHot schemaHot,
      final S schemaFinalized,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final Spec spec,
      final V4FinalizedStateStorageLogic<S> finalizedStateStorageLogic) {
    final V4HotKvStoreDao dao = new V4HotKvStoreDao(hotDb, schemaHot);
    final KvStoreFinalizedDao finalizedDbDao =
        new V4FinalizedKvStoreDao<>(finalizedDb, schemaFinalized, finalizedStateStorageLogic);
    return new KvStoreDatabase(
        metricsSystem, dao, finalizedDbDao, dao, stateStorageMode, storeNonCanonicalBlocks, spec);
  }

  private KvStoreDatabase(
      final MetricsSystem metricsSystem,
      final KvStoreHotDao hotDao,
      final KvStoreFinalizedDao finalizedDao,
      final KvStoreEth1Dao eth1Dao,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    checkNotNull(spec);
    this.metricsSystem = metricsSystem;
    this.finalizedDao = finalizedDao;
    this.eth1Dao = eth1Dao;
    this.stateStorageMode = stateStorageMode;
    this.hotDao = hotDao;
    this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
    this.spec = spec;
  }

  @Override
  public void storeInitialAnchor(final AnchorPoint anchor) {
    try (final HotUpdater hotUpdater = hotDao.hotUpdater();
        final FinalizedUpdater finalizedUpdater = finalizedDao.finalizedUpdater()) {
      // We should only have a single block / state / checkpoint at anchorpoint initialization
      final Checkpoint anchorCheckpoint = anchor.getCheckpoint();
      final Bytes32 anchorRoot = anchorCheckpoint.getRoot();
      final BeaconState anchorState = anchor.getState();
      final Optional<SignedBeaconBlock> anchorBlock = anchor.getSignedBeaconBlock();

      hotUpdater.setAnchor(anchor.getCheckpoint());
      hotUpdater.setGenesisTime(anchorState.getGenesis_time());
      hotUpdater.setJustifiedCheckpoint(anchorCheckpoint);
      hotUpdater.setBestJustifiedCheckpoint(anchorCheckpoint);
      hotUpdater.setFinalizedCheckpoint(anchorCheckpoint);
      hotUpdater.setLatestFinalizedState(anchorState);

      // We need to store the anchor block in both hot and cold storage so that on restart
      // we're guaranteed to have at least one block / state to load into RecentChainData.
      anchorBlock.ifPresent(
          block -> {
            // Save to hot storage
            hotUpdater.addHotBlock(
                new BlockAndCheckpointEpochs(
                    block,
                    new CheckpointEpochs(
                        anchorState.getCurrent_justified_checkpoint().getEpoch(),
                        anchorState.getFinalized_checkpoint().getEpoch())));
            // Save to cold storage
            finalizedUpdater.addFinalizedBlock(block);
          });

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

  public void ingestDatabase(
      final KvStoreDatabase kvStoreDatabase, final int batchSize, final Consumer<String> logger) {
    hotDao.ingest(kvStoreDatabase.hotDao, batchSize, logger);
    finalizedDao.ingest(kvStoreDatabase.finalizedDao, batchSize, logger);
  }

  @Override
  public void storeFinalizedBlocks(final Collection<SignedBeaconBlock> blocks) {
    if (blocks.isEmpty()) {
      return;
    }

    // Sort blocks and verify that they are contiguous with the oldestBlock
    final List<SignedBeaconBlock> sorted =
        blocks.stream()
            .sorted(Comparator.comparing(SignedBeaconBlock::getSlot).reversed())
            .collect(Collectors.toList());

    // The new block should be just prior to our earliest block if available, and otherwise should
    // match our latest finalized block
    Bytes32 expectedRoot =
        getEarliestAvailableBlock()
            .map(SignedBeaconBlock::getParentRoot)
            .orElseGet(() -> this.getLatestFinalizedBlockSummary().getRoot());
    for (SignedBeaconBlock block : sorted) {
      if (!block.getRoot().equals(expectedRoot)) {
        throw new IllegalArgumentException(
            "Blocks must be contiguous with the earliest known block.");
      }
      expectedRoot = block.getParentRoot();
    }

    try (final FinalizedUpdater updater = finalizedDao.finalizedUpdater()) {
      blocks.forEach(updater::addFinalizedBlock);
      updater.commit();
    }
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
    final Optional<Checkpoint> maybeAnchor = hotDao.getAnchor();
    final Checkpoint justifiedCheckpoint = hotDao.getJustifiedCheckpoint().orElseThrow();
    final Checkpoint finalizedCheckpoint = hotDao.getFinalizedCheckpoint().orElseThrow();
    final Checkpoint bestJustifiedCheckpoint = hotDao.getBestJustifiedCheckpoint().orElseThrow();
    final BeaconState finalizedState = hotDao.getLatestFinalizedState().orElseThrow();

    final Map<UInt64, VoteTracker> votes = hotDao.getVotes();

    // Build map with block information
    final Map<Bytes32, StoredBlockMetadata> blockInformation = new HashMap<>();
    try (final Stream<SignedBeaconBlock> hotBlocks = hotDao.streamHotBlocks()) {
      hotBlocks.forEach(
          b -> {
            final Optional<CheckpointEpochs> checkpointEpochs =
                hotDao.getHotBlockCheckpointEpochs(b.getRoot());
            blockInformation.put(
                b.getRoot(),
                new StoredBlockMetadata(
                    b.getSlot(),
                    b.getRoot(),
                    b.getParentRoot(),
                    b.getStateRoot(),
                    b.getMessage()
                        .getBody()
                        .getOptionalExecutionPayload()
                        .map(ExecutionPayload::getBlockHash),
                    checkpointEpochs));
          });
    }
    // If anchor block is missing, try to pull block info from the anchor state
    final boolean shouldIncludeAnchorBlock =
        maybeAnchor.isPresent()
            && finalizedCheckpoint
                .getEpochStartSlot(spec)
                .equals(maybeAnchor.get().getEpochStartSlot(spec));
    if (shouldIncludeAnchorBlock && !blockInformation.containsKey(maybeAnchor.get().getRoot())) {
      final Checkpoint anchor = maybeAnchor.orElseThrow();
      final StateAndBlockSummary latestFinalized = StateAndBlockSummary.create(finalizedState);
      if (!latestFinalized.getRoot().equals(anchor.getRoot())) {
        throw new IllegalStateException("Anchor state (" + anchor + ") is unavailable");
      }
      blockInformation.put(
          anchor.getRoot(), StoredBlockMetadata.fromBlockAndState(latestFinalized));
    }

    final Optional<SignedBeaconBlock> finalizedBlock =
        hotDao.getHotBlock(finalizedCheckpoint.getRoot());
    final AnchorPoint latestFinalized =
        AnchorPoint.create(spec, finalizedCheckpoint, finalizedState, finalizedBlock);

    // Make sure time is set to a reasonable value in the case where we start up before genesis when
    // the clock time would be prior to genesis
    final long clockTime = timeSupplier.get();
    final UInt64 slotTime = spec.getSlotStartTime(finalizedState.getSlot(), genesisTime);
    final UInt64 time = slotTime.max(clockTime);

    return Optional.of(
        StoreBuilder.create()
            .metricsSystem(metricsSystem)
            .specProvider(spec)
            .time(time)
            .anchor(maybeAnchor)
            .genesisTime(genesisTime)
            .latestFinalized(latestFinalized)
            .justifiedCheckpoint(justifiedCheckpoint)
            .bestJustifiedCheckpoint(bestJustifiedCheckpoint)
            .blockInformation(blockInformation)
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
  public Optional<SignedBeaconBlock> getEarliestAvailableBlock() {
    return finalizedDao.getEarliestFinalizedBlock();
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
    return hotDao
        .getHotBlock(root)
        .or(() -> finalizedDao.getFinalizedBlock(root))
        .or(() -> finalizedDao.getNonCanonicalBlock(root));
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
  public Set<SignedBeaconBlock> getNonCanonicalBlocksAtSlot(final UInt64 slot) {
    return finalizedDao.getNonCanonicalBlocksAtSlot(slot);
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
  public void storeVotes(final Map<UInt64, VoteTracker> votes) {
    try (final KvStoreHotDao.HotUpdater hotUpdater = hotDao.hotUpdater()) {
      hotUpdater.addVotes(votes);
      hotUpdater.commit();
    }
  }

  @Override
  public void close() throws Exception {
    hotDao.close();
    eth1Dao.close();
    finalizedDao.close();
  }

  private void doUpdate(final StorageUpdate update) {
    LOG.trace("Applying finalized updates");
    // Update finalized blocks and states
    updateFinalizedData(
        update.getFinalizedChildToParentMap(),
        update.getFinalizedBlocks(),
        update.getFinalizedStates(),
        update.getDeletedHotBlocks());
    LOG.trace("Applying hot updates");
    try (final HotUpdater updater = hotDao.hotUpdater()) {
      // Store new hot data
      update.getGenesisTime().ifPresent(updater::setGenesisTime);
      update
          .getFinalizedCheckpoint()
          .ifPresent(
              checkpoint -> {
                updater.setFinalizedCheckpoint(checkpoint);
                final int slotsPerEpoch = spec.slotsPerEpoch(checkpoint.getEpoch());
                final UInt64 finalizedSlot = checkpoint.getEpochStartSlot(spec).plus(slotsPerEpoch);
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

      // Delete finalized data from hot db
      update.getDeletedHotBlocks().forEach(updater::deleteHotBlock);

      LOG.trace("Committing hot db changes");
      updater.commit();
    }
    LOG.trace("Update complete");
  }

  private void updateFinalizedData(
      Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates,
      final Set<Bytes32> deletedHotBlocks) {
    if (finalizedChildToParentMap.isEmpty()) {
      // Nothing to do
      return;
    }

    switch (stateStorageMode) {
      case ARCHIVE:
        updateFinalizedDataArchiveMode(finalizedChildToParentMap, finalizedBlocks, finalizedStates);
        break;

      case PRUNE:
        updateFinalizedDataPruneMode(finalizedChildToParentMap, finalizedBlocks);
        break;
      default:
        throw new UnsupportedOperationException("Unhandled storage mode: " + stateStorageMode);
    }

    if (storeNonCanonicalBlocks) {
      storeNonCanonicalBlocks(
          deletedHotBlocks.stream()
              .filter(root -> !finalizedChildToParentMap.containsKey(root))
              .flatMap(root -> getHotBlock(root).stream())
              .collect(Collectors.toSet()));
    }
  }

  private void storeNonCanonicalBlocks(final Set<SignedBeaconBlock> nonCanonicalBlocks) {
    int i = 0;
    final Iterator<SignedBeaconBlock> it = nonCanonicalBlocks.iterator();
    while (it.hasNext()) {
      final Map<UInt64, Set<Bytes32>> nonCanonicalRootsBySlotBuffer = new HashMap<>();
      final int start = i;
      try (final FinalizedUpdater updater = finalizedDao.finalizedUpdater()) {
        while (it.hasNext() && (i - start) < TX_BATCH_SIZE) {
          final SignedBeaconBlock block = it.next();
          LOG.debug("Non canonical block {}:{}", block.getRoot().toHexString(), block.getSlot());
          updater.addNonCanonicalBlock(block);
          nonCanonicalRootsBySlotBuffer
              .computeIfAbsent(block.getSlot(), __ -> new HashSet<>())
              .add(block.getRoot());
          i++;
        }
        nonCanonicalRootsBySlotBuffer.forEach(updater::addNonCanonicalRootAtSlot);
        updater.commit();
      }
    }
  }

  private void updateFinalizedDataArchiveMode(
      Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates) {
    final BlockProvider blockProvider =
        BlockProvider.withKnownBlocks(
            roots -> SafeFuture.completedFuture(getHotBlocks(roots)), finalizedBlocks);

    final Optional<Checkpoint> initialCheckpoint = hotDao.getAnchor();
    final Optional<Bytes32> initialBlockRoot = initialCheckpoint.map(Checkpoint::getRoot);
    // Get previously finalized block to build on top of
    final BeaconBlockSummary baseBlock = getLatestFinalizedBlockOrSummary();

    final List<Bytes32> finalizedRoots =
        HashTree.builder()
            .rootHash(baseBlock.getRoot())
            .childAndParentRoots(finalizedChildToParentMap)
            .build()
            .preOrderStream()
            .collect(Collectors.toList());

    int i = 0;
    UInt64 lastSlot = baseBlock.getSlot();
    while (i < finalizedRoots.size()) {
      final int start = i;
      try (final FinalizedUpdater updater = finalizedDao.finalizedUpdater()) {
        final StateRootRecorder recorder =
            new StateRootRecorder(lastSlot, updater::addFinalizedStateRoot, spec);

        while (i < finalizedRoots.size() && (i - start) < TX_BATCH_SIZE) {
          final Bytes32 blockRoot = finalizedRoots.get(i);

          final Optional<SignedBeaconBlock> maybeBlock = blockProvider.getBlock(blockRoot).join();
          maybeBlock.ifPresent(updater::addFinalizedBlock);
          // If block is missing and doesn't match the initial anchor, throw
          if (maybeBlock.isEmpty() && initialBlockRoot.filter(r -> r.equals(blockRoot)).isEmpty()) {
            throw new IllegalStateException("Missing finalized block");
          }

          Optional.ofNullable(finalizedStates.get(blockRoot))
              .or(() -> getHotState(blockRoot))
              .ifPresent(
                  state -> {
                    updater.addFinalizedState(blockRoot, state);
                    recorder.acceptNextState(state);
                  });

          lastSlot =
              maybeBlock
                  .map(SignedBeaconBlock::getSlot)
                  .orElseGet(() -> initialCheckpoint.orElseThrow().getEpochStartSlot(spec));
          i++;
        }
        updater.commit();
        if (i >= TX_BATCH_SIZE) {
          STATUS_LOG.recordedFinalizedBlocks(i, finalizedRoots.size());
        }
      }
    }
  }

  private void updateFinalizedDataPruneMode(
      Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks) {
    final Optional<Bytes32> initialBlockRoot = hotDao.getAnchor().map(Checkpoint::getRoot);
    final BlockProvider blockProvider =
        BlockProvider.withKnownBlocks(
            roots -> SafeFuture.completedFuture(getHotBlocks(roots)), finalizedBlocks);

    final List<Bytes32> finalizedRoots = new ArrayList<>(finalizedChildToParentMap.keySet());
    int i = 0;
    while (i < finalizedRoots.size()) {
      try (final FinalizedUpdater updater = finalizedDao.finalizedUpdater()) {
        final int start = i;
        while (i < finalizedRoots.size() && (i - start) < TX_BATCH_SIZE) {
          final Bytes32 root = finalizedRoots.get(i);
          final Optional<SignedBeaconBlock> maybeBlock = blockProvider.getBlock(root).join();
          maybeBlock.ifPresent(updater::addFinalizedBlock);

          // If block is missing and doesn't match the initial anchor, throw
          if (maybeBlock.isEmpty() && initialBlockRoot.filter(r -> r.equals(root)).isEmpty()) {
            throw new IllegalStateException("Missing finalized block");
          }
          i++;
        }
        updater.commit();
        if (i >= TX_BATCH_SIZE) {
          STATUS_LOG.recordedFinalizedBlocks(i, finalizedRoots.size());
        }
      }
    }
  }

  private BeaconBlockSummary getLatestFinalizedBlockOrSummary() {
    final Bytes32 baseBlockRoot = hotDao.getFinalizedCheckpoint().orElseThrow().getRoot();
    return finalizedDao
        .getFinalizedBlock(baseBlockRoot)
        .<BeaconBlockSummary>map(a -> a)
        .orElseGet(this::getLatestFinalizedBlockSummary);
  }

  private BeaconBlockSummary getLatestFinalizedBlockSummary() {
    final Optional<BeaconBlockSummary> finalizedBlock =
        hotDao.getLatestFinalizedState().map(BeaconBlockHeader::fromState);
    return finalizedBlock.orElseThrow(
        () -> new IllegalStateException("Unable to reconstruct latest finalized block summary"));
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
