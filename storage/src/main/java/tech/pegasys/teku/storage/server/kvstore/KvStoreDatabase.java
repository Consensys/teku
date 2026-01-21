/*
 * Copyright Consensys Software Inc., 2025
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
import static java.util.stream.Collectors.groupingBy;
import static tech.pegasys.teku.infrastructure.logging.DbLogger.DB_LOGGER;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
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
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockInvariants;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.hashtree.HashTree;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.api.WeakSubjectivityState;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.archive.BlobSidecarsArchiver;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.filesystem.FileBasedDataColumnStorage;
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
  protected static final int BLOBS_TX_BATCH_SIZE = 100;
  private static final Logger LOG = LogManager.getLogger();
  protected final Spec spec;
  protected final boolean storeNonCanonicalBlocks;
  @VisibleForTesting final KvStoreCombinedDao dao;
  private final StateStorageMode stateStorageMode;
  private final FileBasedDataColumnStorage dataColumnStorage;

  KvStoreDatabase(
      final KvStoreCombinedDao dao,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final Spec spec,
      final Path beaconDataDirectory) {
    this.dao = dao;
    checkNotNull(spec);
    this.stateStorageMode = stateStorageMode;
    this.storeNonCanonicalBlocks = storeNonCanonicalBlocks;
    this.spec = spec;
    this.dataColumnStorage =
        new FileBasedDataColumnStorage(spec, beaconDataDirectory.resolve("columns"));
  }

  public static Database createV4(
      final KvStoreAccessor hotDb,
      final KvStoreAccessor finalizedDb,
      final SchemaHotAdapter schemaHot,
      final SchemaFinalizedSnapshotStateAdapter schemaFinalized,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec,
      final Path beaconDataDirectory) {
    final V4FinalizedStateSnapshotStorageLogic<SchemaFinalizedSnapshotStateAdapter>
        finalizedStateStorageLogic =
            new V4FinalizedStateSnapshotStorageLogic<>(stateStorageFrequency);
    final V4HotKvStoreDao hotDao = new V4HotKvStoreDao(hotDb, schemaHot);
    final KvStoreCombinedDaoAdapter dao =
        new KvStoreCombinedDaoAdapter(
            hotDao,
            new V4FinalizedKvStoreDao(finalizedDb, schemaFinalized, finalizedStateStorageLogic));
    return new KvStoreDatabase(
        dao, stateStorageMode, storeNonCanonicalBlocks, spec, beaconDataDirectory);
  }

  public static Database createWithStateSnapshots(
      final KvStoreAccessor db,
      final SchemaCombinedSnapshotState schema,
      final StateStorageMode stateStorageMode,
      final long stateStorageFrequency,
      final boolean storeNonCanonicalBlocks,
      final Spec spec,
      final Path beaconDataDirectory) {
    final V4FinalizedStateSnapshotStorageLogic<SchemaCombinedSnapshotState>
        finalizedStateStorageLogic =
            new V4FinalizedStateSnapshotStorageLogic<>(stateStorageFrequency);
    return create(
        db,
        schema,
        stateStorageMode,
        storeNonCanonicalBlocks,
        spec,
        finalizedStateStorageLogic,
        beaconDataDirectory);
  }

  public static Database createWithStateTree(
      final MetricsSystem metricsSystem,
      final KvStoreAccessor db,
      final SchemaCombinedTreeState schema,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final int maxKnownNodeCacheSize,
      final Spec spec,
      final Path beaconDataDirectory) {
    final V4FinalizedStateStorageLogic<SchemaCombinedTreeState> finalizedStateStorageLogic =
        new V4FinalizedStateTreeStorageLogic(metricsSystem, spec, maxKnownNodeCacheSize);
    return create(
        db,
        schema,
        stateStorageMode,
        storeNonCanonicalBlocks,
        spec,
        finalizedStateStorageLogic,
        beaconDataDirectory);
  }

  private static <S extends SchemaCombined> KvStoreDatabase create(
      final KvStoreAccessor db,
      final S schema,
      final StateStorageMode stateStorageMode,
      final boolean storeNonCanonicalBlocks,
      final Spec spec,
      final V4FinalizedStateStorageLogic<S> finalizedStateStorageLogic,
      final Path beaconDataDirectory) {
    final CombinedKvStoreDao<S> dao =
        new CombinedKvStoreDao<>(db, schema, finalizedStateStorageLogic);
    return new KvStoreDatabase(
        dao, stateStorageMode, storeNonCanonicalBlocks, spec, beaconDataDirectory);
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
  public Optional<Bytes32> getLatestCanonicalBlockRoot() {
    return dao.getLatestCanonicalBlockRoot();
  }

  @Override
  public Optional<UInt64> getCustodyGroupCount() {
    return dao.getCustodyGroupCount();
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
            final Optional<ExecutionPayload> executionPayload =
                b.getMessage().getBody().getOptionalExecutionPayload();
            blockInformation.put(
                b.getRoot(),
                new StoredBlockMetadata(
                    b.getSlot(),
                    b.getRoot(),
                    b.getParentRoot(),
                    b.getStateRoot(),
                    executionPayload.map(ExecutionPayload::getBlockNumber),
                    executionPayload.map(ExecutionPayload::getBlockHash),
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
      final Map<SlotAndBlockRoot, List<BlobSidecar>> finalizedBlobSidecarsBySlot,
      final Optional<UInt64> maybeEarliestBlobSidecar) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      blocks.forEach(
          block -> {
            updater.addFinalizedBlock(block);
            // If there is no slot in BlobSidecar's map it means we are pre-Deneb or not in
            // availability period
            if (!finalizedBlobSidecarsBySlot.containsKey(block.getSlotAndBlockRoot())) {
              return;
            }
            finalizedBlobSidecarsBySlot
                .get(block.getSlotAndBlockRoot())
                .forEach(updater::addBlobSidecar);
          });

      needToUpdateEarliestBlockSlot(blocks.stream().findFirst())
          .ifPresent(updater::setEarliestBlockSlot);
      needToUpdateEarliestBlobSidecarSlot(maybeEarliestBlobSidecar)
          .ifPresent(updater::setEarliestBlobSidecarSlot);
      updater.commit();
    }
  }

  private Optional<UInt64> needToUpdateEarliestBlockSlot(
      final Optional<SignedBeaconBlock> maybeNewEarliestBlockSlot) {
    // New value is absent - not updating
    if (maybeNewEarliestBlockSlot.isEmpty()) {
      return Optional.empty();
    }
    // New value is present, value from DB is absent - updating
    final Optional<UInt64> maybeEarliestFinalizedBlockSlotDb = dao.getEarliestFinalizedBlockSlot();
    if (maybeEarliestFinalizedBlockSlotDb.isEmpty()) {
      return maybeNewEarliestBlockSlot.map(SignedBeaconBlock::getSlot);
    }
    // New value is smaller than value from DB - updating
    final UInt64 newEarliestBlockSlot = maybeNewEarliestBlockSlot.get().getSlot();
    if (newEarliestBlockSlot.isLessThan(maybeEarliestFinalizedBlockSlotDb.get())) {
      return maybeNewEarliestBlockSlot.map(SignedBeaconBlock::getSlot);
    } else {
      return Optional.empty();
    }
  }

  private Optional<UInt64> needToUpdateEarliestBlobSidecarSlot(
      final Optional<UInt64> maybeNewEarliestBlobSidecarSlot) {
    // New value is absent - not updating
    if (maybeNewEarliestBlobSidecarSlot.isEmpty()) {
      return Optional.empty();
    }
    // New value is present, value from DB is absent - updating
    final Optional<UInt64> maybeEarliestFinalizedBlockSlotDb = dao.getEarliestFinalizedBlockSlot();
    if (maybeEarliestFinalizedBlockSlotDb.isEmpty()) {
      return maybeNewEarliestBlobSidecarSlot;
    }
    // New value is smaller than value from DB - updating
    final UInt64 newEarliestBlobSidecarSlot = maybeNewEarliestBlobSidecarSlot.get();
    if (newEarliestBlobSidecarSlot.isLessThan(maybeEarliestFinalizedBlockSlotDb.get())) {
      return maybeNewEarliestBlobSidecarSlot;
    } else {
      return Optional.empty();
    }
  }

  protected Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root) {
    return dao.getHotBlock(root);
  }

  @Override
  public Map<String, Long> getColumnCounts(final Optional<String> maybeColumnFilter) {
    return dao.getColumnCounts(maybeColumnFilter);
  }

  @Override
  public Map<String, Optional<String>> getVariables() {
    return dao.getVariables();
  }

  @Override
  public long getBlobSidecarColumnCount() {
    return dao.getBlobSidecarColumnCount();
  }

  @Override
  public long getSidecarColumnCount() {
    return dataColumnStorage.getCount();
  }

  @Override
  public long getNonCanonicalBlobSidecarColumnCount() {
    return dao.getNonCanonicalBlobSidecarColumnCount();
  }

  @Override
  public void deleteHotBlocks(final Set<Bytes32> blockRootsToDelete) {
    try (final HotUpdater updater = hotUpdater()) {
      blockRootsToDelete.forEach(updater::deleteHotBlock);
      updater.commit();
    }
  }

  @Override
  public UInt64 pruneFinalizedBlocks(
      final UInt64 lastSlotToPrune, final int pruneLimit, final UInt64 checkpointInitialSlot) {
    final Optional<UInt64> earliestFinalizedBlockSlot = dao.getEarliestFinalizedBlockSlot();
    LOG.debug(
        "Earliest block slot stored is {}",
        () -> earliestFinalizedBlockSlot.map(UInt64::toString).orElse("EMPTY"));
    if (earliestFinalizedBlockSlot.isEmpty()) {
      return lastSlotToPrune;
    }
    return pruneToBlock(
        lastSlotToPrune, earliestFinalizedBlockSlot.get(), pruneLimit, checkpointInitialSlot);
  }

  private UInt64 pruneToBlock(
      final UInt64 lastSlotToPrune,
      final UInt64 earliestFinalizedBlockSlot,
      final int pruneLimit,
      final UInt64 checkpointInitialSlot) {
    final List<Pair<UInt64, Bytes32>> blocksToPrune;
    final Optional<UInt64> earliestSlotAvailableAfterPrune;
    LOG.debug("Pruning finalized blocks to slot {} (included)", lastSlotToPrune);
    if (lastSlotToPrune.isLessThanOrEqualTo(earliestFinalizedBlockSlot)) {
      LOG.debug(
          "Last slot to prune {} was lower than the earliest finalized block slot in the database {}",
          lastSlotToPrune,
          earliestFinalizedBlockSlot);
      return lastSlotToPrune;
    }
    try (final Stream<SignedBeaconBlock> stream =
        dao.streamFinalizedBlocks(earliestFinalizedBlockSlot, lastSlotToPrune)) {
      // get an extra block to set earliest finalized block slot available after pruning runs
      // ensuring it is an existing block in the DB
      blocksToPrune =
          stream.limit(pruneLimit).map(block -> Pair.of(block.getSlot(), block.getRoot())).toList();
    }

    if (blocksToPrune.isEmpty()) {
      LOG.debug("No finalized blocks to prune up to {} slot", lastSlotToPrune);
      return lastSlotToPrune;
    }

    try (final Stream<SignedBeaconBlock> stream =
        dao.streamFinalizedBlocks(earliestFinalizedBlockSlot, checkpointInitialSlot)) {

      earliestSlotAvailableAfterPrune =
          stream
              .map(SignedBeaconBlock::getSlot)
              .filter(slot -> slot.isGreaterThan(blocksToPrune.getLast().getLeft()))
              .findFirst();
    }

    final UInt64 lastPrunedBlockSlot = blocksToPrune.getLast().getKey();
    LOG.debug(
        "Pruning {} finalized blocks, last block slot is {}",
        blocksToPrune.size(),
        lastPrunedBlockSlot);
    deleteFinalizedBlocks(blocksToPrune, earliestSlotAvailableAfterPrune);

    return blocksToPrune.size() < pruneLimit ? lastSlotToPrune : lastPrunedBlockSlot;
  }

  private void deleteFinalizedBlocks(
      final List<Pair<UInt64, Bytes32>> blocksToPrune,
      final Optional<UInt64> earliestSlotAvailableAfterPrune) {
    if (blocksToPrune.size() > 0) {
      if (blocksToPrune.size() < 20) {
        LOG.debug(
            "Received blocks ({}) to delete",
            () -> blocksToPrune.stream().map(Pair::getLeft).toList());
      } else {
        LOG.debug("Received {} finalized blocks to delete", blocksToPrune.size());
      }

      try (final FinalizedUpdater updater = finalizedUpdater()) {
        blocksToPrune.forEach(
            pair -> updater.deleteFinalizedBlock(pair.getLeft(), pair.getRight()));
        earliestSlotAvailableAfterPrune.ifPresentOrElse(
            updater::setEarliestBlockSlot, updater::deleteEarliestBlockSlot);
        updater.commit();
      }
    }
  }

  @Override
  public Optional<UInt64> pruneFinalizedStates(
      final Optional<UInt64> lastPrunedSlot, final UInt64 lastSlotToPrune, final long pruneLimit) {
    final Optional<UInt64> earliestFinalizedStateSlot;

    if (lastPrunedSlot.isEmpty()) {
      earliestFinalizedStateSlot = dao.getEarliestFinalizedStateSlot();
    } else {
      earliestFinalizedStateSlot = lastPrunedSlot;
    }

    LOG.debug(
        "Earliest finalized state stored is for slot {}",
        () ->
            earliestFinalizedStateSlot.isEmpty()
                ? "EMPTY"
                : earliestFinalizedStateSlot.get().toString());
    return earliestFinalizedStateSlot
        .map(uInt64 -> pruneFinalizedStateForSlots(uInt64, lastSlotToPrune, pruneLimit))
        .or(() -> Optional.of(lastSlotToPrune));
  }

  private UInt64 pruneFinalizedStateForSlots(
      final UInt64 earliestFinalizedStateSlot,
      final UInt64 lastSlotToPrune,
      final long pruneLimit) {
    final List<Pair<UInt64, Optional<Bytes32>>> slotsToPruneStateFor;

    try (final Stream<UInt64> stream =
        dao.streamFinalizedStateSlots(earliestFinalizedStateSlot, lastSlotToPrune)) {
      slotsToPruneStateFor =
          stream
              .limit(pruneLimit)
              .map(
                  slot ->
                      Pair.of(
                          slot,
                          dao.getFinalizedBlockAtSlot(slot).map(SignedBeaconBlock::getStateRoot)))
              .toList();
    }
    if (slotsToPruneStateFor.isEmpty()) {
      LOG.debug("No finalized state to prune up to {} slot", lastSlotToPrune);
      return lastSlotToPrune;
    }

    final UInt64 lastPrunedSlot = slotsToPruneStateFor.getLast().getLeft();

    deleteFinalizedStatesForSlot(slotsToPruneStateFor);

    return slotsToPruneStateFor.size() < pruneLimit ? lastSlotToPrune : lastPrunedSlot;
  }

  private void deleteFinalizedStatesForSlot(
      final List<Pair<UInt64, Optional<Bytes32>>> slotsToPruneStateFor) {
    if (!slotsToPruneStateFor.isEmpty()) {
      if (slotsToPruneStateFor.size() < 20) {
        LOG.debug(
            "Received finalized slots ({}) to delete state",
            () -> slotsToPruneStateFor.stream().map(Pair::getLeft).toList());
      } else {
        LOG.debug("Received {} finalized slots to delete state", slotsToPruneStateFor.size());
      }

      try (final FinalizedUpdater updater = finalizedUpdater()) {
        slotsToPruneStateFor.forEach(
            pair -> {
              updater.deleteFinalizedState(pair.getLeft());
              pair.getRight().ifPresent(updater::deleteFinalizedStateRoot);
            });

        updater.commit();
      } catch (Exception e) {
        LOG.error("Failed to prune finalized states", e);
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
  public void storeInitialAnchor(final AnchorPoint initialAnchor) {
    try (final CombinedUpdater updater = combinedUpdater()) {
      // We should only have a single block / state / checkpoint at anchorpoint initialization
      final Checkpoint anchorCheckpoint = initialAnchor.getCheckpoint();
      final Bytes32 anchorRoot = anchorCheckpoint.getRoot();
      final BeaconState anchorState = initialAnchor.getState();
      final Optional<SignedBeaconBlock> anchorBlock = initialAnchor.getSignedBeaconBlock();

      updater.setAnchor(initialAnchor.getCheckpoint());
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
      if (initialAnchor.isGenesis()
          && spec.atSlot(initialAnchor.getSlot())
              .getMilestone()
              .isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
        updater.setEarliestBlockSlot(initialAnchor.getSlot());
        updater.setEarliestBlobSidecarSlot(initialAnchor.getSlot());
      }

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
      final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsBySlot,
      final Optional<UInt64> maybeEarliestBlobSidecarSlot) {
    if (blocks.isEmpty()) {
      return;
    }

    // Sort blocks and verify that they are contiguous with the oldestBlock
    final List<SignedBeaconBlock> sorted =
        blocks.stream()
            .sorted(Comparator.comparing(SignedBeaconBlock::getSlot).reversed())
            .toList();

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

    storeFinalizedBlocksToDao(blocks, blobSidecarsBySlot, maybeEarliestBlobSidecarSlot);
  }

  @Override
  public void updateWeakSubjectivityState(final WeakSubjectivityUpdate weakSubjectivityUpdate) {
    try (final HotUpdater updater = hotUpdater()) {
      Optional<Checkpoint> checkpoint = weakSubjectivityUpdate.getWeakSubjectivityCheckpoint();
      checkpoint.ifPresentOrElse(
          updater::setWeakSubjectivityCheckpoint, updater::clearWeakSubjectivityCheckpoint);
      updater.commit();
    }
  }

  @Override
  public void storeReconstructedFinalizedState(final BeaconState state, final Bytes32 blockRoot) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.addReconstructedFinalizedState(blockRoot, state);
      handleAddFinalizedStateRoot(state, updater);
    }
  }

  private void handleAddFinalizedStateRoot(
      final BeaconState state, final FinalizedUpdater updater) {
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
    final Optional<Bytes32> latestCanonicalBlockRoot = dao.getLatestCanonicalBlockRoot();
    final Optional<UInt64> custodyGroupCount = dao.getCustodyGroupCount();

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
    final UInt64 slotTime = spec.computeTimeAtSlot(finalizedState.getSlot(), genesisTime);
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
            votes,
            latestCanonicalBlockRoot,
            custodyGroupCount));
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
  public void removeDepositsFromBlockEvents(final List<UInt64> blockNumbers) {
    try (final HotUpdater updater = hotUpdater()) {
      blockNumbers.forEach(updater::removeDepositsFromBlockEvent);
      updater.commit();
    }
  }

  @Override
  public void storeBlobSidecar(final BlobSidecar blobSidecar) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.addBlobSidecar(blobSidecar);
      updater.commit();
    }
  }

  @Override
  public void storeNonCanonicalBlobSidecar(final BlobSidecar blobSidecar) {
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      updater.addNonCanonicalBlobSidecar(blobSidecar);
      updater.commit();
    }
  }

  @Override
  public Optional<BlobSidecar> getBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
    final Optional<Bytes> maybePayload = dao.getBlobSidecar(key);
    return maybePayload.map(payload -> spec.deserializeBlobSidecar(payload, key.getSlot()));
  }

  @Override
  public Optional<BlobSidecar> getNonCanonicalBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
    final Optional<Bytes> maybePayload = dao.getNonCanonicalBlobSidecar(key);
    return maybePayload.map(payload -> spec.deserializeBlobSidecar(payload, key.getSlot()));
  }

  @Override
  public boolean pruneOldestBlobSidecars(
      final UInt64 lastSlotToPrune,
      final int pruneLimit,
      final BlobSidecarsArchiver blobSidecarsArchiver) {
    final Optional<UInt64> earliestBlobSidecarSlot = getEarliestBlobSidecarSlot();
    if (earliestBlobSidecarSlot.isPresent()
        && earliestBlobSidecarSlot.get().isGreaterThan(lastSlotToPrune)) {
      return false;
    }
    try (final Stream<SlotAndBlockRootAndBlobIndex> prunableBlobKeys =
        streamBlobSidecarKeys(earliestBlobSidecarSlot.orElse(UInt64.ZERO), lastSlotToPrune)) {
      return pruneBlobSidecars(pruneLimit, prunableBlobKeys, blobSidecarsArchiver, false);
    }
  }

  @Override
  public boolean pruneOldestNonCanonicalBlobSidecars(
      final UInt64 lastSlotToPrune,
      final int pruneLimit,
      final BlobSidecarsArchiver blobSidecarsArchiver) {
    final Optional<UInt64> earliestBlobSidecarSlot = getEarliestBlobSidecarSlot();
    if (earliestBlobSidecarSlot.isPresent()
        && earliestBlobSidecarSlot.get().isGreaterThan(lastSlotToPrune)) {
      return false;
    }
    try (final Stream<SlotAndBlockRootAndBlobIndex> prunableNoncanonicalBlobKeys =
        streamNonCanonicalBlobSidecarKeys(
            earliestBlobSidecarSlot.orElse(UInt64.ZERO), lastSlotToPrune)) {
      return pruneBlobSidecars(
          pruneLimit, prunableNoncanonicalBlobKeys, blobSidecarsArchiver, true);
    }
  }

  private boolean pruneBlobSidecars(
      final int pruneLimit,
      final Stream<SlotAndBlockRootAndBlobIndex> prunableBlobKeys,
      final BlobSidecarsArchiver blobSidecarsArchiver,
      final boolean nonCanonicalBlobSidecars) {

    int pruned = 0;
    Optional<UInt64> earliestBlobSidecarSlot = Optional.empty();

    // Group the BlobSidecars by slot. Potential for higher memory usage
    // if it hasn't been pruned in a while
    final Map<UInt64, List<SlotAndBlockRootAndBlobIndex>> prunableMap =
        prunableBlobKeys.collect(groupingBy(SlotAndBlockRootAndBlobIndex::getSlot));

    // pruneLimit is the number of slots to prune, not the number of BlobSidecars
    final List<UInt64> slots = prunableMap.keySet().stream().sorted().limit(pruneLimit).toList();
    try (final FinalizedUpdater updater = finalizedUpdater()) {
      for (final UInt64 slot : slots) {
        final List<SlotAndBlockRootAndBlobIndex> keys = prunableMap.get(slot);

        // Retrieve the BlobSidecars for archiving.
        final List<BlobSidecar> blobSidecars =
            keys.stream()
                .map(
                    nonCanonicalBlobSidecars
                        ? this::getNonCanonicalBlobSidecar
                        : this::getBlobSidecar)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        // Just warn if we failed to find all the BlobSidecars.
        if (keys.size() != blobSidecars.size()) {
          LOG.warn("Failed to retrieve BlobSidecars for keys: {}", keys);
        }

        if (!keys.isEmpty()) {
          final SlotAndBlockRoot slotAndBlockRoot = keys.getFirst().getSlotAndBlockRoot();
          blobSidecarsArchiver.archive(slotAndBlockRoot, blobSidecars);
        }

        // Remove the BlobSidecars from the database.
        for (final SlotAndBlockRootAndBlobIndex key : keys) {
          if (nonCanonicalBlobSidecars) {
            updater.removeNonCanonicalBlobSidecar(key);
          } else {
            updater.removeBlobSidecar(key);
            earliestBlobSidecarSlot = Optional.of(slot.plus(1));
          }
        }

        ++pruned;
      }

      if (!nonCanonicalBlobSidecars) {
        earliestBlobSidecarSlot.ifPresent(updater::setEarliestBlobSidecarSlot);
      }
      updater.commit();
    }
    LOG.debug("Pruned {} BlobSidecars", pruned);
    // `pruned` will be greater when we reach pruneLimit not on the latest BlobSidecar in a slot
    return pruned >= pruneLimit;
  }

  @MustBeClosed
  @Override
  public Stream<SlotAndBlockRootAndBlobIndex> streamBlobSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot) {
    return dao.streamBlobSidecarKeys(startSlot, endSlot);
  }

  @MustBeClosed
  @Override
  public Stream<SlotAndBlockRootAndBlobIndex> streamNonCanonicalBlobSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot) {
    return dao.streamNonCanonicalBlobSidecarKeys(startSlot, endSlot);
  }

  @MustBeClosed
  @Override
  public Stream<BlobSidecar> streamBlobSidecars(final SlotAndBlockRoot slotAndBlockRoot) {
    return dao.streamBlobSidecars(slotAndBlockRoot)
        .map(payload -> spec.deserializeBlobSidecar(payload, slotAndBlockRoot.getSlot()));
  }

  @Override
  public List<SlotAndBlockRootAndBlobIndex> getBlobSidecarKeys(
      final SlotAndBlockRoot slotAndBlockRoot) {
    final List<SlotAndBlockRootAndBlobIndex> blobSidecarKeys =
        dao.getBlobSidecarKeys(slotAndBlockRoot);
    if (blobSidecarKeys.isEmpty()) {
      return dao.getNonCanonicalBlobSidecarKeys(slotAndBlockRoot);
    } else {
      return blobSidecarKeys;
    }
  }

  @Override
  public Optional<UInt64> getEarliestBlobSidecarSlot() {
    return dao.getEarliestBlobSidecarSlot();
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
  public void setFinalizedDepositSnapshot(final DepositTreeSnapshot finalizedDepositSnapshot) {
    try (final HotUpdater updater = hotUpdater()) {
      updater.setFinalizedDepositSnapshot(finalizedDepositSnapshot);
      updater.commit();
    }
  }

  @Override
  public Optional<UInt64> getFirstCustodyIncompleteSlot() {
    return dataColumnStorage.getFirstCustodyIncompleteSlot();
  }

  @Override
  public Optional<DataColumnSidecar> getSidecar(final DataColumnSlotAndIdentifier identifier) {
    return dataColumnStorage.getSidecar(identifier);
  }

  @Override
  public Optional<DataColumnSidecar> getNonCanonicalSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    return dataColumnStorage.getNonCanonicalSidecar(identifier);
  }

  @Override
  @MustBeClosed
  public Stream<DataColumnSlotAndIdentifier> streamDataColumnIdentifiers(
      final UInt64 firstSlot, final UInt64 lastSlot) {
    return dataColumnStorage.streamIdentifiers(firstSlot, lastSlot);
  }

  @Override
  @MustBeClosed
  public Stream<DataColumnSlotAndIdentifier> streamNonCanonicalDataColumnIdentifiers(
      final UInt64 firstSlot, final UInt64 lastSlot) {
    return dataColumnStorage.streamNonCanonicalIdentifiers(firstSlot, lastSlot);
  }

  @Override
  public Optional<UInt64> getEarliestDataColumnSidecarSlot() {
    return dataColumnStorage.getEarliestSlot();
  }

  @Override
  public Optional<UInt64> getEarliestAvailableDataColumnSlot() {
    return dataColumnStorage.getEarliestAvailableSlot();
  }

  @Override
  public void setEarliestAvailableDataColumnSlot(final UInt64 slot) {
    dataColumnStorage.setEarliestAvailableSlot(slot);
  }

  @Override
  public Optional<UInt64> getLastDataColumnSidecarsProofsSlot() {
    return dao.getLastDataColumnSidecarsProofsSlot();
  }

  @Override
  public Optional<List<List<KZGProof>>> getDataColumnSidecarsProofs(final UInt64 slot) {
    return dao.getDataColumnSidecarsProofs(slot);
  }

  @Override
  public void setFirstCustodyIncompleteSlot(final UInt64 slot) {
    dataColumnStorage.setFirstCustodyIncompleteSlot(slot);
  }

  @Override
  public void addSidecar(final DataColumnSidecar sidecar) {
    dataColumnStorage.addSidecar(sidecar);
  }

  @Override
  public void addNonCanonicalSidecar(final DataColumnSidecar sidecar) {
    dataColumnStorage.addNonCanonicalSidecar(sidecar);
  }

  @Override
  public void pruneAllSidecars(final UInt64 tillSlotInclusive, final int pruneLimit) {
    // Prune epochs up to the epoch containing tillSlotInclusive
    // Both canonical and non-canonical are stored together, so one call handles both
    final Optional<UInt64> lastPrunedEpoch =
        dataColumnStorage.pruneEpochs(tillSlotInclusive, pruneLimit);

    // Update earliest available slot metadata only if we actually pruned something
    lastPrunedEpoch.ifPresent(
        epoch -> {
          // The new earliest available slot is the first slot of the next epoch after the last
          // pruned epoch
          final UInt64 newEarliestSlot = spec.computeStartSlotAtEpoch(epoch.plus(1));
          dataColumnStorage.setEarliestAvailableSlot(newEarliestSlot);
          LOG.debug(
              "Updated earliest available data column slot to {} after pruning epoch {}",
              newEarliestSlot,
              epoch);
        });
  }

  /**
   * Migrate existing DataColumnSidecars from database to file storage.
   *
   * <p>This is a one-time migration that runs on node startup. It moves all DataColumnSidecars from
   * the key-value store database to the file-based storage system.
   */
  public void migrateDataColumnSidecarsToFileStorage() {
    // Check if migration already done
    if (dataColumnStorage.isMigrationComplete()) {
      LOG.info("Data column migration already completed, skipping");
      return;
    }

    LOG.info("Starting migration of DataColumnSidecars from database to file storage");

    // Determine range to migrate
    final Optional<UInt64> earliestSlot = dao.getEarliestDataSidecarColumnSlot();
    final Optional<UInt64> latestSlot = dao.getLastDataColumnSidecarsProofsSlot();

    if (earliestSlot.isEmpty()) {
      LOG.info("No DataColumnSidecars in database, migration not needed");
      dataColumnStorage.setMigrationComplete(true);
      return;
    }

    // Migrate in batches (32 slots = 1 epoch, aligns with filesystem structure)
    final int batchSizeSlots = 32;
    UInt64 currentSlot = earliestSlot.get();
    final UInt64 endSlot = latestSlot.orElse(currentSlot);
    int totalMigrated = 0;

    while (currentSlot.isLessThanOrEqualTo(endSlot)) {
      final UInt64 batchEndSlot = currentSlot.plus(batchSizeSlots).min(endSlot);

      try (final FinalizedUpdater updater = finalizedUpdater()) {
        final int batchCount = migrateBatch(currentSlot, batchEndSlot, updater);
        updater.commit();
        totalMigrated += batchCount;
        if (batchCount > 0) {
          LOG.debug("Migrated {} sidecars (slots {}-{})", batchCount, currentSlot, batchEndSlot);
        }
      } catch (Exception e) {
        LOG.error(
            "Failed to migrate data column batch (slots {}-{}), will retry on next startup",
            currentSlot,
            batchEndSlot,
            e);
        // Don't set migration complete, so it will retry on next startup
        return;
      }

      currentSlot = batchEndSlot.plus(1);
    }

    dataColumnStorage.setMigrationComplete(true);
    LOG.info("Migration completed: {} DataColumnSidecars moved to file storage", totalMigrated);
  }

  private int migrateBatch(
      final UInt64 startSlot, final UInt64 endSlot, final FinalizedUpdater updater) {
    int count = 0;
    try (final Stream<DataColumnSlotAndIdentifier> stream =
        dao.streamDataColumnIdentifiers(startSlot, endSlot)) {

      final Iterator<DataColumnSlotAndIdentifier> iterator = stream.iterator();
      while (iterator.hasNext()) {
        final DataColumnSlotAndIdentifier identifier = iterator.next();

        // Get sidecar bytes from database
        final Optional<Bytes> sidecarBytes = dao.getSidecar(identifier);
        if (sidecarBytes.isPresent()) {
          // Deserialize
          final DataColumnSidecar sidecar =
              spec.deserializeSidecar(sidecarBytes.get(), identifier.slot());

          // Write to file storage
          dataColumnStorage.addSidecar(sidecar);

          // Delete from database
          updater.removeSidecar(identifier);
          count++;
        }
      }
    }
    return count;
  }

  @Override
  public void close() throws Exception {
    dao.close();
  }

  private UpdateResult doUpdate(final StorageUpdate update) {
    LOG.trace("Applying finalized updates");
    long startTime = System.currentTimeMillis();
    // Update finalized blocks and states
    final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticExecutionPayload =
        updateFinalizedData(
            update.getFinalizedChildToParentMap(),
            update.getFinalizedBlocks(),
            update.getFinalizedStates(),
            update.getDeletedHotBlocks(),
            update.isFinalizedOptimisticTransitionBlockRootSet(),
            update.getOptimisticTransitionBlockRoot());

    if (update.isBlobSidecarsEnabled()) {
      removeNonCanonicalBlobSidecars(
          update.getDeletedHotBlocks(), update.getFinalizedChildToParentMap());

      updateBlobSidecarData(
          update.getEarliestBlobSidecarSlot(),
          update.getBlobSidecars().values().stream().flatMap(Collection::stream));
    }
    if (update.isSidecarsEnabled()) {
      removeNonCanonicalSidecars(
          update.getDeletedHotBlocks(), update.getFinalizedChildToParentMap());
    }
    long finalizedDataUpdatedTime = System.currentTimeMillis();

    LOG.trace("Applying hot updates");
    final long latestFinalizedStateUpdateStartTime;
    final long latestFinalizedStateUpdateEndTime;
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

      update.getLatestCanonicalBlockRoot().ifPresent(updater::setLatestCanonicalBlockRoot);
      update.getCustodyGroupCount().ifPresent(updater::setCustodyGroupCount);
      update.getJustifiedCheckpoint().ifPresent(updater::setJustifiedCheckpoint);
      update.getBestJustifiedCheckpoint().ifPresent(updater::setBestJustifiedCheckpoint);
      latestFinalizedStateUpdateStartTime = System.currentTimeMillis();
      update.getLatestFinalizedState().ifPresent(updater::setLatestFinalizedState);
      latestFinalizedStateUpdateEndTime = System.currentTimeMillis();

      updateHotBlocks(updater, update.getHotBlocks(), update.getDeletedHotBlocks().keySet());
      updater.addHotStates(update.getHotStates());

      if (update.getStateRoots().size() > 0) {
        updater.addHotStateRoots(update.getStateRoots());
      }

      // Delete finalized data from hot db

      LOG.trace("Committing hot db changes");
      updater.commit();
    }

    long endTime = System.currentTimeMillis();
    DB_LOGGER.onDbOpAlertThreshold(
        "KvStoreDatabase::doUpdate",
        () ->
            String.format(
                "Finalized data updated time: %d ms - Hot data updated time: %d ms of which latest finalized state updated time: %d ms",
                finalizedDataUpdatedTime - startTime,
                endTime - finalizedDataUpdatedTime,
                latestFinalizedStateUpdateEndTime - latestFinalizedStateUpdateStartTime),
        startTime,
        endTime);
    LOG.trace("Update complete");
    return new UpdateResult(finalizedOptimisticExecutionPayload);
  }

  private void updateBlobSidecarData(
      final Optional<UInt64> getEarliestBlobSidecarSlot, final Stream<BlobSidecar> blobSidecars) {
    LOG.trace("Applying blob sidecar updates");

    try (final FinalizedUpdater updater = finalizedUpdater()) {
      // add blob sidecars
      blobSidecars.forEach(updater::addBlobSidecar);

      // update the earliest blob sidecar slot if needed
      getEarliestBlobSidecarSlot.ifPresent(
          earliestSlot -> {
            if (dao.getEarliestBlobSidecarSlot().isEmpty()) {
              updater.setEarliestBlobSidecarSlot(earliestSlot);
            }
          });

      LOG.trace("Committing blob sidecar updates");
      updater.commit();
    }
  }

  private Optional<SlotAndExecutionPayloadSummary> updateFinalizedData(
      final Map<Bytes32, Bytes32> finalizedChildToParentMap,
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

  private void removeNonCanonicalBlobSidecars(
      final Map<Bytes32, UInt64> deletedHotBlocks,
      final Map<Bytes32, Bytes32> finalizedChildToParentMap) {

    final Set<SlotAndBlockRoot> nonCanonicalBlocks =
        deletedHotBlocks.entrySet().stream()
            .filter(entry -> !finalizedChildToParentMap.containsKey(entry.getKey()))
            .map(entry -> new SlotAndBlockRoot(entry.getValue(), entry.getKey()))
            .collect(Collectors.toSet());

    if (storeNonCanonicalBlocks) {
      final Iterator<SlotAndBlockRoot> nonCanonicalBlocksIterator = nonCanonicalBlocks.iterator();
      int index = 0;
      while (nonCanonicalBlocksIterator.hasNext()) {
        final int start = index;
        try (final FinalizedUpdater updater = finalizedUpdater()) {
          while (nonCanonicalBlocksIterator.hasNext() && (index - start) < BLOBS_TX_BATCH_SIZE) {
            dao.getBlobSidecarKeys(nonCanonicalBlocksIterator.next())
                .forEach(
                    key -> {
                      dao.getBlobSidecar(key)
                          .ifPresent(
                              blobSidecarBytes -> {
                                updater.addNonCanonicalBlobSidecarRaw(blobSidecarBytes, key);
                                updater.removeBlobSidecar(key);
                              });
                    });
            index++;
          }
          updater.commit();
        }
      }
    } else {
      LOG.trace("Removing blob sidecars for non-canonical blocks");
      try (final FinalizedUpdater updater = finalizedUpdater()) {
        for (final SlotAndBlockRoot slotAndBlockRoot : nonCanonicalBlocks) {
          dao.getBlobSidecarKeys(slotAndBlockRoot)
              .forEach(
                  key -> {
                    LOG.trace("Removing blobSidecar with index {} for non-canonical block", key);
                    updater.removeBlobSidecar(key);
                  });
        }
        updater.commit();
      }
    }
  }

  private void removeNonCanonicalSidecars(
      final Map<Bytes32, UInt64> deletedHotBlocks,
      final Map<Bytes32, Bytes32> finalizedChildToParentMap) {

    final Set<SlotAndBlockRoot> nonCanonicalBlocks =
        deletedHotBlocks.entrySet().stream()
            .filter(entry -> !finalizedChildToParentMap.containsKey(entry.getKey()))
            .map(entry -> new SlotAndBlockRoot(entry.getValue(), entry.getKey()))
            .collect(Collectors.toSet());

    if (storeNonCanonicalBlocks) {
      final Iterator<SlotAndBlockRoot> nonCanonicalBlocksIterator = nonCanonicalBlocks.iterator();
      int index = 0;
      while (nonCanonicalBlocksIterator.hasNext()) {
        final int start = index;
        try (final FinalizedUpdater updater = finalizedUpdater()) {
          while (nonCanonicalBlocksIterator.hasNext() && (index - start) < BLOBS_TX_BATCH_SIZE) {
            dao.getDataColumnIdentifiers(nonCanonicalBlocksIterator.next())
                .forEach(
                    key -> {
                      dao.getSidecar(key)
                          .ifPresent(
                              sidecarBytes -> {
                                DataColumnSidecar sideCar =
                                    spec.deserializeSidecar(sidecarBytes, key.slot());
                                updater.addNonCanonicalSidecar(sideCar);
                                LOG.trace(
                                    "Moving non-canonical sidecar with identifier {} to non-canonical sidecars table",
                                    key);
                                updater.removeSidecar(key);
                              });
                    });
            index++;
          }
          updater.commit();
        }
      }
    } else {
      LOG.trace("Removing sidecars for non-canonical blocks");
      try (final FinalizedUpdater updater = finalizedUpdater()) {
        for (final SlotAndBlockRoot slotAndBlockRoot : nonCanonicalBlocks) {
          dao.getDataColumnIdentifiers(slotAndBlockRoot)
              .forEach(
                  key -> {
                    LOG.trace("Removing sidecar with identifier {} for non-canonical block", key);
                    updater.removeSidecar(key);
                  });
        }
        updater.commit();
      }
    }
  }

  private void updateFinalizedDataArchiveMode(
      final Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates) {

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
            .toList();

    int i = 0;
    UInt64 lastSlot = baseBlock.getSlot();
    while (i < finalizedRoots.size()) {
      final int start = i;
      try (final FinalizedUpdater updater = finalizedUpdater()) {
        final StateRootRecorder recorder =
            new StateRootRecorder(lastSlot, updater::addFinalizedStateRoot, spec);

        while (i < finalizedRoots.size() && (i - start) < TX_BATCH_SIZE) {
          final Bytes32 blockRoot = finalizedRoots.get(i);

          final Optional<UInt64> maybeBlockSlot =
              addFinalizedBlock(blockRoot, finalizedBlocks, updater, initialBlockRoot);

          Optional.ofNullable(finalizedStates.get(blockRoot))
              .or(() -> getHotState(blockRoot))
              .ifPresent(
                  state -> {
                    updater.addFinalizedState(blockRoot, state);
                    recorder.acceptNextState(state);
                  });

          lastSlot =
              maybeBlockSlot.orElseGet(
                  () -> initialCheckpoint.orElseThrow().getEpochStartSlot(spec));
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
      final Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks) {
    final Optional<Bytes32> initialBlockRoot = dao.getAnchor().map(Checkpoint::getRoot);

    final List<Bytes32> finalizedRoots = new ArrayList<>(finalizedChildToParentMap.keySet());
    int i = 0;
    while (i < finalizedRoots.size()) {
      try (final FinalizedUpdater updater = finalizedUpdater()) {
        final int start = i;
        while (i < finalizedRoots.size() && (i - start) < TX_BATCH_SIZE) {
          final Bytes32 root = finalizedRoots.get(i);

          addFinalizedBlock(root, finalizedBlocks, updater, initialBlockRoot);

          i++;
        }
        updater.commit();
        if (i >= TX_BATCH_SIZE) {
          STATUS_LOG.recordedFinalizedBlocks(i, finalizedRoots.size());
        }
      }
    }
  }

  private Optional<UInt64> addFinalizedBlock(
      final Bytes32 blockRoot,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final FinalizedUpdater updater,
      final Optional<Bytes32> initialBlockRoot) {
    final SignedBeaconBlock block = finalizedBlocks.get(blockRoot);
    if (block != null) {
      addFinalizedBlock(block, updater);
      return Optional.of(block.getSlot());
    }

    final Optional<Bytes> rawBlock = dao.getHotBlockAsSsz(blockRoot);
    if (rawBlock.isPresent()) {
      final UInt64 slot = BeaconBlockInvariants.extractSignedBlockContainerSlot(rawBlock.get());
      updater.addFinalizedBlockRaw(slot, blockRoot, rawBlock.get());
      return Optional.of(slot);
    }

    // If block is missing and doesn't match the initial anchor, throw
    if (initialBlockRoot.filter(r -> r.equals(blockRoot)).isEmpty()) {
      throw new IllegalStateException("Missing finalized block");
    }

    return Optional.empty();
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
      final FinalizedUpdater updater, final Bytes32 blockRoot, final BeaconState state) {
    if (stateStorageMode.storesFinalizedStates()) {
      updater.addFinalizedState(blockRoot, state);
      updater.addFinalizedStateRoot(state.hashTreeRoot(), state.getSlot());
    }
  }
}
