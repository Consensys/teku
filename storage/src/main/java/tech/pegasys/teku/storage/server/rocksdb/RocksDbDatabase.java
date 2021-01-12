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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE_FINALIZED_DB;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE_HOT_DB;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_BEACON_PROPOSER;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.MustBeClosed;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.datastructures.blocks.CheckpointEpochs;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.ValidatorsUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.protoarray.StoredBlockMetadata;
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
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.StateStorageMode;

public class RocksDbDatabase implements Database {
  private static final Logger LOG = LogManager.getLogger();

  private static final int TX_BATCH_SIZE = 500;

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

    // Check block signatures are valid for blocks except the genesis block
    boolean isGenesisBlockIncluded =
        Iterables.getLast(sorted).getSlot().equals(UInt64.valueOf(Constants.GENESIS_SLOT));
    checkArgument(
        batchVerifyHistoricalBlockSignatures(
            isGenesisBlockIncluded ? sorted.subList(0, sorted.size() - 1) : sorted),
        "Block signatures are invalid");

    try (final FinalizedUpdater updater = finalizedDao.finalizedUpdater()) {
      sorted.forEach(updater::addFinalizedBlock);
      updater.commit();
    }
  }

  boolean batchVerifyHistoricalBlockSignatures(final Collection<SignedBeaconBlock> blocks) {
    BeaconState finalizedState =
        this.hotDao
            .getLatestFinalizedState()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Finalized state is required to sync finalized historical blocks"));
    List<BLSSignature> signatures = new ArrayList<>();
    List<Bytes> signingRoots = new ArrayList<>();
    List<List<BLSPublicKey>> proposerPublicKeys = new ArrayList<>();

    // TODO: This domain is dependent on the fork version. Thus when we support forks, we're going
    // to have to change the way we retrieve the domain here.
    Bytes32 domain = get_domain(finalizedState, DOMAIN_BEACON_PROPOSER);

    blocks.forEach(
        signedBlock -> {
          BeaconBlock block = signedBlock.getMessage();
          signatures.add(signedBlock.getSignature());
          signingRoots.add(compute_signing_root(block, domain));
          BLSPublicKey proposerPublicKey =
              ValidatorsUtil.getValidatorPubKey(finalizedState, block.getProposerIndex())
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Proposer has to be in the state since state is more recent than the block proposed"));
          proposerPublicKeys.add(List.of(proposerPublicKey));
        });

    return BLS.batchVerify(proposerPublicKeys, signingRoots, signatures);
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
                    checkpointEpochs));
          });
    }
    // If anchor block is missing, try to pull block info from the anchor state
    final boolean shouldIncludeAnchorBlock =
        maybeAnchor.isPresent()
            && finalizedCheckpoint
                .getEpochStartSlot()
                .equals(maybeAnchor.get().getEpochStartSlot());
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
        AnchorPoint.create(finalizedCheckpoint, finalizedState, finalizedBlock);

    // Make sure time is set to a reasonable value in the case where we start up before genesis when
    // the clock time would be prior to genesis
    final long clockTime = timeSupplier.get();
    final UInt64 slotTime = genesisTime.plus(finalizedState.getSlot().times(SECONDS_PER_SLOT));
    final UInt64 time = slotTime.max(clockTime);

    return Optional.of(
        StoreBuilder.create()
            .metricsSystem(metricsSystem)
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
    try (final RocksDbHotDao.HotUpdater hotUpdater = hotDao.hotUpdater()) {
      protoArraySnapshot
          .getBlockInformationList()
          .forEach(
              block ->
                  hotUpdater.addHotBlockCheckpointEpochs(
                      block.getBlockRoot(),
                      new CheckpointEpochs(block.getJustifiedEpoch(), block.getFinalizedEpoch())));
      hotUpdater.commit();
    }
    try (final RocksDbProtoArrayDao.ProtoArrayUpdater updater = protoArrayDao.protoArrayUpdater()) {
      updater.deleteProtoArraySnapshot();
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
    LOG.trace("Applying finalized updates");
    // Update finalized blocks and states
    updateFinalizedData(
        update.getFinalizedChildToParentMap(),
        update.getFinalizedBlocks(),
        update.getFinalizedStates());

    LOG.trace("Applying hot updates");
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

      LOG.trace("Committing hot db changes");
      updater.commit();
    }
    LOG.trace("Update complete");
  }

  private void updateFinalizedData(
      Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates) {
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
            new StateRootRecorder(lastSlot, updater::addFinalizedStateRoot);

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
                  .orElseGet(() -> initialCheckpoint.orElseThrow().getEpochStartSlot());
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
