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

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.util.collections.ConcurrentLimitedSet;
import tech.pegasys.teku.util.collections.LimitStrategy;

/** Holds items with slots that are in the future relative to our node's current slot */
public class FutureItems<T> {
  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_ITEMS_PER_SLOT = 500;
  private final NavigableMap<UnsignedLong, Set<T>> queuedFutureItems =
      new ConcurrentSkipListMap<>();
  private final Function<T, UnsignedLong> slotFunction;

  public FutureItems(final Function<T, UnsignedLong> slotFunction) {
    this.slotFunction = slotFunction;
  }

  /**
   * Add a item to the future items set
   *
   * @param item The item to add
   */
  public void add(final T item) {
    final UnsignedLong slot = slotFunction.apply(item);
    LOG.trace("Save future item at slot {} for later import: {}", slot, item);
    queuedFutureItems.computeIfAbsent(slot, key -> createNewSet()).add(item);
  }

  /**
   * Removes all items that are no longer in the future according to the {@code currentSlot}
   *
   * @param currentSlot The slot to be considered current
   * @return The set of items that are no longer in the future
   */
  public List<T> prune(final UnsignedLong currentSlot) {
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
    return ConcurrentLimitedSet.create(
        MAX_ITEMS_PER_SLOT, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
  }
}
