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

package tech.pegasys.teku.statetransition.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

/** Holds items with slots that are in the future relative to our node's current slot */
public class FutureItems<T> implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  static final UInt64 DEFAULT_FUTURE_SLOT_TOLERANCE = UInt64.valueOf(2);
  private static final int MAX_ITEMS_PER_SLOT = 500;

  private final UInt64 futureSlotTolerance;
  private final Function<T, UInt64> slotFunction;

  private final NavigableMap<UInt64, Set<T>> queuedFutureItems = new ConcurrentSkipListMap<>();
  private volatile UInt64 currentSlot = UInt64.ZERO;

  private FutureItems(final Function<T, UInt64> slotFunction, final UInt64 futureSlotTolerance) {
    this.slotFunction = slotFunction;
    this.futureSlotTolerance = futureSlotTolerance;
  }

  public static <T> FutureItems<T> create(final Function<T, UInt64> slotFunction) {
    return new FutureItems<T>(slotFunction, DEFAULT_FUTURE_SLOT_TOLERANCE);
  }

  public static <T> FutureItems<T> create(
      final Function<T, UInt64> slotFunction, final UInt64 futureSlotTolerance) {
    return new FutureItems<T>(slotFunction, futureSlotTolerance);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    currentSlot = slot;
  }

  /**
   * Add a item to the future items set
   *
   * @param item The item to add
   */
  public void add(final T item) {
    final UInt64 slot = slotFunction.apply(item);
    if (slot.isGreaterThan(currentSlot.plus(futureSlotTolerance))) {
      // Item is too far in the future
      return;
    }

    LOG.trace("Save future item at slot {} for later import: {}", slot, item);
    queuedFutureItems.computeIfAbsent(slot, key -> createNewSet()).add(item);
  }

  /**
   * Removes all items that are no longer in the future according to the {@code currentSlot}
   *
   * @param currentSlot The slot to be considered current
   * @return The set of items that are no longer in the future
   */
  public List<T> prune(final UInt64 currentSlot) {
    final List<T> dequeued = new ArrayList<>();
    queuedFutureItems
        .headMap(currentSlot, true)
        .keySet()
        .forEach(key -> dequeued.addAll(queuedFutureItems.remove(key)));
    return dequeued;
  }

  public boolean contains(final T item) {
    return queuedFutureItems
        .getOrDefault(slotFunction.apply(item), Collections.emptySet())
        .contains(item);
  }

  public int size() {
    return queuedFutureItems.values().stream().map(Set::size).reduce(Integer::sum).orElse(0);
  }

  private Set<T> createNewSet() {
    return LimitedSet.create(MAX_ITEMS_PER_SLOT);
  }
}
