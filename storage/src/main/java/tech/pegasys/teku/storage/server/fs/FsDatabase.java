package tech.pegasys.teku.storage.server.fs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.primitives.UnsignedLong;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.storage.events.AnchorPoint;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.store.StoreBuilder;

public class FsDatabase implements Database {

  private final MetricsSystem metricsSystem;
  private final FsStorage storage;

  public FsDatabase(final MetricsSystem metricsSystem, final FsStorage storage) {
    this.metricsSystem = metricsSystem;
    this.storage = storage;
  }

  @Override
  public void storeGenesis(final AnchorPoint genesis) {
    // We should only have a single block / state / checkpoint at genesis
    final Checkpoint genesisCheckpoint = genesis.getCheckpoint();
    final Bytes32 genesisRoot = genesisCheckpoint.getRoot();
    final BeaconState genesisState = genesis.getState();
    final SignedBeaconBlock genesisBlock = genesis.getBlock();
    try (final FsStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeJustifiedCheckpoint(genesisCheckpoint);
      transaction.storeBestJustifiedCheckpoint(genesisCheckpoint);
      transaction.storeFinalizedCheckpoint(genesisCheckpoint);

      transaction.storeBlock(genesisBlock, true);
      transaction.storeState(genesisState);
    }
  }

  @Override
  public void update(final StorageUpdate update) {
    if (update.isEmpty()) {
      return;
    }

    try (final FsStorage.Transaction transaction = storage.startTransaction()) {
      update.getFinalizedCheckpoint().ifPresent(transaction::storeFinalizedCheckpoint);
      update.getJustifiedCheckpoint().ifPresent(transaction::storeJustifiedCheckpoint);
      update.getBestJustifiedCheckpoint().ifPresent(transaction::storeBestJustifiedCheckpoint);

      update.getHotBlocks().values().forEach(block -> transaction.storeBlock(block, false));
      update.getDeletedHotBlocks().forEach(transaction::deleteBlock);
      transaction.storeVotes(update.getVotes());

      update.getFinalizedBlocks().forEach((root, block) -> transaction.finalizeBlock(block));

      // TODO: Periodically store finalized states
      transaction.commit();
    }
    storage.prune();
  }

  @Override
  public Optional<StoreBuilder> createMemoryStore() {
    final Optional<Checkpoint> maybeFinalizedCheckpoint = storage.getFinalizedCheckpoint();
    if (maybeFinalizedCheckpoint.isEmpty()) {
      return Optional.empty();
    }
    final Checkpoint justifiedCheckpoint = storage.getJustifiedCheckpoint().orElseThrow();
    final Checkpoint bestJustifiedCheckpoint = storage.getBestJustifiedCheckpoint().orElseThrow();
    final Checkpoint finalizedCheckpoint = maybeFinalizedCheckpoint.get();
    final BeaconState finalizedState =
        storage.getStateByBlockRoot(finalizedCheckpoint.getRoot()).orElseThrow();
    final SignedBeaconBlock finalizedBlock =
        storage.getBlockByBlockRoot(finalizedCheckpoint.getRoot()).orElse(null);
    checkNotNull(finalizedBlock);
    checkState(
        finalizedBlock.getMessage().getState_root().equals(finalizedState.hash_tree_root()),
        "Latest finalized state does not match latest finalized block");
    final Map<Bytes32, Bytes32> childToParentLookup = storage.getHotBlockChildToParentLookup();

    final Map<UnsignedLong, VoteTracker> votes = storage.loadVotes();

    return Optional.of(
        StoreBuilder.create()
            .metricsSystem(metricsSystem)
            .time(UnsignedLong.valueOf(Instant.now().getEpochSecond()))
            .genesisTime(finalizedState.getGenesis_time())
            .finalizedCheckpoint(finalizedCheckpoint)
            .justifiedCheckpoint(justifiedCheckpoint)
            .bestJustifiedCheckpoint(bestJustifiedCheckpoint)
            .childToParentMap(childToParentLookup)
            .latestFinalized(new SignedBlockAndState(finalizedBlock, finalizedState))
            .votes(votes));
  }

  @Override
  public Optional<UnsignedLong> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UnsignedLong slot) {
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UnsignedLong slot) {
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return Optional.empty();
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots) {
    return null;
  }

  @Override
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UnsignedLong startSlot, final UnsignedLong endSlot) {
    return null;
  }

  @Override
  public List<Bytes32> getStateRootsBeforeSlot(final UnsignedLong slot) {
    return null;
  }

  @Override
  public void addHotStateRoots(
      final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap) {}

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return Optional.empty();
  }

  @Override
  public void pruneHotStateRoots(final List<Bytes32> stateRoots) {}

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UnsignedLong maxSlot) {
    return Optional.empty();
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return Optional.empty();
  }

  @Override
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return null;
  }

  @Override
  public Optional<ProtoArraySnapshot> getProtoArraySnapshot() {
    return Optional.empty();
  }

  @Override
  public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {}

  @Override
  public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {}

  @Override
  public void putProtoArraySnapshot(final ProtoArraySnapshot protoArray) {}

  @Override
  public void close() throws Exception {}
}
