/*
 * Copyright ConsenSys Software Inc., 2022
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
import static tech.pegasys.teku.infrastructure.logging.DbLogger.DB_LOGGER;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.MustBeClosed;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
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
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayload;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.hashtree.HashTree;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.api.WeakSubjectivityState;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.CombinedKvStoreDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoAdapter;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoCommon;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoCommon.CombinedUpdaterCommon;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoCommon.FinalizedUpdaterCommon;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoCommon.HotUpdaterCommon;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedKvStoreDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedStateSnapshotStorageLogic;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedStateStorageLogic;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedStateTreeStorageLogic;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4HotKvStoreDao;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaCombined;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaCombinedSnapshotState;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaCombinedTreeState;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotStateAdapter;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaHotAdapter;
import tech.pegasys.teku.storage.server.state.StateRootRecorder;

public abstract class KvStoreDatabase<
        DaoT extends KvStoreCombinedDaoCommon,
        CombinedUpdaterT extends CombinedUpdaterCommon,
        HotUpdaterT extends HotUpdaterCommon,
        FinalizedUpdaterT extends FinalizedUpdaterCommon>
    implements Database {

  private static final Logger LOG = LogManager.getLogger();

  protected static final int TX_BATCH_SIZE = 500;

  private final StateStorageMode stateStorageMode;

  protected final Spec spec;
  protected final boolean storeNonCanonicalBlocks;
  @VisibleForTesting final DaoT dao;

  public static Database createV4(
      final KvStoreAccessor hotDb,
      final KvStoreAccessor finalizedDb,
      final SchemaHotAdapter schemaHot,
      final SchemaFinalizedSnapshotStateAdapter schemaFinalized,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final boolean storeBlockExecutionPayloadSeparately,
      final int blockMigrationBatchSize,
      final int blockMigrationBatchDelay,
      final Optional<AsyncRunner> asyncRunner,
      final Spec spec) {
    final V4FinalizedStateSnapshotStorageLogic<SchemaFinalizedSnapshotStateAdapter>
        finalizedStateStorageLogic =
            new V4FinalizedStateSnapshotStorageLogic<>(stateStorageFrequency);
    final V4HotKvStoreDao hotDao = new V4HotKvStoreDao(hotDb, schemaHot);
    final KvStoreCombinedDaoAdapter dao =
        new KvStoreCombinedDaoAdapter(
            hotDao,
            new V4FinalizedKvStoreDao(finalizedDb, schemaFinalized, finalizedStateStorageLogic));
    if (storeBlockExecutionPayloadSeparately) {
      return new BlindedBlockKvStoreDatabase(
          dao,
          new BlindedBlockMigration<>(
              spec, dao, blockMigrationBatchSize, blockMigrationBatchDelay, asyncRunner),
          stateStorageMode,
          storeNonCanonicalBlocks,
          spec);
    }
    return new UnblindedBlockKvStoreDatabase(dao, stateStorageMode, storeNonCanonicalBlocks, spec);
  }

  public static Database createWithStateSnapshots(
      final KvStoreAccessor db,
      final SchemaCombinedSnapshotState schema,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final boolean storeBlockExecutionPayloadSeparately,
      final int blockMigrationBatchSize,
      final int blockMigrationBatchDelay,
      final Optional<AsyncRunner> asyncRunner,
      final Spec spec) {
    final V4FinalizedStateSnapshotStorageLogic<SchemaCombinedSnapshotState>
        finalizedStateStorageLogic =
            new V4FinalizedStateSnapshotStorageLogic<>(stateStorageFrequency);
    return create(
        db,
        schema,
        stateStorageMode,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        blockMigrationBatchSize,
        blockMigrationBatchDelay,
        asyncRunner,
        spec,
        finalizedStateStorageLogic);
  }

  public static Database createWithStateTree(
      final MetricsSystem metricsSystem,
      final KvStoreAccessor db,
      final SchemaCombinedTreeState schema,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final boolean storeBlockExecutionPayloadSeparately,
      final int blockMigrationBatchSize,
      final int blockMigrationBatchDelay,
      final int maxKnownNodeCacheSize,
      final Optional<AsyncRunner> asyncRunner,
      final Spec spec) {
    final V4FinalizedStateStorageLogic<SchemaCombinedTreeState> finalizedStateStorageLogic =
        new V4FinalizedStateTreeStorageLogic(metricsSystem, spec, maxKnownNodeCacheSize);
    return create(
        db,
        schema,
        stateStorageMode,
        storeNonCanonicalBlocks,
        storeBlockExecutionPayloadSeparately,
        blockMigrationBatchSize,
        blockMigrationBatchDelay,
        asyncRunner,
        spec,
        finalizedStateStorageLogic);
  }

  private static <S extends SchemaCombined> KvStoreDatabase<?, ?, ?, ?> create(
      final KvStoreAccessor db,
      final S schema,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final boolean storeBlockExecutionPayloadSeparately,
      final int blockMigrationBatchSize,
      final int blockMigrationBatchDelay,
      final Optional<AsyncRunner> asyncRunner,
      final Spec spec,
      final V4FinalizedStateStorageLogic<S> finalizedStateStorageLogic) {
    final CombinedKvStoreDao<S> dao =
        new CombinedKvStoreDao<>(db, schema, finalizedStateStorageLogic);
    if (storeBlockExecutionPayloadSeparately) {
      return new BlindedBlockKvStoreDatabase(
          dao,
          new BlindedBlockMigration<>(
              spec, dao, blockMigrationBatchSize, blockMigrationBatchDelay, asyncRunner),
          stateStorageMode,
          storeNonCanonicalBlocks,
          spec);
    }
    return new UnblindedBlockKvStoreDatabase(dao, stateStorageMode, storeNonCanonicalBlocks, spec);
  }

  KvStoreDatabase(
      final DaoT dao,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    this.dao = dao;
    checkNotNull(spec);
    this.stateStorageMode = stateStorageMode;
    this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
    this.spec = spec;
  }

  @MustBeClosed
  protected abstract CombinedUpdaterT combinedUpdater();

  @MustBeClosed
  protected abstract HotUpdaterT hotUpdater();

  @MustBeClosed
  protected abstract FinalizedUpdaterT finalizedUpdater();

  @Override
  public void storeInitialAnchor(final AnchorPoint anchor) {
    try (final CombinedUpdaterT updater = combinedUpdater()) {
      // We should only have a single block / state / checkpoint at anchorpoint initialization
      final Checkpoint anchorCheckpoint = anchor.getCheckpoint();
      final Bytes32 anchorRoot = anchorCheckpoint.getRoot();
      final BeaconState anchorState = anchor.getState();
      final Optional<SignedBeaconBlock> anchorBlock = anchor.getSignedBeaconBlock();

      updater.setAnchor(anchor.getCheckpoint());
      updater.setGenesisTime(anchorState.getGenesisTime());
      updater.setJustifiedCheckpoint(anchorCheckpoint);
      updater.setBestJustifiedCheckpoint(anchorCheckpoint);
      updater.setFinalizedCheckpoint(anchorCheckpoint);
      updater.setLatestFinalizedState(anchorState);

      // We need to store the anchor block in both hot and cold storage so that on restart
      // we're guaranteed to have at least one block / state to load into RecentChainData.
      anchorBlock.ifPresent(
          block -> {
            // Save to hot storage
            storeAnchorStateAndBlock(updater, anchorState, block);
          });

      putFinalizedState(updater, anchorRoot, anchorState);

      updater.commit();
    }
  }

  protected abstract void storeAnchorStateAndBlock(
      final CombinedUpdaterT updater, final BeaconState anchorState, final SignedBeaconBlock block);

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    Optional<SlotAndBlockRoot> maybeSlotAndBlockRoot =
        dao.getSlotAndBlockRootFromStateRoot(stateRoot);
    if (maybeSlotAndBlockRoot.isPresent()) {
      return maybeSlotAndBlockRoot;
    }
    return dao.getSlotAndBlockRootForFinalizedStateRoot(stateRoot);
  }

  @Override
  public UpdateResult update(final StorageUpdate event) {
    if (event.isEmpty()) {
      return UpdateResult.EMPTY;
    }
    return doUpdate(event);
  }

  public void ingestDatabase(
      final KvStoreDatabase<?, ?, ?, ?> kvStoreDatabase,
      final int batchSize,
      final Consumer<String> logger) {
    dao.ingest(kvStoreDatabase.dao, batchSize, logger);
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

    storeFinalizedBlocksToDao(blocks);
  }

  protected abstract void storeFinalizedBlocksToDao(final Collection<SignedBeaconBlock> blocks);

  @Override
  public void updateWeakSubjectivityState(WeakSubjectivityUpdate weakSubjectivityUpdate) {
    try (final HotUpdaterT updater = hotUpdater()) {
      Optional<Checkpoint> checkpoint = weakSubjectivityUpdate.getWeakSubjectivityCheckpoint();
      checkpoint.ifPresentOrElse(
          updater::setWeakSubjectivityCheckpoint, updater::clearWeakSubjectivityCheckpoint);
      updater.commit();
    }
  }

  @Override
  public void storeFinalizedState(BeaconState state) {
    final Bytes32 blockRoot = spec.getBlockRootAtSlot(state, state.getSlot());
    try (final FinalizedUpdaterCommon updater = finalizedUpdater()) {
      updater.addFinalizedState(blockRoot, state);
      updater.commit();
    }
  }

  @Override
  public Optional<OnDiskStoreData> createMemoryStore() {
    return createMemoryStore(() -> Instant.now().getEpochSecond());
  }

  @VisibleForTesting
  Optional<OnDiskStoreData> createMemoryStore(final Supplier<Long> timeSupplier) {
    Optional<UInt64> maybeGenesisTime = dao.getGenesisTime();
    if (maybeGenesisTime.isEmpty()) {
      // If genesis time hasn't been set, genesis hasn't happened and we have no data
      return Optional.empty();
    }
    final UInt64 genesisTime = maybeGenesisTime.get();
    final Optional<Checkpoint> maybeAnchor = dao.getAnchor();
    final Checkpoint justifiedCheckpoint = dao.getJustifiedCheckpoint().orElseThrow();
    final Checkpoint finalizedCheckpoint = dao.getFinalizedCheckpoint().orElseThrow();
    final Checkpoint bestJustifiedCheckpoint = dao.getBestJustifiedCheckpoint().orElseThrow();
    final BeaconState finalizedState = dao.getLatestFinalizedState().orElseThrow();

    final Map<UInt64, VoteTracker> votes = dao.getVotes();

    // Build map with block information
    final Map<Bytes32, StoredBlockMetadata> blockInformation = buildHotBlockMetadata();
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
          anchor.getRoot(), StoredBlockMetadata.fromBlockAndState(spec, latestFinalized));
    }

    final Optional<SignedBeaconBlock> finalizedBlock =
        getFinalizedBlock(finalizedCheckpoint.getRoot());
    final AnchorPoint latestFinalized =
        AnchorPoint.create(spec, finalizedCheckpoint, finalizedState, finalizedBlock);
    final Optional<SlotAndExecutionPayload> finalizedOptimisticTransitionPayload =
        dao.getOptimisticTransitionBlockSlot()
            .flatMap(this::getFinalizedBlockAtSlot)
            .flatMap(SlotAndExecutionPayload::fromBlock);

    // Make sure time is set to a reasonable value in the case where we start up before genesis when
    // the clock time would be prior to genesis
    final long clockTime = timeSupplier.get();
    final UInt64 slotTime = spec.getSlotStartTime(finalizedState.getSlot(), genesisTime);
    final UInt64 time = slotTime.max(clockTime);

    return Optional.of(
        new OnDiskStoreData(
            time,
            maybeAnchor,
            genesisTime,
            latestFinalized,
            finalizedOptimisticTransitionPayload,
            justifiedCheckpoint,
            bestJustifiedCheckpoint,
            blockInformation,
            votes));
  }

  protected abstract Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root);

  protected abstract Map<Bytes32, StoredBlockMetadata> buildHotBlockMetadata();

  @Override
  public WeakSubjectivityState getWeakSubjectivityState() {
    return WeakSubjectivityState.create(dao.getWeakSubjectivityCheckpoint());
  }

  @Override
  public Map<UInt64, VoteTracker> getVotes() {
    return dao.getVotes();
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return dao.getLatestAvailableFinalizedState(maxSlot);
  }

  @Override
  public Optional<BeaconState> getHotState(final Bytes32 root) {
    return dao.getHotState(root);
  }

  @Override
  @MustBeClosed
  public Stream<Map.Entry<Bytes32, BlockCheckpoints>> streamBlockCheckpoints() {
    return dao.streamBlockCheckpoints();
  }

  @Override
  public List<Bytes32> getStateRootsBeforeSlot(final UInt64 slot) {
    return dao.getStateRootsBeforeSlot(slot);
  }

  @Override
  public void addHotStateRoots(
      final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap) {
    try (final HotUpdaterCommon updater = hotUpdater()) {
      updater.addHotStateRoots(stateRootToSlotAndBlockRootMap);
      updater.commit();
    }
  }

  @Override
  public void pruneHotStateRoots(final List<Bytes32> stateRoots) {
    try (final HotUpdaterCommon updater = hotUpdater()) {
      updater.pruneHotStateRoots(stateRoots);
      updater.commit();
    }
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
  @MustBeClosed
  public Stream<UInt64> streamFinalizedStateSlots(final UInt64 startSlot, final UInt64 endSlot) {
    return dao.streamFinalizedStateSlots(startSlot, endSlot);
  }

  @Override
  public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
    try (final HotUpdaterCommon updater = hotUpdater()) {
      updater.addMinGenesisTimeBlock(event);
      updater.commit();
    }
  }

  @Override
  public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
    try (final HotUpdaterCommon updater = hotUpdater()) {
      updater.addDepositsFromBlockEvent(event);
      updater.commit();
    }
  }

  @Override
  public void storeVotes(final Map<UInt64, VoteTracker> votes) {
    try (final HotUpdaterCommon hotUpdater = hotUpdater()) {
      hotUpdater.addVotes(votes);
      hotUpdater.commit();
    }
  }

  @Override
  public Optional<Checkpoint> getAnchor() {
    return dao.getAnchor();
  }

  @Override
  public void close() throws Exception {
    dao.close();
  }

  private UpdateResult doUpdate(final StorageUpdate update) {
    LOG.trace("Applying finalized updates");
    // Update finalized blocks and states
    final Optional<SlotAndExecutionPayload> finalizedOptimisticExecutionPayload =
        updateFinalizedData(
            update.getFinalizedChildToParentMap(),
            update.getFinalizedBlocks(),
            update.getFinalizedStates(),
            update.getDeletedHotBlocks(),
            update.isFinalizedOptimisticTransitionBlockRootSet(),
            update.getOptimisticTransitionBlockRoot());
    LOG.trace("Applying hot updates");
    long startTime = System.nanoTime();
    try (final HotUpdaterT updater = hotUpdater()) {
      // Store new hot data
      update.getGenesisTime().ifPresent(updater::setGenesisTime);
      update
          .getFinalizedCheckpoint()
          .ifPresent(
              checkpoint -> {
                updater.setFinalizedCheckpoint(checkpoint);
                final int slotsPerEpoch = spec.slotsPerEpoch(checkpoint.getEpoch());
                final UInt64 finalizedSlot = checkpoint.getEpochStartSlot(spec).plus(slotsPerEpoch);
                updater.pruneHotStateRoots(dao.getStateRootsBeforeSlot(finalizedSlot));
                updater.deleteHotState(checkpoint.getRoot());
              });

      update.getJustifiedCheckpoint().ifPresent(updater::setJustifiedCheckpoint);
      update.getBestJustifiedCheckpoint().ifPresent(updater::setBestJustifiedCheckpoint);
      update.getLatestFinalizedState().ifPresent(updater::setLatestFinalizedState);

      updateHotBlocks(
          updater,
          update.getHotBlocks(),
          update.getDeletedHotBlocks(),
          update.getFinalizedBlocks().keySet());
      updater.addHotStates(update.getHotStates());

      if (update.getStateRoots().size() > 0) {
        updater.addHotStateRoots(update.getStateRoots());
      }

      // Delete finalized data from hot db

      LOG.trace("Committing hot db changes");
      updater.commit();
    }
    long endTime = System.nanoTime();
    DB_LOGGER.onDbOpAlertThreshold("KvStoreDatabase update", startTime, endTime);
    LOG.trace("Update complete");
    return new UpdateResult(finalizedOptimisticExecutionPayload);
  }

  protected abstract void updateHotBlocks(
      final HotUpdaterT updater,
      final Map<Bytes32, BlockAndCheckpoints> addedBlocks,
      final Set<Bytes32> deletedHotBlockRoots,
      final Set<Bytes32> finalizedBlockRoots);

  private Optional<SlotAndExecutionPayload> updateFinalizedData(
      Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates,
      final Set<Bytes32> deletedHotBlocks,
      final boolean isFinalizedOptimisticBlockRootSet,
      final Optional<Bytes32> finalizedOptimisticTransitionBlockRoot) {
    if (finalizedChildToParentMap.isEmpty()) {
      // Nothing to do
      return Optional.empty();
    }

    final Optional<SlotAndExecutionPayload> optimisticTransitionPayload =
        updateFinalizedOptimisticTransitionBlock(
            isFinalizedOptimisticBlockRootSet, finalizedOptimisticTransitionBlockRoot);
    switch (stateStorageMode) {
      case ARCHIVE:
        updateFinalizedDataArchiveMode(
            finalizedChildToParentMap, finalizedBlocks, deletedHotBlocks, finalizedStates);
        break;

      case PRUNE:
        updateFinalizedDataPruneMode(finalizedChildToParentMap, deletedHotBlocks, finalizedBlocks);
        break;
      default:
        throw new UnsupportedOperationException("Unhandled storage mode: " + stateStorageMode);
    }

    storeNonCanonicalBlocks(deletedHotBlocks, finalizedChildToParentMap);

    return optimisticTransitionPayload;
  }

  private Optional<SlotAndExecutionPayload> updateFinalizedOptimisticTransitionBlock(
      final boolean isFinalizedOptimisticBlockRootSet,
      final Optional<Bytes32> finalizedOptimisticTransitionBlockRoot) {
    if (isFinalizedOptimisticBlockRootSet) {
      final Optional<SignedBeaconBlock> transitionBlock =
          finalizedOptimisticTransitionBlockRoot.flatMap(this::getHotBlock);
      try (final FinalizedUpdaterT updater = finalizedUpdater()) {
        updater.setOptimisticTransitionBlockSlot(transitionBlock.map(SignedBeaconBlock::getSlot));
        updater.commit();
      }
      return transitionBlock.flatMap(SlotAndExecutionPayload::fromBlock);
    } else {
      return Optional.empty();
    }
  }

  protected abstract void storeNonCanonicalBlocks(
      final Set<Bytes32> blockRoots, final Map<Bytes32, Bytes32> finalizedChildToParentMap);

  private void updateFinalizedDataArchiveMode(
      Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Set<Bytes32> deletedHotBlocks,
      final Map<Bytes32, BeaconState> finalizedStates) {
    final BlockProvider blockProvider =
        BlockProvider.withKnownBlocks(
            roots -> SafeFuture.completedFuture(getHotBlocks(roots)), finalizedBlocks);

    final Optional<Checkpoint> initialCheckpoint = dao.getAnchor();
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
      try (final FinalizedUpdaterT updater = finalizedUpdater()) {
        final StateRootRecorder recorder =
            new StateRootRecorder(lastSlot, updater::addFinalizedStateRoot, spec);

        while (i < finalizedRoots.size() && (i - start) < TX_BATCH_SIZE) {
          final Bytes32 blockRoot = finalizedRoots.get(i);

          final Optional<SignedBeaconBlock> maybeBlock = blockProvider.getBlock(blockRoot).join();
          maybeBlock.ifPresent(
              block ->
                  addFinalizedBlock(block, deletedHotBlocks.contains(block.getRoot()), updater));
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

  protected abstract void addFinalizedBlock(
      final SignedBeaconBlock block,
      final boolean isRemovedFromHotBlocks,
      final FinalizedUpdaterT updater);

  private void updateFinalizedDataPruneMode(
      Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Set<Bytes32> deletedHotBlocks,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks) {
    final Optional<Bytes32> initialBlockRoot = dao.getAnchor().map(Checkpoint::getRoot);
    final BlockProvider blockProvider =
        BlockProvider.withKnownBlocks(
            roots -> SafeFuture.completedFuture(getHotBlocks(roots)), finalizedBlocks);

    final List<Bytes32> finalizedRoots = new ArrayList<>(finalizedChildToParentMap.keySet());
    int i = 0;
    while (i < finalizedRoots.size()) {
      try (final FinalizedUpdaterT updater = finalizedUpdater()) {
        final int start = i;
        while (i < finalizedRoots.size() && (i - start) < TX_BATCH_SIZE) {
          final Bytes32 root = finalizedRoots.get(i);
          final Optional<SignedBeaconBlock> maybeBlock = blockProvider.getBlock(root).join();
          maybeBlock.ifPresent(
              block ->
                  addFinalizedBlock(block, deletedHotBlocks.contains(block.getRoot()), updater));

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
    final Bytes32 baseBlockRoot = dao.getFinalizedCheckpoint().orElseThrow().getRoot();
    return getFinalizedBlock(baseBlockRoot)
        .<BeaconBlockSummary>map(a -> a)
        .orElseGet(this::getLatestFinalizedBlockSummary);
  }

  private BeaconBlockSummary getLatestFinalizedBlockSummary() {
    final Optional<BeaconBlockSummary> finalizedBlock =
        dao.getLatestFinalizedState().map(BeaconBlockHeader::fromState);
    return finalizedBlock.orElseThrow(
        () -> new IllegalStateException("Unable to reconstruct latest finalized block summary"));
  }

  private void putFinalizedState(
      FinalizedUpdaterCommon updater, final Bytes32 blockRoot, final BeaconState state) {
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
