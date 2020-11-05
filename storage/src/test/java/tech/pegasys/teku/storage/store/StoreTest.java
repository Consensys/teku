/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.storage.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ProtoArrayStorageChannel;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannel;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannelWithDelays;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.config.Constants;

class StoreTest extends AbstractStoreTest {

  @Test
  public void create_timeLessThanGenesisTime() {
    final UInt64 genesisTime = UInt64.valueOf(100);
    final SignedBlockAndState genesis = chainBuilder.generateGenesis(genesisTime, false);
    final Checkpoint genesisCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(0);

    assertThatThrownBy(
            () ->
                Store.create(
                    SYNC_RUNNER,
                    new StubMetricsSystem(),
                    blockProviderFromChainBuilder(),
                    StateAndBlockSummaryProvider.NOOP,
                    Optional.empty(),
                    genesisTime.minus(1),
                    genesisTime,
                    AnchorPoint.create(genesisCheckpoint, genesis),
                    genesisCheckpoint,
                    genesisCheckpoint,
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    StoreConfig.createDefault(),
                    mock(ProtoArrayStorageChannel.class)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Time must be greater than or equal to genesisTime");
  }

  @Test
  public void retrieveSignedBlock_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          final Bytes32 root = blockAndState.getRoot();
          final SignedBeaconBlock expectedBlock = blockAndState.getBlock();
          SafeFuture<Optional<SignedBeaconBlock>> result = store.retrieveSignedBlock(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .describedAs("block %s", expectedBlock.getSlot())
              .isCompletedWithValue(Optional.of(expectedBlock));
        });
  }

  @Test
  public void retrieveBlock_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          final Bytes32 root = blockAndState.getRoot();
          final BeaconBlock expectedBlock = blockAndState.getBlock().getMessage();
          SafeFuture<Optional<BeaconBlock>> result = store.retrieveBlock(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .describedAs("block %s", expectedBlock.getSlot())
              .isCompletedWithValue(Optional.of(expectedBlock));
        });
  }

  @Test
  public void retrieveBlockAndState_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          final Bytes32 root = blockAndState.getRoot();
          SafeFuture<Optional<SignedBlockAndState>> result = store.retrieveBlockAndState(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .describedAs("block and state at %s", blockAndState.getSlot())
              .isCompletedWithValue(Optional.of(blockAndState));
        });
  }

  @Test
  public void retrieveBlockState_withLimitedCache() {
    processChainWithLimitedCache(
        (store, blockAndState) -> {
          final Bytes32 root = blockAndState.getRoot();
          SafeFuture<Optional<BeaconState>> result = store.retrieveBlockState(root);
          assertThat(result).isCompleted();
          assertThat(result)
              .describedAs("State at %s", blockAndState.getSlot())
              .isCompletedWithValue(Optional.of(blockAndState.getState()));
        });
  }

  @Test
  public void retrieveCheckpointState_withLimitedCache() {
    processCheckpointsWithLimitedCache(
        (store, checkpointState) -> {
          SafeFuture<Optional<BeaconState>> result =
              store.retrieveCheckpointState(checkpointState.getCheckpoint());
          assertThat(result)
              .describedAs("Checkpoint state for checkpoint %s", checkpointState.getCheckpoint())
              .isCompletedWithValue(Optional.of(checkpointState.getState()));
        });
  }

  @Test
  public void shouldApplyChangesWhenTransactionCommits() {
    testApplyChangesWhenTransactionCommits(false);
  }

  @Test
  public void shouldApplyChangesWhenTransactionCommits_withInterleavedTx() {
    testApplyChangesWhenTransactionCommits(true);
  }

  @Test
  public void retrieveCheckpointState_shouldGenerateCheckpointStates() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState genesisBlockAndState = chainBuilder.getLatestBlockAndState();
    final Checkpoint checkpoint = new Checkpoint(UInt64.ONE, genesisBlockAndState.getRoot());

    final SafeFuture<Optional<BeaconState>> result = store.retrieveCheckpointState(checkpoint);
    assertThatSafeFuture(result).isCompletedWithNonEmptyOptional();
    final BeaconState checkpointState = result.join().orElseThrow();
    assertThat(checkpointState.getSlot()).isEqualTo(checkpoint.getEpochStartSlot());
    assertThat(checkpointState.getLatest_block_header().hash_tree_root())
        .isEqualTo(checkpoint.getRoot());
  }

  @Test
  public void retrieveCheckpointState_forGenesis() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState genesisBlockAndState = chainBuilder.getLatestBlockAndState();
    final Checkpoint checkpoint = new Checkpoint(UInt64.ZERO, genesisBlockAndState.getRoot());

    final BeaconState baseState = genesisBlockAndState.getState();
    final SafeFuture<Optional<BeaconState>> result =
        store.retrieveCheckpointState(checkpoint, baseState);
    assertThatSafeFuture(result).isCompletedWithOptionalContaining(baseState);
  }

  @Test
  public void retrieveCheckpointState_forEpochPastGenesis() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState genesisBlockAndState = chainBuilder.getLatestBlockAndState();
    final Checkpoint checkpoint = new Checkpoint(UInt64.ONE, genesisBlockAndState.getRoot());

    final BeaconState baseState = genesisBlockAndState.getState();
    final SafeFuture<Optional<BeaconState>> resultFuture =
        store.retrieveCheckpointState(checkpoint, baseState);
    assertThatSafeFuture(resultFuture).isCompletedWithNonEmptyOptional();
    final BeaconState result = resultFuture.join().orElseThrow();
    assertThat(result.getSlot()).isGreaterThan(baseState.getSlot());
    assertThat(result.getSlot()).isEqualTo(checkpoint.getEpochStartSlot());
    assertThat(result.getLatest_block_header().hash_tree_root()).isEqualTo(checkpoint.getRoot());
  }

  @Test
  public void retrieveCheckpointState_invalidState() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState futureBlockAndState =
        chainBuilder.generateBlockAtSlot(compute_start_slot_at_epoch(UInt64.valueOf(2)));

    final Checkpoint checkpoint = new Checkpoint(UInt64.ONE, futureBlockAndState.getRoot());

    assertThatSafeFuture(store.retrieveCheckpointState(checkpoint, futureBlockAndState.getState()))
        .isCompletedExceptionallyWith(InvalidCheckpointException.class);
  }

  @Test
  public void retrieveFinalizedCheckpointAndState() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState finalizedBlockAndState =
        chainBuilder.generateBlockAtSlot(Constants.SLOTS_PER_EPOCH - 1);
    final Checkpoint finalizedCheckpoint =
        new Checkpoint(UInt64.ONE, finalizedBlockAndState.getRoot());

    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    tx.putBlockAndState(finalizedBlockAndState);
    tx.setFinalizedCheckpoint(finalizedCheckpoint);
    assertThat(tx.commit()).isCompleted();

    final SafeFuture<CheckpointState> result = store.retrieveFinalizedCheckpointAndState();
    assertThat(result).isCompleted();
    assertThat(result.join().getCheckpoint()).isEqualTo(finalizedCheckpoint);
    assertThat(result.join().getRoot()).isEqualTo(finalizedBlockAndState.getRoot());
    assertThat(result.join().getState()).isNotEqualTo(finalizedBlockAndState.getState());
    assertThat(result.join().getState().getSlot())
        .isEqualTo(finalizedBlockAndState.getSlot().plus(1));
  }

  @Test
  public void
      retrieveCheckpointState_shouldThrowInvalidCheckpointExceptionWhenEpochBeforeBlockRoot() {
    final UpdatableStore store = createGenesisStore();
    final Bytes32 futureRoot =
        chainBuilder.generateBlockAtSlot(compute_start_slot_at_epoch(UInt64.valueOf(2))).getRoot();

    // Add blocks
    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    chainBuilder.streamBlocksAndStates().forEach(tx::putBlockAndState);
    tx.commit().join();

    final Checkpoint checkpoint = new Checkpoint(UInt64.ONE, futureRoot);
    final SafeFuture<Optional<BeaconState>> result = store.retrieveCheckpointState(checkpoint);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasCauseInstanceOf(InvalidCheckpointException.class);
  }

  public void testApplyChangesWhenTransactionCommits(final boolean withInterleavedTransaction) {
    final UpdatableStore store = createGenesisStore();
    final UInt64 epoch3Slot = compute_start_slot_at_epoch(UInt64.valueOf(4));
    chainBuilder.generateBlocksUpToSlot(epoch3Slot);

    final Checkpoint genesisCheckpoint = store.getFinalizedCheckpoint();
    final UInt64 initialTime = store.getTime();
    final UInt64 genesisTime = store.getGenesisTime();

    final Checkpoint checkpoint1 = chainBuilder.getCurrentCheckpointForEpoch(UInt64.valueOf(1));
    final Checkpoint checkpoint2 = chainBuilder.getCurrentCheckpointForEpoch(UInt64.valueOf(2));
    final Checkpoint checkpoint3 = chainBuilder.getCurrentCheckpointForEpoch(UInt64.valueOf(3));

    // Start transaction
    final StubStorageUpdateChannelWithDelays updateChannel =
        new StubStorageUpdateChannelWithDelays();
    final StoreTransaction tx = store.startTransaction(updateChannel);
    // Add blocks
    chainBuilder.streamBlocksAndStates().forEach(tx::putBlockAndState);
    // Update checkpoints
    tx.setFinalizedCheckpoint(checkpoint1);
    tx.setJustifiedCheckpoint(checkpoint2);
    tx.setBestJustifiedCheckpoint(checkpoint3);
    // Update time
    tx.setTime(initialTime.plus(UInt64.ONE));
    tx.setGenesis_time(genesisTime.plus(UInt64.ONE));

    // Check that store is not yet updated
    // Check blocks
    chainBuilder
        .streamBlocksAndStates(1, chainBuilder.getLatestSlot().longValue())
        .forEach(b -> assertThat(store.containsBlock(b.getRoot())).isFalse());
    // Check checkpoints
    assertThat(store.getJustifiedCheckpoint()).isEqualTo(genesisCheckpoint);
    assertThat(store.getBestJustifiedCheckpoint()).isEqualTo(genesisCheckpoint);
    assertThat(store.getFinalizedCheckpoint()).isEqualTo(genesisCheckpoint);
    // Check time
    assertThat(store.getTime()).isEqualTo(initialTime);
    assertThat(store.getGenesisTime()).isEqualTo(genesisTime);

    // Check that transaction is updated
    chainBuilder
        .streamBlocksAndStates(1, chainBuilder.getLatestSlot().longValue())
        .forEach(
            b ->
                assertThat(tx.retrieveBlockAndState(b.getRoot()))
                    .isCompletedWithValue(Optional.of(b)));
    // Check checkpoints
    assertThat(tx.getFinalizedCheckpoint()).isEqualTo(checkpoint1);
    assertThat(tx.getJustifiedCheckpoint()).isEqualTo(checkpoint2);
    assertThat(tx.getBestJustifiedCheckpoint()).isEqualTo(checkpoint3);
    // Check time
    assertThat(tx.getTime()).isEqualTo(initialTime.plus(UInt64.ONE));
    assertThat(tx.getGenesisTime()).isEqualTo(genesisTime.plus(UInt64.ONE));

    // Commit transaction
    final SafeFuture<Void> txResult = tx.commit();

    final SafeFuture<Void> txResult2;
    if (withInterleavedTransaction) {
      UInt64 time = store.getTime().plus(UInt64.ONE);
      StoreTransaction tx2 = store.startTransaction(updateChannel);
      tx2.setTime(time);
      txResult2 = tx2.commit();
    } else {
      txResult2 = SafeFuture.COMPLETE;
    }

    // Complete transactions
    assertThat(updateChannel.getAsyncRunner().countDelayedActions()).isLessThanOrEqualTo(2);
    updateChannel.getAsyncRunner().executeUntilDone();
    assertThat(txResult).isCompleted();
    assertThat(txResult2).isCompleted();

    // Check store is updated
    chainBuilder
        .streamBlocksAndStates(checkpoint3.getEpochStartSlot(), chainBuilder.getLatestSlot())
        .forEach(
            b ->
                assertThat(store.retrieveBlockAndState(b.getRoot()))
                    .isCompletedWithValue(Optional.of(b)));
    // Check checkpoints
    assertThat(store.getFinalizedCheckpoint()).isEqualTo(checkpoint1);
    assertThat(store.getJustifiedCheckpoint()).isEqualTo(checkpoint2);
    assertThat(store.getBestJustifiedCheckpoint()).isEqualTo(checkpoint3);
    // Extra checks for finalized checkpoint
    final SafeFuture<CheckpointState> finalizedCheckpointState =
        store.retrieveFinalizedCheckpointAndState();
    assertThat(finalizedCheckpointState).isCompleted();
    assertThat(finalizedCheckpointState.join().getCheckpoint()).isEqualTo(checkpoint1);
    // Check time
    assertThat(store.getTime()).isEqualTo(initialTime.plus(UInt64.ONE));
    assertThat(store.getGenesisTime()).isEqualTo(genesisTime.plus(UInt64.ONE));

    // Check store was pruned as expected
    final List<Bytes32> expectedBlockRoots =
        chainBuilder
            .streamBlocksAndStates(checkpoint1.getEpochStartSlot())
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toList());
    assertThat(store.getOrderedBlockRoots()).containsExactlyElementsOf(expectedBlockRoots);
  }
}
