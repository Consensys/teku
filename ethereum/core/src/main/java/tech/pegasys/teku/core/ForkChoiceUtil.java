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

package tech.pegasys.teku.core;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.util.config.Constants.GENESIS_SLOT;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import java.time.Instant;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ForkChoiceUtil {

  public static UInt64 get_slots_since_genesis(ReadOnlyStore store, boolean useUnixTime) {
    UInt64 time = useUnixTime ? UInt64.valueOf(Instant.now().getEpochSecond()) : store.getTime();
    return getCurrentSlot(time, store.getGenesisTime());
  }

  public static UInt64 getCurrentSlot(UInt64 currentTime, UInt64 genesisTime) {
    if (currentTime.isLessThan(genesisTime)) {
      return UInt64.ZERO;
    }
    return currentTime.minus(genesisTime).dividedBy(SECONDS_PER_SLOT);
  }

  public static UInt64 getSlotStartTime(UInt64 slotNumber, UInt64 genesisTime) {
    return genesisTime.plus(slotNumber.times(SECONDS_PER_SLOT));
  }

  public static UInt64 get_current_slot(ReadOnlyStore store, boolean useUnixTime) {
    return UInt64.valueOf(GENESIS_SLOT).plus(get_slots_since_genesis(store, useUnixTime));
  }

  public static UInt64 get_current_slot(ReadOnlyStore store) {
    return get_current_slot(store, false);
  }

  public static UInt64 compute_slots_since_epoch_start(UInt64 slot) {
    return slot.minus(compute_start_slot_at_epoch(compute_epoch_at_slot(slot)));
  }

  /**
   * Get the ancestor of ``block`` with slot number ``slot``.
   *
   * @param forkChoiceStrategy
   * @param root
   * @param slot
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.10.1/specs/phase0/fork-choice.md#get_ancestor</a>
   */
  public static Optional<Bytes32> get_ancestor(
      ReadOnlyForkChoiceStrategy forkChoiceStrategy, Bytes32 root, UInt64 slot) {
    return forkChoiceStrategy.getAncestor(root, slot);
  }

  public static NavigableMap<UInt64, Bytes32> getAncestors(
      ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      Bytes32 root,
      UInt64 startSlot,
      UInt64 step,
      UInt64 count) {
    final NavigableMap<UInt64, Bytes32> roots = new TreeMap<>();
    // minus(ONE) because the start block is included
    final UInt64 endSlot = startSlot.plus(step.times(count)).minus(UInt64.ONE);
    Bytes32 parentRoot = root;
    Optional<UInt64> parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    while (parentSlot.isPresent() && parentSlot.get().compareTo(startSlot) > 0) {
      maybeAddRoot(startSlot, step, roots, endSlot, parentRoot, parentSlot);
      parentRoot = forkChoiceStrategy.blockParentRoot(parentRoot).orElseThrow();
      parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    }
    maybeAddRoot(startSlot, step, roots, endSlot, parentRoot, parentSlot);
    return roots;
  }

  /**
   * @param forkChoiceStrategy the object that stores information on forks and block roots
   * @param root the root that dictates the block/fork that we walk backwards from
   * @param startSlot the slot (exclusive) until which we walk the chain backwards
   * @return every block root from root (inclusive) to start slot (exclusive) traversing the chain
   *     backwards
   */
  public static NavigableMap<UInt64, Bytes32> getAncestorsOnFork(
      ReadOnlyForkChoiceStrategy forkChoiceStrategy, Bytes32 root, UInt64 startSlot) {
    final NavigableMap<UInt64, Bytes32> roots = new TreeMap<>();
    Bytes32 parentRoot = root;
    Optional<UInt64> parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    while (parentSlot.isPresent() && parentSlot.get().isGreaterThan(startSlot)) {
      maybeAddRoot(startSlot, UInt64.ONE, roots, UInt64.MAX_VALUE, parentRoot, parentSlot);
      parentRoot = forkChoiceStrategy.blockParentRoot(parentRoot).orElseThrow();
      parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    }
    return roots;
  }

  private static void maybeAddRoot(
      final UInt64 startSlot,
      final UInt64 step,
      final NavigableMap<UInt64, Bytes32> roots,
      final UInt64 endSlot,
      final Bytes32 root,
      final Optional<UInt64> maybeSlot) {
    maybeSlot.ifPresent(
        slot -> {
          if (slot.compareTo(endSlot) <= 0
              && slot.compareTo(startSlot) >= 0
              && slot.minus(startSlot).mod(step).equals(UInt64.ZERO)) {
            roots.put(slot, root);
          }
        });
  }

  // Fork Choice Event Handlers

  /**
   * @param store
   * @param time
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#on_tick</a>
   */
  public static void on_tick(MutableStore store, UInt64 time) {
    // To be extra safe check both time and genesisTime, although time should always be >=
    // genesisTime
    if (store.getTime().isGreaterThan(time) || store.getGenesisTime().isGreaterThan(time)) {
      return;
    }
    UInt64 previous_slot = get_current_slot(store);

    // Update store time
    store.setTime(time);

    UInt64 current_slot = get_current_slot(store);

    // Not a new epoch, return
    if (!(current_slot.compareTo(previous_slot) > 0
        && compute_slots_since_epoch_start(current_slot).equals(UInt64.ZERO))) {
      return;
    }

    // Update store.justified_checkpoint if a better checkpoint is known
    if (store
            .getBestJustifiedCheckpoint()
            .getEpoch()
            .compareTo(store.getJustifiedCheckpoint().getEpoch())
        > 0) {
      store.setJustifiedCheckpoint(store.getBestJustifiedCheckpoint());
    }
  }
}
