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

package tech.pegasys.teku.core.stategenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.lookup.StateAndBlockProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.state.BlockRootAndState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class StateRegenerationBaseSelectorTest {

  private static final int REPLAY_TOLERANCE_TO_AVOID_LOADING_IN_EPOCHS = 2;
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @SuppressWarnings("unchecked")
  private final Supplier<Optional<BlockRootAndState>> closestAvailableStateSupplier =
      mock(Supplier.class);

  private final StateAndBlockProvider stateAndBlockProvider = mock(StateAndBlockProvider.class);
  private final BlockProvider blockProvider = mock(BlockProvider.class);

  private Optional<SignedBlockAndState> closestBlockAndStateFromStore = Optional.empty();
  private Optional<SignedBlockAndState> rebasedStartingPoint = Optional.empty();
  private Optional<SignedBlockAndState> latestEpochBoundary = Optional.empty();

  @Test
  void shouldReturnEmptyWhenClosestAvailableStateIsEmpty() {
    // If we can't generate the state from what's in the Store, it's either finalized or invalid so
    // don't bother trying to generate it at all
    withRebasedStartingPointAtSlot(1);
    withLatestEpochBoundaryAtSlot(1);

    assertThatSafeFuture(getBestBase()).isCompletedWithEmptyOptional();
    verifyNoInteractions(stateAndBlockProvider, blockProvider);
  }

  @Test
  void shouldUseStateFromStoreWhenNoOtherOptionAvailable() {
    final SignedBlockAndState storeBlockAndState = withClosestAvailableFromStoreAtSlot(1);

    assertSelectedBase(storeBlockAndState);
  }

  @Test
  void shouldUseStateFromStoreWhenItHasHigherSlotThanOtherOptions() {
    final SignedBlockAndState storeBlockAndState = withClosestAvailableFromStoreAtSlot(100);
    withRebasedStartingPointAtSlot(1);
    withLatestEpochBoundaryAtSlot(50);

    assertSelectedBase(storeBlockAndState);
  }

  @Test
  void shouldUseLatestEpochBoundaryWhenBetterThanOtherOptions() {
    withClosestAvailableFromStoreAtSlot(100);
    withRebasedStartingPointAtSlot(1);
    final SignedBlockAndState latestEpochBoundary = withLatestEpochBoundaryAtSlot(200);

    assertSelectedBase(latestEpochBoundary);
  }

  @Test
  void shouldUseLatestEpochBoundaryWhenBetterThanStoreAndNoRebasedOptionAvailable() {
    withClosestAvailableFromStoreAtSlot(100);
    final SignedBlockAndState latestEpochBoundary = withLatestEpochBoundaryAtSlot(200);

    assertSelectedBase(latestEpochBoundary);
  }

  @Test
  void shouldNotUseLatestEpochBoundaryWhenNotMuchBetterThanStore() {
    final SignedBlockAndState storeState = withClosestAvailableFromStoreAtSlot(100);
    withLatestEpochBoundaryAtSlot(101);

    assertSelectedBase(storeState);
  }

  @Test
  void shouldNotUseLatestEpochBoundaryWhenNotMuchBetterThanRebasedOption() {
    withClosestAvailableFromStoreAtSlot(1);
    final SignedBlockAndState rebasedState = withRebasedStartingPointAtSlot(100);
    withLatestEpochBoundaryAtSlot(101);

    assertSelectedBase(rebasedState);
  }

  @Test
  void shouldUseNextBestOptionWhenLatestEpochBoundaryDoesNotLoad() {
    final SignedBlockAndState fromStore = withClosestAvailableFromStoreAtSlot(100);
    final SignedBlockAndState fromEpochBoundary = withLatestEpochBoundaryAtSlot(101);
    withRebasedStartingPointAtSlot(99);

    final StateRegenerationBaseSelector selector = createSelector();

    // Make the epoch boundary state unavailable
    when(stateAndBlockProvider.getBlockAndState(fromEpochBoundary.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    assertThatSafeFuture(selector.getBestBase()).isCompletedWithValue(Optional.of(fromStore));
  }

  @Test
  void shouldUseRebasedStartingPointWhenItIsTheBestOption() {
    final SignedBlockAndState rebasedStartingPoint = withRebasedStartingPointAtSlot(150);
    withClosestAvailableFromStoreAtSlot(100);
    withLatestEpochBoundaryAtSlot(120);

    assertSelectedBase(rebasedStartingPoint);
  }

  @Test
  void shouldNotStoreRebasedStateThatIsWorseThanTheCurrentOne() {
    withRebasedStartingPointAtSlot(10);
    final StateRegenerationBaseSelector selector = createSelector();

    final SignedBlockAndState worseStartingPoint =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(9));
    assertThat(selector.withRebasedStartingPoint(worseStartingPoint)).isSameAs(selector);
  }

  @Test
  void shouldNotStoreRebasedStateThatIsWorseThanLatestEpochBoundary() {
    withRebasedStartingPointAtSlot(10);
    withLatestEpochBoundaryAtSlot(15);
    final StateRegenerationBaseSelector selector = createSelector();

    final SignedBlockAndState worseStartingPoint =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(12));
    assertThat(selector.withRebasedStartingPoint(worseStartingPoint)).isSameAs(selector);
  }

  @Test
  void shouldStoreRebasedStateThatIsBetterThanTheCurrentOne() {
    withClosestAvailableFromStoreAtSlot(5);
    withRebasedStartingPointAtSlot(10);
    final StateRegenerationBaseSelector selector = createSelector();

    final SignedBlockAndState betterStartingPoint =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(11));
    final StateRegenerationBaseSelector newSelector =
        selector.withRebasedStartingPoint(betterStartingPoint);
    assertThat(newSelector.getBestBase()).isCompletedWithValue(Optional.of(betterStartingPoint));
  }

  @Test
  void shouldStoreRebasedStateThatIsBetterThanLatestEpochBoundary() {
    withClosestAvailableFromStoreAtSlot(5);
    withLatestEpochBoundaryAtSlot(12);
    withRebasedStartingPointAtSlot(10);
    final StateRegenerationBaseSelector selector = createSelector();

    final SignedBlockAndState betterStartingPoint =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(15));
    final StateRegenerationBaseSelector newSelector =
        selector.withRebasedStartingPoint(betterStartingPoint);
    assertThat(newSelector.getBestBase()).isCompletedWithValue(Optional.of(betterStartingPoint));
  }

  private void assertSelectedBase(final SignedBlockAndState fromStore) {
    assertThatSafeFuture(getBestBase()).isCompletedWithValue(Optional.of(fromStore));
  }

  private SignedBlockAndState withClosestAvailableFromStoreAtSlot(final long slot) {
    final SignedBlockAndState blockAndState =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(slot));
    closestBlockAndStateFromStore = Optional.of(blockAndState);
    return blockAndState;
  }

  private SignedBlockAndState withLatestEpochBoundaryAtSlot(final long slot) {
    final SignedBlockAndState blockAndState =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(slot));
    latestEpochBoundary = Optional.of(blockAndState);
    return blockAndState;
  }

  private SignedBlockAndState withRebasedStartingPointAtSlot(final long slot) {
    final SignedBlockAndState blockAndState =
        dataStructureUtil.randomSignedBlockAndState(UInt64.valueOf(slot));
    rebasedStartingPoint = Optional.of(blockAndState);
    return blockAndState;
  }

  private SafeFuture<Optional<SignedBlockAndState>> getBestBase() {
    return createSelector().getBestBase();
  }

  private StateRegenerationBaseSelector createSelector() {
    final Optional<BlockRootAndState> closestStateFromStore =
        closestBlockAndStateFromStore.map(
            blockAndState ->
                new BlockRootAndState(blockAndState.getRoot(), blockAndState.getState()));
    when(closestAvailableStateSupplier.get()).thenReturn(closestStateFromStore);

    closestBlockAndStateFromStore.ifPresent(
        blockAndState ->
            when(blockProvider.getBlock(blockAndState.getRoot()))
                .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState.getBlock()))));

    latestEpochBoundary.ifPresent(
        blockAndState ->
            when(stateAndBlockProvider.getBlockAndState(blockAndState.getRoot()))
                .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState))));

    return new StateRegenerationBaseSelector(
        latestEpochBoundary.map(
            blockAndState ->
                new SlotAndBlockRoot(blockAndState.getSlot(), blockAndState.getRoot())),
        closestAvailableStateSupplier,
        stateAndBlockProvider,
        blockProvider,
        rebasedStartingPoint,
        REPLAY_TOLERANCE_TO_AVOID_LOADING_IN_EPOCHS);
  }
}
