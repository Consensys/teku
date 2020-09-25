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

package tech.pegasys.teku.storage.server.sql;

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.storage.server.sql.CheckpointType.BEST_JUSTIFIED;
import static tech.pegasys.teku.storage.server.sql.CheckpointType.FINALIZED;
import static tech.pegasys.teku.storage.server.sql.CheckpointType.JUSTIFIED;
import static tech.pegasys.teku.storage.server.sql.CheckpointType.WEAK_SUBJECTIVITY;

import com.google.errorprone.annotations.MustBeClosed;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.storage.events.AnchorPoint;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.sql.SqlChainStorage.Transaction;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.StateStorageMode;

public class SqlDatabase implements Database {
  private static final Logger LOG = LogManager.getLogger();
  private final MetricsSystem metricsSystem;
  private final SqlChainStorage chainStorage;
  private final SqlEth1Storage eth1Storage;
  private final SqlProtoArrayStorage protoArrayStorage;
  private final StateStorageMode stateStorageMode;
  private final UInt64 stateStorageFrequency;

  public SqlDatabase(
      final MetricsSystem metricsSystem,
      final SqlChainStorage chainStorage,
      final SqlEth1Storage eth1Storage,
      final SqlProtoArrayStorage protoArrayStorage,
      final StateStorageMode stateStorageMode,
      final UInt64 stateStorageFrequency) {
    this.metricsSystem = metricsSystem;
    this.eth1Storage = eth1Storage;
    this.chainStorage = chainStorage;
    this.protoArrayStorage = protoArrayStorage;
    this.stateStorageMode = stateStorageMode;
    this.stateStorageFrequency = stateStorageFrequency;
  }

  @Override
  public void storeGenesis(final AnchorPoint genesis) {
    // We should only have a single block / state / checkpoint at genesis
    final Checkpoint genesisCheckpoint = genesis.getCheckpoint();
    final BeaconState genesisState = genesis.getState();
    final SignedBeaconBlock genesisBlock = genesis.getBlock();
    try (final SqlChainStorage.Transaction transaction = chainStorage.startTransaction()) {
      transaction.storeCheckpoint(CheckpointType.JUSTIFIED, genesisCheckpoint);
      transaction.storeCheckpoint(CheckpointType.BEST_JUSTIFIED, genesisCheckpoint);
      transaction.storeCheckpoint(FINALIZED, genesisCheckpoint);

      transaction.storeBlock(genesisBlock, true);
      transaction.storeState(genesisBlock.getRoot(), genesisState);
      transaction.commit();
    }
  }

  @Override
  public void update(final StorageUpdate update) {
    if (update.isEmpty()) {
      return;
    }

    try (final SqlChainStorage.Transaction transaction = chainStorage.startTransaction()) {
      final Optional<Checkpoint> previousFinalizedCheckpoint =
          chainStorage.getCheckpoint(FINALIZED);
      updateCheckpoints(update, transaction);

      transaction.storeBlocks(update.getFinalizedBlocks().values(), true);
      transaction.storeBlocks(
          update.getHotBlocksAndStates().values().stream()
              .map(SignedBlockAndState::getBlock)
              .collect(toList()),
          false);
      transaction.finalizeBlocks(update.getFinalizedChildToParentMap().keySet());
      transaction.storeStateRoots(update.getStateRoots());
      update.getDeletedHotBlocks().forEach(transaction::deleteHotBlockByBlockRoot);
      transaction.storeVotes(update.getVotes());

      // Store the periodic hot states (likely to be higher frequency than finalized states)
      update.getHotStates().forEach(transaction::storeState);
      //      System.out.println("Deleted hot blocks: " + update.getDeletedHotBlocks().size());
      updateFinalizedStates(update, transaction, previousFinalizedCheckpoint);

      // Ensure the latest finalized block and state is always stored
      update
          .getLatestFinalizedBlockAndState()
          .ifPresent(
              blockAndState ->
                  transaction.storeState(blockAndState.getRoot(), blockAndState.getState()));
      transaction.commit();
    }
  }

  private void updateFinalizedStates(
      final StorageUpdate update,
      final Transaction transaction,
      final Optional<Checkpoint> previousFinalizedCheckpoint) {

    if (stateStorageMode == StateStorageMode.PRUNE) {
      transaction.pruneFinalizedStates();
    } else {
      if (stateStorageFrequency.isLessThan(UInt64.valueOf(Constants.SLOTS_PER_EPOCH))) {
        // If we need states more often than once per epoch, we must store every hot state
        // so we have the ones we need available
        update
            .getHotBlocksAndStates()
            .forEach(
                (blockRoot, blockAndState) ->
                    transaction.storeState(blockRoot, blockAndState.getState()));
        update.getFinalizedStates().forEach(transaction::storeState);
      }
      update
          .getFinalizedCheckpoint()
          .ifPresent(
              newFinalizedCheckpoint ->
                  trimFinalizedStates(
                      transaction, previousFinalizedCheckpoint, newFinalizedCheckpoint));
    }
  }

  private void trimFinalizedStates(
      final Transaction transaction,
      final Optional<Checkpoint> previousFinalizedCheckpoint,
      final Checkpoint newFinalizedCheckpoint) {
    final UInt64 previousFinalizedSlot =
        previousFinalizedCheckpoint.map(Checkpoint::getEpochStartSlot).orElse(UInt64.ZERO);
    final UInt64 newFinalizedSlot = newFinalizedCheckpoint.getEpochStartSlot();
    final UInt64 trailingPruneDistance = stateStorageFrequency.plus(Constants.SLOTS_PER_EPOCH);
    final UInt64 trimStartSlot =
        previousFinalizedSlot.isGreaterThan(trailingPruneDistance)
            ? previousFinalizedSlot.minus(trailingPruneDistance)
            : UInt64.ZERO;
    LOG.trace(
        "Trimming between {} and {} with storage frequency {}",
        trimStartSlot,
        newFinalizedSlot,
        stateStorageFrequency);
    transaction.trimFinalizedStates(trimStartSlot, newFinalizedSlot, stateStorageFrequency);
  }

  private void updateCheckpoints(
      final StorageUpdate update, final SqlChainStorage.Transaction transaction) {
    update
        .getFinalizedCheckpoint()
        .ifPresent(checkpoint -> transaction.storeCheckpoint(FINALIZED, checkpoint));
    update
        .getJustifiedCheckpoint()
        .ifPresent(checkpoint -> transaction.storeCheckpoint(CheckpointType.JUSTIFIED, checkpoint));
    update
        .getBestJustifiedCheckpoint()
        .ifPresent(
            checkpoint -> transaction.storeCheckpoint(CheckpointType.BEST_JUSTIFIED, checkpoint));
  }

  @Override
  public void updateWeakSubjectivityState(final WeakSubjectivityUpdate weakSubjectivityUpdate) {
    try (final SqlChainStorage.Transaction updater = chainStorage.startTransaction()) {
      weakSubjectivityUpdate
          .getWeakSubjectivityCheckpoint()
          .ifPresentOrElse(
              checkpoint -> updater.storeCheckpoint(CheckpointType.WEAK_SUBJECTIVITY, checkpoint),
              () -> updater.clearCheckpoint(CheckpointType.WEAK_SUBJECTIVITY));
      updater.commit();
    }
  }

  @Override
  public Optional<StoreBuilder> createMemoryStore() {
    final Optional<Checkpoint> maybeFinalizedCheckpoint = chainStorage.getCheckpoint(FINALIZED);
    if (maybeFinalizedCheckpoint.isEmpty()) {
      return Optional.empty();
    }
    final Checkpoint justifiedCheckpoint = chainStorage.getCheckpoint(JUSTIFIED).orElseThrow();
    final Checkpoint bestJustifiedCheckpoint =
        chainStorage.getCheckpoint(BEST_JUSTIFIED).orElseThrow();
    final Checkpoint finalizedCheckpoint = maybeFinalizedCheckpoint.get();
    final Bytes32 finalizedBlockRoot = finalizedCheckpoint.getRoot();
    final BeaconState finalizedState =
        chainStorage.getStateByBlockRoot(finalizedBlockRoot).orElseThrow();
    final SignedBeaconBlock finalizedBlock =
        chainStorage.getBlockByBlockRoot(finalizedBlockRoot).orElseThrow();

    final Map<Bytes32, Bytes32> childToParentLookup = chainStorage.getHotBlockChildToParentLookup();
    childToParentLookup.put(finalizedBlock.getRoot(), finalizedBlock.getParent_root());

    final Map<Bytes32, UInt64> rootToSlotMap = chainStorage.getBlockRootToSlotLookup();
    rootToSlotMap.put(finalizedBlock.getRoot(), finalizedBlock.getSlot());

    final Map<UInt64, VoteTracker> votes = chainStorage.getVotes();

    return Optional.of(
        StoreBuilder.create()
            .metricsSystem(metricsSystem)
            .time(UInt64.valueOf(Instant.now().getEpochSecond()))
            .genesisTime(finalizedState.getGenesis_time())
            .finalizedCheckpoint(finalizedCheckpoint)
            .justifiedCheckpoint(justifiedCheckpoint)
            .bestJustifiedCheckpoint(bestJustifiedCheckpoint)
            .childToParentMap(childToParentLookup)
            .rootToSlotMap(rootToSlotMap)
            .latestFinalized(new SignedBlockAndState(finalizedBlock, finalizedState))
            .votes(votes));
  }

  @Override
  public Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    return chainStorage.getCheckpoint(WEAK_SUBJECTIVITY);
  }

  @Override
  public Map<UInt64, VoteTracker> getVotes() {
    return chainStorage.getVotes();
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return chainStorage.getBlockByBlockRoot(blockRoot).map(SignedBeaconBlock::getSlot);
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    return chainStorage.getSlotAndBlockRootByStateRoot(stateRoot).map(SlotAndBlockRoot::getSlot);
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {
    return chainStorage.getFinalizedBlockBySlot(slot);
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return chainStorage.getLatestFinalizedBlockAtSlot(slot);
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return chainStorage.getBlockByBlockRoot(root);
  }

  @Override
  public Optional<BeaconState> getHotState(final Bytes32 root) {
    return chainStorage.getHotStateByBlockRoot(root);
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots) {
    return blockRoots.stream()
        .flatMap(blockRoot -> getHotBlock(blockRoot).stream())
        .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 blockRoot) {
    return chainStorage.getBlockByBlockRoot(blockRoot, true);
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return chainStorage.streamFinalizedBlocks(startSlot, endSlot);
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return chainStorage.getSlotAndBlockRootByStateRoot(stateRoot);
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return chainStorage.getLatestAvailableFinalizedState(maxSlot);
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return eth1Storage.getMinGenesisTimeBlock();
  }

  @Override
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return eth1Storage.streamDepositsFromBlocks();
  }

  @Override
  public Optional<ProtoArraySnapshot> getProtoArraySnapshot() {
    return protoArrayStorage.loadProtoArraySnapshot();
  }

  @Override
  public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
    try (final SqlEth1Storage.Transaction updater = eth1Storage.startTransaction()) {
      updater.addMinGenesisTimeBlock(event);
      updater.commit();
    }
  }

  @Override
  public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
    try (final SqlEth1Storage.Transaction updater = eth1Storage.startTransaction()) {
      updater.addDepositsFromBlockEvent(event);
      updater.commit();
    }
  }

  @Override
  public void putProtoArraySnapshot(final ProtoArraySnapshot protoArray) {
    try (final SqlProtoArrayStorage.Transaction transaction =
        protoArrayStorage.startTransaction()) {
      transaction.storeProtoArraySnapshot(protoArray);
      transaction.commit();
    }
  }

  @Override
  public void close() {
    eth1Storage.close();
  }
}
