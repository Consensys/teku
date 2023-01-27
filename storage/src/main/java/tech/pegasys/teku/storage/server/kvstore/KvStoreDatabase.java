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
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.MustBeClosed;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
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
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
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
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.CombinedUpdater;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.FinalizedUpdater;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDao.HotUpdater;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoAdapter;
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

public class KvStoreDatabase implements Database {

  protected static final int TX_BATCH_SIZE = 500;
  private static final Logger LOG = LogManager.getLogger();
  protected final Spec spec;
  protected final boolean storeNonCanonicalBlocks;
  @VisibleForTesting final KvStoreCombinedDao dao;
  private final StateStorageMode stateStorageMode;

  KvStoreDatabase(
      final KvStoreCombinedDao dao,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    this.dao = dao;
    checkNotNull(spec);
    this.stateStorageMode = stateStorageMode;
    this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
    this.spec = spec;
  }

  public static Database createV4(
      final KvStoreAccessor hotDb,
      final KvStoreAccessor finalizedDb,
      final SchemaHotAdapter schemaHot,
      final SchemaFinalizedSnapshotStateAdapter schemaFinalized,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    final V4FinalizedStateSnapshotStorageLogic<SchemaFinalizedSnapshotStateAdapter>
        finalizedStateStorageLogic =
            new V4FinalizedStateSnapshotStorageLogic<>(stateStorageFrequency);
    final V4HotKvStoreDao hotDao = new V4HotKvStoreDao(hotDb, schemaHot);
    final KvStoreCombinedDaoAdapter dao =
        new KvStoreCombinedDaoAdapter(
            hotDao,
            new V4FinalizedKvStoreDao(finalizedDb, schemaFinalized, finalizedStateStorageLogic));
    return new KvStoreDatabase(dao, stateStorageMode, storeNonCanonicalBlocks, spec);
  }

  public static Database createWithStateSnapshots(
      final KvStoreAccessor db,
      final SchemaCombinedSnapshotState schema,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec) {
    final V4FinalizedStateSnapshotStorageLogic<SchemaCombinedSnapshotState>
        finalizedStateStorageLogic =
            new V4FinalizedStateSnapshotStorageLogic<>(stateStorageFrequency);
    return create(
        db, schema, stateStorageMode, storeNonCanonicalBlocks, spec, finalizedStateStorageLogic);
  }

  public static Database createWithStateTree(
      final MetricsSystem metricsSystem,
      final KvStoreAccessor db,
      final SchemaCombinedTreeState schema,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final int maxKnownNodeCacheSize,
      final Spec spec) {
    final V4FinalizedStateStorageLogic<SchemaCombinedTreeState> finalizedStateStorageLogic =
        new V4FinalizedStateTreeStorageLogic(metricsSystem, spec, maxKnownNodeCacheSize);
    return create(
        db, schema, stateStorageMode, storeNonCanonicalBlocks, spec, finalizedStateStorageLogic);
  }

  private static <S extends SchemaCombined> KvStoreDatabase create(
      final KvStoreAccessor db,
      final S schema,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final Spec spec,
      final V4FinalizedStateStorageLogic<S> finalizedStateStorageLogic) {
    final CombinedKvStoreDao<S> dao =
        new CombinedKvStoreDao<>(db, schema, finalizedStateStorageLogic);
    return new KvStoreDatabase(dao, stateStorageMode, storeNonCanonicalBlocks, spec);
  }

  @MustBeClosed
  protected CombinedUpdater combinedUpdater() {
    return dao.combinedUpdater();
  }

  @MustBeClosed
  protected HotUpdater hotUpdater() {
    return dao.hotUpdater();
  }

  @MustBeClosed
  protected FinalizedUpdater finalizedUpdater() {
    return dao.finalizedUpdater();
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return dao.getSlotForFinalizedBlockRoot(blockRoot);
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    return dao.getSlotForFinalizedStateRoot(stateRoot);
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {
    return dao.getFinalizedBlockAtSlot(slot);
  }

  @Override
  public Optional<UInt64> getEarliestAvailableBlockSlot() {
    return dao.getEarliestFinalizedBlockSlot();
  }

  @Override
  public Optional<SignedBeaconBlock> getEarliestAvailableBlock() {
    return dao.getEarliestFinalizedBlock();
  }

  @Override
  public Optional<SignedBeaconBlock> getLastAvailableFinalizedBlock() {
    return dao.getFinalizedCheckpoint()
        .flatMap(
            checkpoint -> getFinalizedBlock(checkpoint.toSlotAndBlockRoot(spec).getBlockRoot()));
  }

  @Override
  public Optional<Bytes32> getFinalizedBlockRootBySlot(final UInt64 slot) {
    return dao.getFinalizedBlockAtSlot(slot).map(SignedBeaconBlock::getRoot);
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return dao.getLatestFinalizedBlockAtSlot(slot);
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return dao.getHotBlock(root)
        .or(() -> dao.getFinalizedBlock(root))
        .or(() -> dao.getNonCanonicalBlock(root));
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots) {
    return blockRoots.stream()
        .flatMap(root -> dao.getHotBlock(root).stream())
        .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 blockRoot) {
    return dao.getHotBlock(blockRoot);
  }

  @Override
  public Stream<Map.Entry<Bytes, Bytes>> streamHotBlocksAsSsz() {
    return dao.streamHotBlocksAsSsz();
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return dao.streamFinalizedBlocks(startSlot, endSlot);
  }

  protected Map<Bytes32, StoredBlockMetadata> buildHotBlockMetadata() {
    final Map<Bytes32, StoredBlockMetadata> blockInformation = new HashMap<>();
    try (final Stream<SignedBeaconBlock> hotBlocks = dao.streamHotBlocks()) {
      hotBlocks.forEach(
          b -> {
            final Optional<BlockCheckpoints> checkpointEpochs =
                dao.getHotBlockCheckpointEpochs(b.getRoot());
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
    return blockInformation;
  }

  protected void storeAnchorStateAndBlock(
      final CombinedUpdater updater, final BeaconState anchorState, final SignedBeaconBlock block) {
    updater.addHotBlock(
        new BlockAndCheckpoints(
            block,
            new BlockCheckpoints(
                anchorState.getCurrentJustifiedCheckpoint(),
                anchorState.getFinalizedCheckpoint(),
                anchorState.getCurrentJustifiedCheckpoint(),
                anchorState.getFinalizedCheckpoint())));
    // Save to cold storage
    updater.addFinalizedBlock(block);
  }

  @Override
  public List<SignedBeaconBlock> getNonCanonicalBlocksAtSlot(final UInt64 slot) {
    return dao.getNonCanonicalBlocksAtSlot(slot);
  }

  protected void storeFinalizedBlocksToDao(
      final Collection<SignedBeaconBlock> blocks,
      final Map<UInt64, BlobsSidecar> blobsSidecarBySlot) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      blocks.forEach(
          block -> {
            updater.addFinalizedBlock(block);
            Optional.ofNullable(blobsSidecarBySlot.get(block.getSlot()))
                .ifPresent(updater::addBlobsSidecar);
          });
      updater.commit();
    }
  }

  protected Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root) {
    return dao.getHotBlock(root);
  }

  @Override
  public Map<String, Long> getColumnCounts() {
    return dao.getColumnCounts();
  }

  @Override
  public Map<String, Long> getBlobsSidecarColumnCounts() {
    return dao.getBlobsSidecarColumnCounts();
  }

  @Override
  public void migrate() {}

  @Override
  public void deleteHotBlocks(final Set<Bytes32> blockRootsToDelete) {
    try (final HotUpdater updater = hotUpdater()) {
      blockRootsToDelete.forEach(updater::deleteHotBlock);
      updater.commit();
    }
  }

  @Override
  public void pruneFinalizedBlocks(final UInt64 lastSlotToPrune) {
    final Optional<UInt64> earliestBlockSlot =
        dao.getEarliestFinalizedBlock().map(SignedBeaconBlock::getSlot);
    for (UInt64 batchStart = earliestBlockSlot.orElse(lastSlotToPrune);
        batchStart.isLessThanOrEqualTo(lastSlotToPrune);
        batchStart = batchStart.plus(PRUNE_BATCH_SIZE)) {
      try (final FinalizedUpdater updater = finalizedUpdater()) {
        updater.pruneFinalizedBlocks(
            batchStart, lastSlotToPrune.min(batchStart.plus(PRUNE_BATCH_SIZE)));
        updater.commit();
      }
    }
  }

  protected void updateHotBlocks(
      final HotUpdater updater,
      final Map<Bytes32, BlockAndCheckpoints> addedBlocks,
      final Set<Bytes32> deletedHotBlockRoots) {
    updater.addHotBlocks(addedBlocks);
    deletedHotBlockRoots.forEach(updater::deleteHotBlock);
  }

  protected void addFinalizedBlock(final SignedBeaconBlock block, final FinalizedUpdater updater) {
    updater.addFinalizedBlock(block);
  }

  protected void storeNonCanonicalBlocks(
      final Set<Bytes32> blockRoots, final Map<Bytes32, Bytes32> finalizedChildToParentMap) {
    if (storeNonCanonicalBlocks) {
      final Set<SignedBeaconBlock> nonCanonicalBlocks =
          blockRoots.stream()
              .filter(root -> !finalizedChildToParentMap.containsKey(root))
              .flatMap(root -> getHotBlock(root).stream())
              .collect(Collectors.toSet());
      int i = 0;
      final Iterator<SignedBeaconBlock> it = nonCanonicalBlocks.iterator();
      while (it.hasNext()) {
        final Map<UInt64, Set<Bytes32>> nonCanonicalRootsBySlotBuffer = new HashMap<>();
        final int start = i;
        try (final FinalizedUpdater updater = finalizedUpdater()) {
          while (it.hasNext() && (i - start) < TX_BATCH_SIZE) {
            final SignedBeaconBlock block = it.next();
            LOG.debug("Non canonical block {}:{}", block.getRoot().toHexString(), block.getSlot());
            updater.addNonCanonicalBlock(block);
            nonCanonicalRootsBySlotBuffer
                .computeIfAbsent(block.getSlot(), dao::getNonCanonicalBlockRootsAtSlot)
                .add(block.getRoot());
            i++;
          }
          nonCanonicalRootsBySlotBuffer.forEach(updater::addNonCanonicalRootAtSlot);
          updater.commit();
        }
      }
    }
  }

  @Override
  public void storeInitialAnchor(final AnchorPoint anchor) {
    try (final CombinedUpdater updater = combinedUpdater()) {
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
      final KvStoreDatabase kvStoreDatabase, final int batchSize, final Consumer<String> logger) {
    dao.ingest(kvStoreDatabase.dao, batchSize, logger);
  }

  @Override
  public void storeFinalizedBlocks(
      final Collection<SignedBeaconBlock> blocks,
      final Map<UInt64, BlobsSidecar> blobsSidecarBySlot) {
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

    storeFinalizedBlocksToDao(blocks, blobsSidecarBySlot);
  }

  @Override
  public void updateWeakSubjectivityState(WeakSubjectivityUpdate weakSubjectivityUpdate) {
    try (final HotUpdater updater = hotUpdater()) {
      Optional<Checkpoint> checkpoint = weakSubjectivityUpdate.getWeakSubjectivityCheckpoint();
      checkpoint.ifPresentOrElse(
          updater::setWeakSubjectivityCheckpoint, updater::clearWeakSubjectivityCheckpoint);
      updater.commit();
    }
  }

  @Override
  public void storeFinalizedState(BeaconState state, Bytes32 blockRoot) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.addFinalizedState(blockRoot, state);
      handleAddFinalizedStateRoot(state, updater);
    }
  }

  @Override
  public void storeReconstructedFinalizedState(BeaconState state, Bytes32 blockRoot) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.addReconstructedFinalizedState(blockRoot, state);
      handleAddFinalizedStateRoot(state, updater);
    }
  }

  private void handleAddFinalizedStateRoot(BeaconState state, FinalizedUpdater updater) {
    final Optional<BeaconState> maybeLastState =
        getLatestAvailableFinalizedState(state.getSlot().minusMinZero(ONE));
    maybeLastState.ifPresentOrElse(
        lastState -> {
          final StateRootRecorder recorder =
              new StateRootRecorder(
                  lastState.getSlot().increment(), updater::addFinalizedStateRoot, spec);
          recorder.acceptNextState(state);
        },
        () -> updater.addFinalizedStateRoot(state.hashTreeRoot(), state.getSlot()));

    updater.commit();
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
    final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload =
        dao.getOptimisticTransitionBlockSlot()
            .flatMap(this::getFinalizedBlockAtSlot)
            .flatMap(SlotAndExecutionPayloadSummary::fromBlock);

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

  @Override
  public WeakSubjectivityState getWeakSubjectivityState() {
    return WeakSubjectivityState.create(dao.getWeakSubjectivityCheckpoint());
  }

  @Override
  public Map<UInt64, VoteTracker> getVotes() {
    return dao.getVotes();
  }

  @Override
  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return dao.getFinalizedCheckpoint();
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return dao.getLatestAvailableFinalizedState(maxSlot);
  }

  @Override
  @MustBeClosed
  public Stream<Map.Entry<Bytes32, UInt64>> getFinalizedStateRoots() {
    return dao.getFinalizedStateRoots();
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
    try (final HotUpdater updater = hotUpdater()) {
      updater.addHotStateRoots(stateRootToSlotAndBlockRootMap);
      updater.commit();
    }
  }

  @Override
  public Optional<UInt64> getGenesisTime() {
    return dao.getGenesisTime();
  }

  @Override
  public void pruneHotStateRoots(final List<Bytes32> stateRoots) {
    try (final HotUpdater updater = hotUpdater()) {
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
    try (final HotUpdater updater = hotUpdater()) {
      updater.addMinGenesisTimeBlock(event);
      updater.commit();
    }
  }

  @Override
  public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
    try (final HotUpdater updater = hotUpdater()) {
      updater.addDepositsFromBlockEvent(event);
      updater.commit();
    }
  }

  @Override
  public void removeDepositsFromBlockEvents(List<UInt64> blockNumbers) {
    try (final HotUpdater updater = hotUpdater()) {
      blockNumbers.forEach(updater::removeDepositsFromBlockEvent);
      updater.commit();
    }
  }

  @Override
  public void storeUnconfirmedBlobsSidecar(final BlobsSidecar blobsSidecar) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.addBlobsSidecar(blobsSidecar);
      updater.addUnconfirmedBlobsSidecar(blobsSidecar);
      updater.commit();
    }
  }

  @Override
  public void confirmBlobsSidecar(final SlotAndBlockRoot slotAndBlockRoot) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.confirmBlobsSidecar(slotAndBlockRoot);
      updater.commit();
    }
  }

  @Override
  public Optional<BlobsSidecar> getBlobsSidecar(final SlotAndBlockRoot slotAndBlockRoot) {
    final Optional<Bytes> maybePayload = dao.getBlobsSidecar(slotAndBlockRoot);
    return maybePayload.map(
        payload -> spec.deserializeBlobsSidecar(payload, slotAndBlockRoot.getSlot()));
  }

  @Override
  public void removeBlobsSidecar(final SlotAndBlockRoot slotAndBlockRoot) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.removeBlobsSidecar(slotAndBlockRoot);
      updater.confirmBlobsSidecar(slotAndBlockRoot);
      updater.commit();
    }
  }

  @Override
  public boolean pruneOldestBlobsSidecar(final UInt64 lastSlotToPrune, final int pruneLimit) {
    try (final Stream<SlotAndBlockRoot> prunableBlobsKeys =
            streamBlobsSidecarKeys(UInt64.ZERO, lastSlotToPrune);
        final FinalizedUpdater updater = finalizedUpdater()) {
      final long pruned =
          prunableBlobsKeys
              .limit(pruneLimit)
              .peek(
                  slotAndBlockRoot -> {
                    updater.removeBlobsSidecar(slotAndBlockRoot);
                    updater.confirmBlobsSidecar(slotAndBlockRoot);
                  })
              .count();
      updater.commit();

      return pruned == pruneLimit;
    }
  }

  @Override
  public boolean pruneOldestUnconfirmedBlobsSidecars(
      final UInt64 lastSlotToPrune, final int pruneLimit) {
    try (final Stream<SlotAndBlockRoot> prunableUnconfirmed =
            streamUnconfirmedBlobsSidecars(UInt64.ZERO, lastSlotToPrune);
        final FinalizedUpdater updater = finalizedUpdater()) {
      final long pruned =
          prunableUnconfirmed
              .limit(pruneLimit)
              .peek(
                  slotAndBlockRoot -> {
                    updater.removeBlobsSidecar(slotAndBlockRoot);
                    updater.confirmBlobsSidecar(slotAndBlockRoot);
                  })
              .count();
      updater.commit();

      return pruned == pruneLimit;
    }
  }

  @MustBeClosed
  @Override
  public Stream<BlobsSidecar> streamBlobsSidecars(final UInt64 startSlot, final UInt64 endSlot) {
    return dao.streamBlobsSidecar(startSlot, endSlot)
        .map(
            slotAndBlockRootBytesEntry ->
                spec.deserializeBlobsSidecar(
                    slotAndBlockRootBytesEntry.getValue(),
                    slotAndBlockRootBytesEntry.getKey().getSlot()));
  }

  @MustBeClosed
  @Override
  public Stream<Map.Entry<SlotAndBlockRoot, Bytes>> streamBlobsSidecarsAsSsz(
      final UInt64 startSlot, final UInt64 endSlot) {
    return dao.streamBlobsSidecar(startSlot, endSlot);
  }

  @MustBeClosed
  @Override
  public Stream<SlotAndBlockRoot> streamBlobsSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot) {
    return dao.streamBlobsSidecarKeys(startSlot, endSlot);
  }

  @MustBeClosed
  @Override
  public Stream<SlotAndBlockRoot> streamUnconfirmedBlobsSidecars(
      final UInt64 startSlot, final UInt64 endSlot) {
    return dao.streamUnconfirmedBlobsSidecar(startSlot, endSlot);
  }

  @Override
  public Optional<UInt64> getEarliestBlobsSidecarSlot() {
    return dao.getEarliestBlobsSidecarSlot();
  }

  @Override
  public void storeVotes(final Map<UInt64, VoteTracker> votes) {
    try (final HotUpdater hotUpdater = hotUpdater()) {
      hotUpdater.addVotes(votes);
      hotUpdater.commit();
    }
  }

  @Override
  public Optional<Checkpoint> getAnchor() {
    return dao.getAnchor();
  }

  @Override
  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return dao.getJustifiedCheckpoint();
  }

  @Override
  public Optional<DepositTreeSnapshot> getFinalizedDepositSnapshot() {
    return dao.getFinalizedDepositSnapshot();
  }

  @Override
  public void setFinalizedDepositSnapshot(DepositTreeSnapshot finalizedDepositSnapshot) {
    try (final HotUpdater updater = hotUpdater()) {
      updater.setFinalizedDepositSnapshot(finalizedDepositSnapshot);
      updater.commit();
    }
  }

  @Override
  public void close() throws Exception {
    dao.close();
  }

  private UpdateResult doUpdate(final StorageUpdate update) {
    LOG.trace("Applying finalized updates");
    // Update finalized blocks and states
    final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticExecutionPayload =
        updateFinalizedData(
            update.getFinalizedChildToParentMap(),
            update.getFinalizedBlocks(),
            update.getFinalizedStates(),
            update.getDeletedHotBlocks(),
            update.isFinalizedOptimisticTransitionBlockRootSet(),
            update.getOptimisticTransitionBlockRoot());

    if (update.isBlobsSidecarEnabled()) {
      updateBlobsSidecars(
          update.getHotBlocks(), update.getFinalizedBlocks(), update.getDeletedHotBlocks());
    }

    LOG.trace("Applying hot updates");
    long startTime = System.nanoTime();
    try (final HotUpdater updater = hotUpdater()) {
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

      updateHotBlocks(updater, update.getHotBlocks(), update.getDeletedHotBlocks().keySet());
      updater.addHotStates(update.getHotStates());

      if (update.getStateRoots().size() > 0) {
        updater.addHotStateRoots(update.getStateRoots());
      }

      // Delete finalized data from hot db

      LOG.trace("Committing hot db changes");
      updater.commit();
    }
    long endTime = System.nanoTime();
    DB_LOGGER.onDbOpAlertThreshold("Block Import", startTime, endTime);
    LOG.trace("Update complete");
    return new UpdateResult(finalizedOptimisticExecutionPayload);
  }

  private Optional<SlotAndExecutionPayloadSummary> updateFinalizedData(
      Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates,
      final Map<Bytes32, UInt64> deletedHotBlocksRootsWithSlot,
      final boolean isFinalizedOptimisticBlockRootSet,
      final Optional<Bytes32> finalizedOptimisticTransitionBlockRoot) {
    if (finalizedChildToParentMap.isEmpty()) {
      // Nothing to do
      return Optional.empty();
    }

    final Optional<SlotAndExecutionPayloadSummary> optimisticTransitionPayload =
        updateFinalizedOptimisticTransitionBlock(
            isFinalizedOptimisticBlockRootSet, finalizedOptimisticTransitionBlockRoot);
    if (stateStorageMode.storesFinalizedStates()) {
      updateFinalizedDataArchiveMode(finalizedChildToParentMap, finalizedBlocks, finalizedStates);
    } else {
      updateFinalizedDataPruneMode(finalizedChildToParentMap, finalizedBlocks);
    }

    storeNonCanonicalBlocks(deletedHotBlocksRootsWithSlot.keySet(), finalizedChildToParentMap);

    return optimisticTransitionPayload;
  }

  private Optional<SlotAndExecutionPayloadSummary> updateFinalizedOptimisticTransitionBlock(
      final boolean isFinalizedOptimisticBlockRootSet,
      final Optional<Bytes32> finalizedOptimisticTransitionBlockRoot) {
    if (isFinalizedOptimisticBlockRootSet) {
      final Optional<SignedBeaconBlock> transitionBlock =
          finalizedOptimisticTransitionBlockRoot.flatMap(this::getHotBlock);
      try (final FinalizedUpdater updater = finalizedUpdater()) {
        updater.setOptimisticTransitionBlockSlot(transitionBlock.map(SignedBeaconBlock::getSlot));
        updater.commit();
      }
      return transitionBlock.flatMap(SlotAndExecutionPayloadSummary::fromBlock);
    } else {
      return Optional.empty();
    }
  }

  private void updateBlobsSidecars(
      final Map<Bytes32, BlockAndCheckpoints> hotBlocks,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, UInt64> deletedHotBlocks) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      LOG.trace("Confirming blobs sidecars for new hot blocks");
      hotBlocks.values().stream()
          .map(
              blockAndCheckpoints ->
                  new SlotAndBlockRoot(
                      blockAndCheckpoints.getSlot(), blockAndCheckpoints.getRoot()))
          .forEach(updater::confirmBlobsSidecar);

      final Set<Bytes32> finalizedBlockRoots = finalizedBlocks.keySet();

      LOG.trace("Removing blobs sidecars for deleted hot blocks");
      deletedHotBlocks.entrySet().stream()
          .filter(entry -> !finalizedBlockRoots.contains(entry.getKey()))
          .map(entry -> new SlotAndBlockRoot(entry.getValue(), entry.getKey()))
          .forEach(updater::removeBlobsSidecar);

      updater.commit();
    }
  }

  private void updateFinalizedDataArchiveMode(
      Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
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
      try (final FinalizedUpdater updater = finalizedUpdater()) {
        final StateRootRecorder recorder =
            new StateRootRecorder(lastSlot, updater::addFinalizedStateRoot, spec);

        while (i < finalizedRoots.size() && (i - start) < TX_BATCH_SIZE) {
          final Bytes32 blockRoot = finalizedRoots.get(i);

          final Optional<SignedBeaconBlock> maybeBlock = blockProvider.getBlock(blockRoot).join();
          maybeBlock.ifPresent(block -> addFinalizedBlock(block, updater));
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
    final Optional<Bytes32> initialBlockRoot = dao.getAnchor().map(Checkpoint::getRoot);
    final BlockProvider blockProvider =
        BlockProvider.withKnownBlocks(
            roots -> SafeFuture.completedFuture(getHotBlocks(roots)), finalizedBlocks);

    final List<Bytes32> finalizedRoots = new ArrayList<>(finalizedChildToParentMap.keySet());
    int i = 0;
    while (i < finalizedRoots.size()) {
      try (final FinalizedUpdater updater = finalizedUpdater()) {
        final int start = i;
        while (i < finalizedRoots.size() && (i - start) < TX_BATCH_SIZE) {
          final Bytes32 root = finalizedRoots.get(i);
          final Optional<SignedBeaconBlock> maybeBlock = blockProvider.getBlock(root).join();
          maybeBlock.ifPresent(block -> addFinalizedBlock(block, updater));

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
      FinalizedUpdater updater, final Bytes32 blockRoot, final BeaconState state) {
    if (stateStorageMode.storesFinalizedStates()) {
      updater.addFinalizedState(blockRoot, state);
    }
  }
}
