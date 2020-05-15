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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.storage.InMemoryStorageSystem;
import tech.pegasys.teku.util.async.SafeFuture;

public abstract class AbstractCombinedChainDataClientTest {

  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(2);

  protected InMemoryStorageSystem storageSystem;
  protected ChainUpdater chainUpdater;
  protected CombinedChainDataClient client;

  @BeforeEach
  public void setup() {
    storageSystem = createStorageSystem();
    chainUpdater =
        new ChainUpdater(storageSystem.recentChainData(), ChainBuilder.create(VALIDATOR_KEYS));
    client = storageSystem.combinedChainDataClient();
  }

  protected abstract InMemoryStorageSystem createStorageSystem();

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void getStateAtSlot_preGenesis(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final UnsignedLong querySlot = UnsignedLong.ZERO;
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, Optional.empty());

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void getStateAtSlot_atGenesis_genesisSlot(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final SignedBlockAndState genesis = chainUpdater.initializeGenesis();
    final UnsignedLong querySlot = genesis.getSlot();

    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, Optional.of(genesis));

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void getStateAtSlot_atGenesis_postGenesisSlot(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final SignedBlockAndState genesis = chainUpdater.initializeGenesis();
    final UnsignedLong querySlot = genesis.getSlot().plus(UnsignedLong.ONE);

    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, Optional.of(genesis));

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void getStateAtSlot_shouldRetrieveLatestFinalizedState(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final UnsignedLong finalizedEpoch = UnsignedLong.valueOf(2);
    final UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    // Setup chain with finalized block
    chainUpdater.initializeGenesis();
    final SignedBlockAndState blockAtEpoch = chainUpdater.advanceChain(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainUpdater.finalizeEpoch(finalizedEpoch);
    chainUpdater.addNewBestBlock();

    // Sanity check
    assertThat(blockAtEpoch).isEqualTo(finalizedBlock);

    final UnsignedLong querySlot = finalizedSlot;
    final Optional<SignedBlockAndState> effectiveBlockAtSlot = Optional.of(blockAtEpoch);
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, effectiveBlockAtSlot);

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void getStateAtSlot_shouldRetrieveHeadState(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final UnsignedLong finalizedEpoch = UnsignedLong.valueOf(2);
    final UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    chainUpdater.initializeGenesis();
    chainUpdater.advanceChain(finalizedSlot);
    chainUpdater.finalizeEpoch(finalizedEpoch);
    final SignedBlockAndState bestBlock = chainUpdater.addNewBestBlock();

    final UnsignedLong querySlot = bestBlock.getSlot();
    final Optional<SignedBlockAndState> effectiveBlockAtSlot = Optional.of(bestBlock);
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, effectiveBlockAtSlot);

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void getStateAtSlot_shouldRetrieveRecentState(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final UnsignedLong finalizedEpoch = UnsignedLong.valueOf(2);
    final UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    chainUpdater.initializeGenesis();
    chainUpdater.advanceChain(finalizedSlot);
    chainUpdater.finalizeEpoch(finalizedEpoch);
    final SignedBlockAndState recentBlock = chainUpdater.advanceChain();
    final SignedBlockAndState bestBlock = chainUpdater.addNewBestBlock();
    // Sanity check
    assertThat(recentBlock.getSlot()).isLessThan(bestBlock.getSlot());

    final UnsignedLong querySlot = recentBlock.getSlot();
    final Optional<SignedBlockAndState> effectiveBlockAtSlot = Optional.of(recentBlock);
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, effectiveBlockAtSlot);

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void getStateAtSlot_shouldRetrieveRecentStateInEffectAtSkippedSlot(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final UnsignedLong finalizedEpoch = UnsignedLong.valueOf(2);
    final UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    chainUpdater.initializeGenesis();
    chainUpdater.advanceChain(finalizedSlot);
    chainUpdater.finalizeEpoch(finalizedEpoch);
    final SignedBlockAndState recentBlock = chainUpdater.advanceChain();
    final UnsignedLong skippedSlot = recentBlock.getSlot().plus(UnsignedLong.ONE);
    final SignedBlockAndState bestBlock =
        chainUpdater.advanceChain(skippedSlot.plus(UnsignedLong.ONE));
    chainUpdater.updateBestBlock(bestBlock);

    final UnsignedLong querySlot = skippedSlot;
    final Optional<SignedBlockAndState> effectiveBlockAtSlot = Optional.of(recentBlock);
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, effectiveBlockAtSlot);

    assertThat(result).isCompletedWithValue(expected);
  }

  public static Stream<Arguments> getQueryBySlotParameters() {
    return Stream.of(
        Arguments.of("getLatestStateAtSlot", new GetLatestStateAtSlotTestCase()),
        Arguments.of("getBlockAtSlot", new GetBlockAtSlotExactTestCase()),
        Arguments.of(
            "getBlockAndStateInEffectAtSlot", new GetBlockAndStateInEffectAtSlotTestCase()));
  }

  public static Stream<Arguments> getStateBySlotParameters() {
    return Stream.of(
        Arguments.of("getLatestStateAtSlot", new GetLatestStateAtSlotTestCase()),
        Arguments.of(
            "getBlockAndStateInEffectAtSlot", new GetBlockAndStateInEffectAtSlotTestCase()));
  }

  protected interface QueryBySlotTestCase<TResult> {
    SafeFuture<Optional<TResult>> executeQueryBySlot(
        final CombinedChainDataClient client, final UnsignedLong slot);

    Optional<TResult> mapEffectiveBlockAtSlotToExpectedResult(
        final UnsignedLong slot, Optional<SignedBlockAndState> effectiveBlockAtSlot);
  }

  private static class GetLatestStateAtSlotTestCase implements QueryBySlotTestCase<BeaconState> {

    @Override
    public SafeFuture<Optional<BeaconState>> executeQueryBySlot(
        final CombinedChainDataClient client, final UnsignedLong slot) {
      return client.getLatestStateAtSlot(slot);
    }

    @Override
    public Optional<BeaconState> mapEffectiveBlockAtSlotToExpectedResult(
        final UnsignedLong slot, final Optional<SignedBlockAndState> effectiveBlockAtSlot) {
      return effectiveBlockAtSlot.map(SignedBlockAndState::getState);
    }
  }

  private static class GetBlockAtSlotExactTestCase
      implements QueryBySlotTestCase<SignedBeaconBlock> {
    @Override
    public SafeFuture<Optional<SignedBeaconBlock>> executeQueryBySlot(
        final CombinedChainDataClient client, final UnsignedLong slot) {
      return client.getBlockAtSlotExact(slot);
    }

    @Override
    public Optional<SignedBeaconBlock> mapEffectiveBlockAtSlotToExpectedResult(
        final UnsignedLong slot, final Optional<SignedBlockAndState> effectiveBlockAtSlot) {
      return effectiveBlockAtSlot
          .filter(b -> b.getSlot().equals(slot))
          .map(SignedBlockAndState::getBlock);
    }
  }

  private static class GetBlockAndStateInEffectAtSlotTestCase
      implements QueryBySlotTestCase<BeaconBlockAndState> {
    @Override
    public SafeFuture<Optional<BeaconBlockAndState>> executeQueryBySlot(
        final CombinedChainDataClient client, final UnsignedLong slot) {
      return client.getBlockAndStateInEffectAtSlot(slot);
    }

    @Override
    public Optional<BeaconBlockAndState> mapEffectiveBlockAtSlotToExpectedResult(
        final UnsignedLong slot, final Optional<SignedBlockAndState> effectiveBlockAtSlot) {
      return effectiveBlockAtSlot.map(SignedBlockAndState::toUnsigned);
    }
  }
}
