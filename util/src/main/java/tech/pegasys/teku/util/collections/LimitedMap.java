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

package tech.pegasys.teku.util.collections;

import java.util.LinkedHashMap;
import java.util.Map;

/** Helper that creates a map with a maximum capacity. */
public final class LimitedMap {
  private LimitedMap() {}

  /**
   * Creates a limited map with a default initial capacity.
   *
   * @param maxSize The maximum number of elements to keep in the map.
   * @param mode A mode that determines which element is evicted when the map exceeds its max size.
   * @param <K> The key type of the map.
   * @param <V> The value type of the map.
   * @return A map that will evict elements when the max size is exceeded.
   */
  public static <K, V> Map<K, V> create(final int maxSize, final LimitStrategy mode) {
    return create(16, maxSize, mode);
  }

  /**
   * Creates a limited map.
   *
   * @param initialCapacity The initial size to allocate for the map.
   * @param maxSize The maximum number of elements to keep in the map.
   * @param mode A mode that determines which element is evicted when the map exceeds its max size.
   * @param <K> The key type of the map.
   * @param <V> The value type of the map.
   * @return A map that will evict elements when the max size is exceeded.
   */
  public static <K, V> Map<K, V> create(
      final int initialCapacity, final int maxSize, final LimitStrategy mode) {
    final boolean useAccessOrder = mode.equals(LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
    return new LinkedHashMap<>(initialCapacity, 0.75f, useAccessOrder) {
      @Override
      protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
        return size() > maxSize;
      }
    };
  }
}
