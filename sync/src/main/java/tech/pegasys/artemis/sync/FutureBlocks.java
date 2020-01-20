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
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.util.collections.LimitedSet;
import tech.pegasys.artemis.util.collections.LimitedSet.Mode;

/** Holds blocks with slots that are in the future relative to our node's current slot */
class FutureBlocks {
  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_BLOCKS_PER_SLOT = 500;
  private final NavigableMap<UnsignedLong, Set<SignedBeaconBlock>> queuedFutureBlocks =
      new ConcurrentSkipListMap<>();

  /**
   * Add a block to the future blocks set
   *
   * @param block The block to add
   */
  public void add(final SignedBeaconBlock block) {
    LOG.trace("Save future block at slot {} for later import: {}", block.getSlot(), block);
    queuedFutureBlocks.computeIfAbsent(block.getSlot(), key -> createNewSet()).add(block);
  }

  /**
   * Removes all blocks that are no longer in the future according to the {@code currentSlot}
   *
   * @param currentSlot The slot to be considered current
   * @return The set of blocks that are no longer in the future
   */
  public List<SignedBeaconBlock> prune(final UnsignedLong currentSlot) {
    final List<SignedBeaconBlock> dequeued = new ArrayList<>();
    queuedFutureBlocks
        .headMap(currentSlot, true)
        .keySet()
        .forEach(key -> dequeued.addAll(queuedFutureBlocks.remove(key)));
    return dequeued;
  }

  public boolean contains(final SignedBeaconBlock block) {
    return queuedFutureBlocks.getOrDefault(block.getSlot(), Collections.emptySet()).contains(block);
  }

  public int size() {
    return queuedFutureBlocks.values().stream().map(Set::size).reduce(Integer::sum).orElse(0);
  }

  private Set<SignedBeaconBlock> createNewSet() {
    return LimitedSet.create(MAX_BLOCKS_PER_SLOT, Mode.DROP_LEAST_RECENTLY_ACCESSED);
  }
}
