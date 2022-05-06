/*
 * Copyright 2022 ConsenSys AG.
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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;

public class SeenAggregatesCache {

  private final Map<Bytes32, Set<SszBitlist>> seenAggregationBitsByDataRoot;
  private final int aggregateSetSize;

  public SeenAggregatesCache(final int rootCacheSize, final int aggregateSetSize) {
    this.seenAggregationBitsByDataRoot = LimitedMap.create(rootCacheSize);
    this.aggregateSetSize = aggregateSetSize;
  }

  public boolean add(final Bytes32 root, final SszBitlist aggregationBits) {
    final Set<SszBitlist> seenBitlists =
        seenAggregationBitsByDataRoot.computeIfAbsent(
            root,
            // Aim to hold all aggregation bits but have a limit for safety.
            // Normally we'd have far fewer as we avoid adding subsets.
            key -> LimitedSet.create(aggregateSetSize));
    if (isAlreadySeen(seenBitlists, aggregationBits)) {
      return false;
    }
    return seenBitlists.add(aggregationBits);
  }

  public boolean isAlreadySeen(final Bytes32 root, final SszBitlist aggregationBits) {
    final Set<SszBitlist> seenAggregates =
        seenAggregationBitsByDataRoot.getOrDefault(root, Collections.emptySet());
    return isAlreadySeen(seenAggregates, aggregationBits);
  }

  private boolean isAlreadySeen(
      final Set<SszBitlist> seenAggregates, final SszBitlist aggregationBits) {
    return seenAggregates.stream().anyMatch(seen -> seen.isSuperSetOf(aggregationBits));
  }
}
