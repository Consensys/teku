/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.collections;

import java.util.Collections;
import java.util.Comparator;

/** Helper that creates a thread-safe sorted map with a maximum capacity. */
final class SynchronizedSortedLimitedMap<K, V> extends AbstractLimitedMap<K, V> {
  final Comparator<? super K> keyComparator;
  final Comparator<? super V> valueComparator;

  public SynchronizedSortedLimitedMap(
      final int maxSize,
      final Comparator<? super K> keyComparator,
      final Comparator<? super V> valueComparator) {
    super(
        Collections.synchronizedMap(
            createSortedLimitedMap(maxSize, keyComparator, valueComparator)),
        maxSize);
    this.keyComparator = keyComparator;
    this.valueComparator = valueComparator;
  }

  @Override
  public LimitedMap<K, V> copy() {
    SynchronizedSortedLimitedMap<K, V> map =
        new SynchronizedSortedLimitedMap<>(getMaxSize(), keyComparator, valueComparator);
    synchronized (delegate) {
      map.putAll(delegate);
    }
    return map;
  }
}
