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

import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.lookup.StateAndBlockProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.state.BlockRootAndState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StateRegenerationBaseSelector {
  private final Optional<SlotAndBlockRoot> latestEpochBoundary;
  private final Supplier<Optional<BlockRootAndState>> closestAvailableStateSupplier;
  private final StateAndBlockProvider stateAndBlockProvider;
  private final BlockProvider blockProvider;
  private final Optional<SignedBlockAndState> rebasedStartingPoint;

  /**
   * Loading states from disk is expensive in terms of time and the amount of memory they consume so
   * if we a reasonably close option, prefer replaying blocks over loading the state from disk. This
   * defines what "reasonably close" means in epochs.
   */
  private final int replayToleranceToAvoidLoadingInEpochs;

  public StateRegenerationBaseSelector(
      final Optional<SlotAndBlockRoot> latestEpochBoundary,
      final Supplier<Optional<BlockRootAndState>> closestAvailableStateSupplier,
      final StateAndBlockProvider stateAndBlockProvider,
      final BlockProvider blockProvider,
      final Optional<SignedBlockAndState> rebasedStartingPoint,
      final int replayToleranceToAvoidLoadingInEpochs) {
    this.latestEpochBoundary = latestEpochBoundary;
    this.closestAvailableStateSupplier = closestAvailableStateSupplier;
    this.stateAndBlockProvider = stateAndBlockProvider;
    this.blockProvider = blockProvider;
    this.rebasedStartingPoint = rebasedStartingPoint;
    this.replayToleranceToAvoidLoadingInEpochs = replayToleranceToAvoidLoadingInEpochs;
  }

  public StateRegenerationBaseSelector withRebasedStartingPoint(
      final SignedBlockAndState blockAndState) {
    if (isBetterThanCurrentRebasedStartingPoint(blockAndState)
        && isEqualToOrBetterThanLatestEpochBoundary(blockAndState)) {
      return new StateRegenerationBaseSelector(
          latestEpochBoundary,
          closestAvailableStateSupplier,
          stateAndBlockProvider,
          blockProvider,
          Optional.of(blockAndState),
          replayToleranceToAvoidLoadingInEpochs);
    }
    return this;
  }

  private boolean isEqualToOrBetterThanLatestEpochBoundary(
      final SignedBlockAndState blockAndState) {
    return latestEpochBoundary.isEmpty()
        || blockAndState.getSlot().isGreaterThanOrEqualTo(latestEpochBoundary.get().getSlot());
  }

  private boolean isBetterThanCurrentRebasedStartingPoint(final SignedBlockAndState blockAndState) {
    return isBetterThan(
        blockAndState.getSlot(), rebasedStartingPoint.map(SignedBlockAndState::getSlot));
  }

  public SafeFuture<Optional<SignedBlockAndState>> getBestBase() {
    final Optional<BlockRootAndState> closestAvailableFromStore =
        closestAvailableStateSupplier.get();
    if (closestAvailableFromStore.isEmpty()) {
      // Can't be a valid target state or has since been finalized. No point regenerating.
      return SafeFuture.completedFuture(Optional.empty());
    }

    final Optional<UInt64> storeSlot = closestAvailableFromStore.map(BlockRootAndState::getSlot);
    final Optional<UInt64> epochBoundarySlot =
        getLatestEpochBoundarySlotMinusTolerance(latestEpochBoundary);
    final Optional<UInt64> rebasedSlot = rebasedStartingPoint.map(SignedBlockAndState::getSlot);

    if (epochBoundarySlot.isPresent()
        && isBestOption(epochBoundarySlot.get(), storeSlot, rebasedSlot)) {
      return stateAndBlockProvider
          .getBlockAndState(latestEpochBoundary.get().getBlockRoot())
          .thenCompose(
              maybeBlockAndState -> {
                if (maybeBlockAndState.isEmpty()) {
                  return getBestBaseExcludingLatestEpochBoundary(closestAvailableFromStore.get());
                } else {
                  return SafeFuture.completedFuture(maybeBlockAndState);
                }
              });
    }

    return getBestBaseExcludingLatestEpochBoundary(closestAvailableFromStore.get());
  }

  private SafeFuture<Optional<SignedBlockAndState>> getBestBaseExcludingLatestEpochBoundary(
      final BlockRootAndState closestAvailableFromStore) {
    if (rebasedStartingPoint.isPresent()
        && rebasedStartingPoint
            .get()
            .getSlot()
            .isGreaterThan(closestAvailableFromStore.getSlot())) {
      return SafeFuture.completedFuture(rebasedStartingPoint);
    }
    return blockProvider
        .getBlock(closestAvailableFromStore.getBlockRoot())
        .thenApply(
            maybeBlock ->
                maybeBlock.map(
                    block -> new SignedBlockAndState(block, closestAvailableFromStore.getState())));
  }

  private Optional<UInt64> getLatestEpochBoundarySlotMinusTolerance(
      final Optional<SlotAndBlockRoot> latestEpochBoundary) {
    return latestEpochBoundary
        .map(SlotAndBlockRoot::getSlot)
        .map(slot -> slot.minusMinZero(replayToleranceToAvoidLoadingInEpochs * SLOTS_PER_EPOCH));
  }

  @SafeVarargs
  private boolean isBestOption(final UInt64 slot, final Optional<UInt64>... others) {
    return Stream.of(others).allMatch(other -> isBetterThan(slot, other));
  }

  private boolean isBetterThan(final UInt64 slot, final Optional<UInt64> other) {
    return other.isEmpty() || slot.isGreaterThan(other.get());
  }
}
