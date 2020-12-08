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

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;
import tech.pegasys.teku.core.stategenerator.CachingTaskQueue.CacheableTask;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StateAtSlotTask implements CacheableTask<SlotAndBlockRoot, BeaconState> {

  /**
   * The number of previous slots to search for an existing checkpoint state with the same root but
   * earlier epoch. While we could search back to zero, that potentially means a lot of cache
   * look-ups that are extremely unlikely to succeed which is wasteful.
   */
  private final UInt64 INTERIM_SLOTS_TO_SEARCH = UInt64.valueOf(640);

  private final SlotAndBlockRoot slotAndBlockRoot;
  private final AsyncStateProvider stateProvider;
  private final Optional<BeaconState> baseState;

  public StateAtSlotTask(
      final SlotAndBlockRoot slotAndBlockRoot, final AsyncStateProvider stateProvider) {
    this.slotAndBlockRoot = slotAndBlockRoot;
    this.stateProvider = stateProvider;
    baseState = Optional.empty();
  }

  private StateAtSlotTask(
      final SlotAndBlockRoot slotAndBlockRoot,
      final AsyncStateProvider stateProvider,
      final BeaconState baseState) {
    this.slotAndBlockRoot = slotAndBlockRoot;
    this.stateProvider = stateProvider;
    this.baseState = Optional.of(baseState);
  }

  @Override
  public SlotAndBlockRoot getKey() {
    return slotAndBlockRoot;
  }

  @Override
  public Stream<SlotAndBlockRoot> streamIntermediateSteps() {
    if (slotAndBlockRoot.getSlot().equals(UInt64.ZERO)) {
      return Stream.empty();
    }
    return Stream.iterate(
            slotAndBlockRoot.getSlot().minus(1),
            current ->
                current != null
                    && current.isGreaterThanOrEqualTo(UInt64.ZERO)
                    && current
                        .plus(INTERIM_SLOTS_TO_SEARCH)
                        .isGreaterThanOrEqualTo(slotAndBlockRoot.getSlot()),
            current -> current.equals(UInt64.ZERO) ? null : current.minus(1))
        .map(slot -> new SlotAndBlockRoot(slot, slotAndBlockRoot.getBlockRoot()));
  }

  @Override
  public CacheableTask<SlotAndBlockRoot, BeaconState> rebase(final BeaconState newBaseValue) {
    return new StateAtSlotTask(slotAndBlockRoot, stateProvider, newBaseValue);
  }

  @Override
  public SafeFuture<Optional<BeaconState>> performTask() {
    return getBaseState().thenApply(maybeState -> maybeState.map(this::regenerateFromState));
  }

  private SafeFuture<Optional<BeaconState>> getBaseState() {
    return baseState.isPresent()
        ? SafeFuture.completedFuture(baseState)
        : stateProvider.getState(slotAndBlockRoot.getBlockRoot());
  }

  private BeaconState regenerateFromState(final BeaconState state) {
    if (state.getSlot().isGreaterThan(slotAndBlockRoot.getSlot())) {
      throw new InvalidCheckpointException(
          String.format(
              "Checkpoint state (%s) must be at or prior to checkpoint slot boundary (%s)",
              state.getSlot(), slotAndBlockRoot.getSlot()));
    }
    try {
      if (state.getSlot().equals(slotAndBlockRoot.getSlot())) {
        return state;
      }

      return new StateTransition().process_slots(state, slotAndBlockRoot.getSlot());
    } catch (SlotProcessingException | EpochProcessingException | IllegalArgumentException e) {
      throw new InvalidCheckpointException(e);
    }
  }

  @FunctionalInterface
  public interface AsyncStateProvider {
    static AsyncStateProvider fromBlockAndState(final SignedBlockAndState blockAndState) {
      return (Bytes32 root) -> {
        if (Objects.equals(root, blockAndState.getRoot())) {
          return SafeFuture.completedFuture(Optional.of(blockAndState.getState()));
        } else {
          return SafeFuture.completedFuture(Optional.empty());
        }
      };
    }

    static AsyncStateProvider fromAnchor(final AnchorPoint anchor) {
      return (Bytes32 root) -> {
        if (Objects.equals(root, anchor.getRoot())) {
          return SafeFuture.completedFuture(Optional.of(anchor.getState()));
        } else {
          return SafeFuture.completedFuture(Optional.empty());
        }
      };
    }

    SafeFuture<Optional<BeaconState>> getState(Bytes32 blockRoot);
  }
}
