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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.stategenerator.CheckpointStateGenerator;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.util.config.StateStorageMode;

public abstract class AbstractCombinedChainDataClientTest {

  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(2);

  protected StorageSystem storageSystem;
  protected ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);
  protected ChainUpdater chainUpdater;
  protected CombinedChainDataClient client;

  @BeforeEach
  public void setup() {
    storageSystem = createStorageSystem();
    chainUpdater = new ChainUpdater(storageSystem.recentChainData(), chainBuilder);
    client = storageSystem.combinedChainDataClient();
  }

  protected abstract StateStorageMode getStorageMode();

  protected StorageSystem createStorageSystem() {
    return InMemoryStorageSystemBuilder.buildDefault(getStorageMode());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void queryBySlot_preGenesis(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final UInt64 querySlot = UInt64.ZERO;
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, Optional.empty());

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void queryBySlot_preForkChoice(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    // Initialize genesis and build small chain with finalization
    chainUpdater.initializeGenesis();
    final UInt64 historicalSlot = chainUpdater.advanceChain().getSlot();
    final UInt64 finalizedSlot = UInt64.valueOf(10);
    chainUpdater.advanceChain(finalizedSlot);
    final UInt64 finalizedEpoch = compute_epoch_at_slot(finalizedSlot).plus(UInt64.ONE);
    final UInt64 recentSlot = compute_start_slot_at_epoch(finalizedEpoch).plus(UInt64.ONE);
    chainUpdater.finalizeEpoch(finalizedEpoch);
    // Add some recent blocks
    chainUpdater.advanceChain(recentSlot);
    chainUpdater.advanceChain();

    // Restart
    final StorageSystem restarted = storageSystem.restarted(getStorageMode());
    final CombinedChainDataClient client = restarted.combinedChainDataClient();
    // We should now have an initialized store, but no chosen chainhead
    assertThat(restarted.recentChainData().getStore()).isNotNull();
    assertThat(restarted.recentChainData().getBestBlockRoot()).isEmpty();

    // Check recent slot
    final UInt64 querySlot = recentSlot;
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, Optional.empty());
    assertThat(result).isCompletedWithValue(expected);

    // Check historical slot
    final UInt64 querySlot2 = historicalSlot;
    final SafeFuture<Optional<T>> result2 = testCase.executeQueryBySlot(client, querySlot2);
    final Optional<T> expected2 =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot2, Optional.empty());
    assertThat(result2).isCompletedWithValue(expected2);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void queryBySlot_atGenesis_genesisSlot(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final SignedBlockAndState genesis = chainUpdater.initializeGenesis();
    final UInt64 querySlot = genesis.getSlot();

    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, Optional.of(genesis));

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void queryBySlot_atGenesis_postGenesisSlot(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final SignedBlockAndState genesis = chainUpdater.initializeGenesis();
    final UInt64 querySlot = genesis.getSlot().plus(UInt64.ONE);

    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, Optional.of(genesis));

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void queryBySlot_shouldRetrieveLatestFinalizedState(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final UInt64 finalizedEpoch = UInt64.valueOf(2);
    final UInt64 finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    // Setup chain with finalized block
    chainUpdater.initializeGenesis();
    final SignedBlockAndState blockAtEpoch = chainUpdater.advanceChain(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainUpdater.finalizeEpoch(finalizedEpoch);
    chainUpdater.addNewBestBlock();

    // Sanity check
    assertThat(blockAtEpoch).isEqualTo(finalizedBlock);

    final UInt64 querySlot = finalizedSlot;
    final Optional<SignedBlockAndState> effectiveBlockAtSlot = Optional.of(blockAtEpoch);
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, effectiveBlockAtSlot);

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void queryBySlot_shouldRetrieveHeadState(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final SignedBlockAndState bestBlock = advanceChainAndGetBestBlockAndState(2);

    final UInt64 querySlot = bestBlock.getSlot();
    final Optional<SignedBlockAndState> effectiveBlockAtSlot = Optional.of(bestBlock);
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, effectiveBlockAtSlot);

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void queryBySlot_shouldRetrieveHeadStateWhenNewerSlotQueried(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final SignedBlockAndState bestBlock = advanceChainAndGetBestBlockAndState(2);

    final UInt64 querySlot = bestBlock.getSlot().plus(2);
    final Optional<SignedBlockAndState> effectiveBlockAtSlot = Optional.of(bestBlock);
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, effectiveBlockAtSlot);

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void queryBySlot_shouldRetrieveRecentState(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final SignedBlockAndState recentBlock = advanceChainAndGetBestBlockAndState(2);
    final SignedBlockAndState bestBlock = chainUpdater.addNewBestBlock();
    // Sanity check
    assertThat(recentBlock.getSlot()).isLessThan(bestBlock.getSlot());

    final UInt64 querySlot = recentBlock.getSlot();
    final Optional<SignedBlockAndState> effectiveBlockAtSlot = Optional.of(recentBlock);
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, effectiveBlockAtSlot);

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void queryBySlot_shouldRetrieveRecentStateInEffectAtSkippedSlot(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final SignedBlockAndState recentBlock = advanceChainAndGetBestBlockAndState(2);
    final UInt64 skippedSlot = recentBlock.getSlot().plus(UInt64.ONE);
    final SignedBlockAndState bestBlock = chainUpdater.advanceChain(skippedSlot.plus(UInt64.ONE));
    chainUpdater.updateBestBlock(bestBlock);

    final UInt64 querySlot = skippedSlot;
    final Optional<SignedBlockAndState> effectiveBlockAtSlot = Optional.of(recentBlock);
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, effectiveBlockAtSlot);

    assertThat(result).isCompletedWithValue(expected);
  }

  @Test
  public void getBlockAndStateInEffectAtSlot_withBlockAndStateAvailable() throws Exception {
    chainUpdater.initializeGenesis();

    final SignedBlockAndState targetBlock = chainBuilder.generateNextBlock();
    chainUpdater.saveBlock(targetBlock);

    final SignedBlockAndState bestBlock = chainUpdater.addNewBestBlock();
    // Sanity check
    assertThat(bestBlock.getSlot()).isGreaterThan(targetBlock.getSlot());

    final SafeFuture<Optional<BeaconBlockAndState>> result =
        client.getBlockAndStateInEffectAtSlot(targetBlock.getSlot());
    assertThat(result).isCompletedWithValue(Optional.of(targetBlock.toUnsigned()));
  }

  @Test
  public void getBlockAtSlotExact_unknownRoot() {
    final SignedBlockAndState genesis = chainUpdater.initializeGenesis();
    final UInt64 querySlot = genesis.getSlot().plus(UInt64.ONE);

    final SafeFuture<Optional<SignedBeaconBlock>> result =
        client.getBlockAtSlotExact(querySlot, Bytes32.ZERO);
    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getStateByStateRoot_shouldReturnState()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState bestBlockAndState = advanceChainAndGetBestBlockAndState(2);
    Optional<BeaconState> result =
        client.getStateByStateRoot(bestBlockAndState.getState().hash_tree_root()).get();
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(bestBlockAndState.getState());
  }

  @Test
  public void getCheckpointStateAtEpoch_recentEpochWithSkippedBoundarySlot() {
    final UInt64 epoch = UInt64.valueOf(3);
    final UInt64 epochSlot = compute_start_slot_at_epoch(epoch);
    final UInt64 nextEpoch = epoch.plus(UInt64.ONE);

    chainUpdater.initializeGenesis();
    // Setup chain at epoch to be queried
    final SignedBlockAndState checkpointBlockAndState =
        chainUpdater.advanceChain(epochSlot.minus(2));
    // Bury queried epoch behind additional blocks
    chainUpdater.advanceChain(compute_start_slot_at_epoch(nextEpoch));
    chainUpdater.addNewBestBlock();

    final Checkpoint checkpoint = new Checkpoint(epoch, checkpointBlockAndState.getRoot());
    final CheckpointState expected =
        CheckpointStateGenerator.generate(checkpoint, checkpointBlockAndState);

    final SafeFuture<Optional<CheckpointState>> actual = client.getCheckpointStateAtEpoch(epoch);
    assertThat(actual).isCompletedWithValue(Optional.of(expected));
  }

  @Test
  public void getCheckpointStateAtEpoch_recentEpoch() {
    final UInt64 epoch = UInt64.valueOf(3);
    final UInt64 epochSlot = compute_start_slot_at_epoch(epoch);
    final UInt64 nextEpoch = epoch.plus(UInt64.ONE);

    chainUpdater.initializeGenesis();
    // Setup chain at epoch to be queried
    final SignedBlockAndState checkpointBlockAndState = chainUpdater.advanceChain(epochSlot);
    // Bury queried epoch behind additional blocks
    chainUpdater.advanceChain(compute_start_slot_at_epoch(nextEpoch));
    chainUpdater.addNewBestBlock();

    final Checkpoint checkpoint = new Checkpoint(epoch, checkpointBlockAndState.getRoot());
    final CheckpointState expected =
        CheckpointStateGenerator.generate(checkpoint, checkpointBlockAndState);

    final SafeFuture<Optional<CheckpointState>> actual = client.getCheckpointStateAtEpoch(epoch);
    assertThat(actual).isCompletedWithValue(Optional.of(expected));
  }

  public static Stream<Arguments> getQueryBySlotParameters() {
    return Stream.of(
        Arguments.of("getLatestStateAtSlot", new GetLatestStateAtSlotTestCase()),
        Arguments.of("getBlockAtSlotExact", new GetBlockAtSlotExactTestCase()),
        Arguments.of("getBlockInEffectAtSlotTestCase", new GetBlockInEffectAtSlotTestCase()),
        Arguments.of(
            "getBlockAndStateInEffectAtSlot", new GetBlockAndStateInEffectAtSlotTestCase()));
  }

  public static Stream<Arguments> getStateBySlotParameters() {
    return Stream.of(
        Arguments.of("getLatestStateAtSlot", new GetLatestStateAtSlotTestCase()),
        Arguments.of(
            "getBlockAndStateInEffectAtSlot", new GetBlockAndStateInEffectAtSlotTestCase()));
  }

  protected SignedBlockAndState advanceChainAndGetBestBlockAndState(final long epoch) {
    final UInt64 finalizedEpoch = UInt64.valueOf(epoch);
    final UInt64 finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    chainUpdater.initializeGenesis();
    chainUpdater.advanceChain(finalizedSlot);
    chainUpdater.finalizeEpoch(finalizedEpoch);
    return chainUpdater.addNewBestBlock();
  }

  protected interface QueryBySlotTestCase<TResult> {
    SafeFuture<Optional<TResult>> executeQueryBySlot(
        final CombinedChainDataClient client, final UInt64 slot);

    Optional<TResult> mapEffectiveBlockAtSlotToExpectedResult(
        final UInt64 slot, Optional<SignedBlockAndState> effectiveBlockAtSlot);
  }

  private static class GetLatestStateAtSlotTestCase implements QueryBySlotTestCase<BeaconState> {

    @Override
    public SafeFuture<Optional<BeaconState>> executeQueryBySlot(
        final CombinedChainDataClient client, final UInt64 slot) {
      return client.getLatestStateAtSlot(slot);
    }

    @Override
    public Optional<BeaconState> mapEffectiveBlockAtSlotToExpectedResult(
        final UInt64 slot, final Optional<SignedBlockAndState> effectiveBlockAtSlot) {
      return effectiveBlockAtSlot.map(SignedBlockAndState::getState);
    }
  }

  private static class GetBlockAtSlotExactTestCase
      implements QueryBySlotTestCase<SignedBeaconBlock> {
    @Override
    public SafeFuture<Optional<SignedBeaconBlock>> executeQueryBySlot(
        final CombinedChainDataClient client, final UInt64 slot) {
      return client.getBlockAtSlotExact(slot);
    }

    @Override
    public Optional<SignedBeaconBlock> mapEffectiveBlockAtSlotToExpectedResult(
        final UInt64 slot, final Optional<SignedBlockAndState> effectiveBlockAtSlot) {
      return effectiveBlockAtSlot
          .filter(b -> b.getSlot().equals(slot))
          .map(SignedBlockAndState::getBlock);
    }
  }

  private static class GetBlockInEffectAtSlotTestCase
      implements QueryBySlotTestCase<SignedBeaconBlock> {
    @Override
    public SafeFuture<Optional<SignedBeaconBlock>> executeQueryBySlot(
        final CombinedChainDataClient client, final UInt64 slot) {
      return client.getBlockInEffectAtSlot(slot);
    }

    @Override
    public Optional<SignedBeaconBlock> mapEffectiveBlockAtSlotToExpectedResult(
        final UInt64 slot, final Optional<SignedBlockAndState> effectiveBlockAtSlot) {
      return effectiveBlockAtSlot.map(SignedBlockAndState::getBlock);
    }
  }

  private static class GetBlockAndStateInEffectAtSlotTestCase
      implements QueryBySlotTestCase<BeaconBlockAndState> {
    @Override
    public SafeFuture<Optional<BeaconBlockAndState>> executeQueryBySlot(
        final CombinedChainDataClient client, final UInt64 slot) {
      return client.getBlockAndStateInEffectAtSlot(slot);
    }

    @Override
    public Optional<BeaconBlockAndState> mapEffectiveBlockAtSlotToExpectedResult(
        final UInt64 slot, final Optional<SignedBlockAndState> effectiveBlockAtSlot) {
      return effectiveBlockAtSlot.map(SignedBlockAndState::toUnsigned);
    }
  }
}
