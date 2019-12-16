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

package tech.pegasys.artemis.sync;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;

/** Holds blocks with slots that are in the future relative to our node's current slot */
class FutureBlocks {
  private final NavigableMap<UnsignedLong, Set<BeaconBlock>> queuedFutureBlocks =
      new ConcurrentSkipListMap<>();

  /**
   * Add a block to the future blocks set
   *
   * @param block The block to add
   */
  public void add(final BeaconBlock block) {
    queuedFutureBlocks
        .computeIfAbsent(
            block.getSlot(), key -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
        .add(block);
  }

  /**
   * Removes all blocks that are no longer in the future according to the {@code currentSlot}
   *
   * @param currentSlot The slot to be considered current
   * @return The set of blocks that are no longer in the future
   */
  public List<BeaconBlock> prune(final UnsignedLong currentSlot) {
    final List<BeaconBlock> dequeued = new ArrayList<>();
    queuedFutureBlocks
        .headMap(currentSlot, true)
        .keySet()
        .forEach(key -> dequeued.addAll(queuedFutureBlocks.remove(key)));
    return dequeued;
  }

  public boolean contains(final BeaconBlock block) {
    final Set<BeaconBlock> blocks = queuedFutureBlocks.get(block.getSlot());
    if (blocks == null) {
      return false;
    }
    return blocks.contains(block);
  }

  public int size() {
    return queuedFutureBlocks.values().stream().map(Set::size).reduce(Integer::sum).orElse(0);
  }
}
