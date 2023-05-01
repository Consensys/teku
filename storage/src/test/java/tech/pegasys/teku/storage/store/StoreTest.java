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

package tech.pegasys.teku.storage.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.millisToSeconds;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.dataproviders.lookup.BlobSidecarsProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannel;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannelWithDelays;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

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
                    spec,
                    blockProviderFromChainBuilder(),
                    StateAndBlockSummaryProvider.NOOP,
                    BlobSidecarsProvider.NOOP,
                    Optional.empty(),
                    genesisTime.minus(1),
                    genesisTime,
                    AnchorPoint.create(spec, genesisCheckpoint, genesis),
                    Optional.empty(),
                    genesisCheckpoint,
                    genesisCheckpoint,
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    StoreConfig.createDefault()))
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
    assertThat(checkpointState.getSlot()).isEqualTo(checkpoint.getEpochStartSlot(spec));
    assertThat(checkpointState.getLatestBlockHeader().hashTreeRoot())
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
    assertThat(result.getSlot()).isEqualTo(checkpoint.getEpochStartSlot(spec));
    assertThat(result.getLatestBlockHeader().hashTreeRoot()).isEqualTo(checkpoint.getRoot());
  }

  @Test
  public void retrieveCheckpointState_invalidState() {
    final UpdatableStore store = createGenesisStore();

    final UInt64 epoch = UInt64.valueOf(2);
    final UInt64 startSlot = spec.computeStartSlotAtEpoch(epoch);
    final SignedBlockAndState futureBlockAndState = chainBuilder.generateBlockAtSlot(startSlot);

    final Checkpoint checkpoint = new Checkpoint(UInt64.ONE, futureBlockAndState.getRoot());

    assertThatSafeFuture(store.retrieveCheckpointState(checkpoint, futureBlockAndState.getState()))
        .isCompletedExceptionallyWith(InvalidCheckpointException.class);
  }

  @Test
  public void retrieveFinalizedCheckpointAndState() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState finalizedBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.slotsPerEpoch(UInt64.ZERO) - 1);
    final Checkpoint finalizedCheckpoint =
        new Checkpoint(UInt64.ONE, finalizedBlockAndState.getRoot());

    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    tx.putBlockAndState(
        finalizedBlockAndState, spec.calculateBlockCheckpoints(finalizedBlockAndState.getState()));
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
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
    final UInt64 epoch = UInt64.valueOf(2);
    final UInt64 startSlot = spec.computeStartSlotAtEpoch(epoch);
    final Bytes32 futureRoot = chainBuilder.generateBlockAtSlot(startSlot).getRoot();

    // Add blocks
    final StoreTransaction tx = store.startTransaction(new StubStorageUpdateChannel());
    chainBuilder
        .streamBlocksAndStates()
        .forEach(
            blockAndState ->
                tx.putBlockAndState(
                    blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState())));
    tx.commit().join();

    final Checkpoint checkpoint = new Checkpoint(UInt64.ONE, futureRoot);
    final SafeFuture<Optional<BeaconState>> result = store.retrieveCheckpointState(checkpoint);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasCauseInstanceOf(InvalidCheckpointException.class);
  }

  private void testApplyChangesWhenTransactionCommits(final boolean withInterleavedTransaction) {
    final UpdatableStore store = createGenesisStore();
    final UInt64 epoch3 = UInt64.valueOf(4);
    final UInt64 epoch3Slot = spec.computeStartSlotAtEpoch(epoch3);
    chainBuilder.generateBlocksUpToSlot(epoch3Slot);

    final Checkpoint genesisCheckpoint = store.getFinalizedCheckpoint();
    final UInt64 initialTimeMillis = store.getTimeMillis();
    final UInt64 genesisTime = store.getGenesisTime();

    final Checkpoint checkpoint1 = chainBuilder.getCurrentCheckpointForEpoch(UInt64.valueOf(1));
    final Checkpoint checkpoint2 = chainBuilder.getCurrentCheckpointForEpoch(UInt64.valueOf(2));
    final Checkpoint checkpoint3 = chainBuilder.getCurrentCheckpointForEpoch(UInt64.valueOf(3));

    // Start transaction
    final StubStorageUpdateChannelWithDelays updateChannel =
        new StubStorageUpdateChannelWithDelays();
    final StoreTransaction tx = store.startTransaction(updateChannel);
    // Add blocks
    chainBuilder
        .streamBlocksAndStates()
        .forEach(
            blockAndState ->
                tx.putBlockAndState(
                    blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState())));
    // Update checkpoints
    tx.setFinalizedCheckpoint(checkpoint1, false);
    tx.setJustifiedCheckpoint(checkpoint2);
    tx.setBestJustifiedCheckpoint(checkpoint3);
    // Update time
    UInt64 firstUpdateTimeMillis = initialTimeMillis.plus(1300);
    tx.setTimeMillis(firstUpdateTimeMillis);
    UInt64 updatedGenesisTime = genesisTime.plus(UInt64.ONE);
    tx.setGenesisTime(updatedGenesisTime);

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
    assertThat(store.getTimeSeconds()).isEqualTo(millisToSeconds(initialTimeMillis));
    assertThat(store.getTimeMillis()).isEqualTo(initialTimeMillis);
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
    assertThat(tx.getTimeSeconds()).isEqualTo(millisToSeconds(firstUpdateTimeMillis));
    assertThat(tx.getTimeMillis()).isEqualTo(firstUpdateTimeMillis);
    assertThat(tx.getGenesisTime()).isEqualTo(updatedGenesisTime);

    // Commit transaction
    final SafeFuture<Void> txResult = tx.commit();

    final UInt64 expectedTimeMillis;
    final SafeFuture<Void> txResult2;
    if (withInterleavedTransaction) {
      expectedTimeMillis = firstUpdateTimeMillis.plus(1500);
      StoreTransaction tx2 = store.startTransaction(updateChannel);
      tx2.setTimeMillis(expectedTimeMillis);
      txResult2 = tx2.commit();
    } else {
      expectedTimeMillis = firstUpdateTimeMillis;
      txResult2 = SafeFuture.COMPLETE;
    }

    // Complete transactions
    assertThat(updateChannel.getAsyncRunner().countDelayedActions()).isLessThanOrEqualTo(2);
    updateChannel.getAsyncRunner().executeUntilDone();
    assertThat(txResult).isCompleted();
    assertThat(txResult2).isCompleted();

    // Check store is updated
    chainBuilder
        .streamBlocksAndStates(checkpoint3.getEpochStartSlot(spec), chainBuilder.getLatestSlot())
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
    assertThat(store.getTimeMillis()).isEqualTo(expectedTimeMillis);
    assertThat(store.getTimeSeconds()).isEqualTo(millisToSeconds(expectedTimeMillis));
    assertThat(store.getGenesisTime()).isEqualTo(updatedGenesisTime);

    // Check store was pruned as expected
    final List<Bytes32> expectedBlockRoots =
        chainBuilder
            .streamBlocksAndStates(checkpoint1.getEpochStartSlot(spec))
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toList());
    assertThat(store.getOrderedBlockRoots()).containsExactlyElementsOf(expectedBlockRoots);
  }
}
