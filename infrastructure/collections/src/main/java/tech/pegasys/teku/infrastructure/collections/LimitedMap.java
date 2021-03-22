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

package tech.pegasys.teku.infrastructure.collections;

import com.google.common.cache.CacheBuilder;
import java.util.Map;

/** Helper that creates a map with a maximum capacity. */
public final class LimitedMap {
  private LimitedMap() {}

  /**
   * Creates a limited map. The returned map is safe for concurrent access and evicts the least
   * recently used items.
   *
   * @param maxSize The maximum number of elements to keep in the map.
   * @param <K> The key type of the map.
   * @param <V> The value type of the map.
   * @return A map that will evict elements when the max size is exceeded.
   */
  public static <K, V> Map<K, V> create(final int maxSize) {
    return defaultBuilder(maxSize).<K, V>build().asMap();
  }

  /**
   * Creates a limited map using soft references for values. The returned map is safe for concurrent
   * access and evicts the least recently used items.
   *
   * <p>Items may be evicted before maxSize is reached if the garbage collector needs to free up
   * memory.
   *
   * @param maxSize The maximum number of elements to keep in the map.
   * @param <K> The key type of the map.
   * @param <V> The value type of the map.
   * @return A map that will evict elements when the max size is exceeded or when the GC evicts
   *     them.
   */
  public static <K, V> Map<K, V> createSoft(final int maxSize) {
    return defaultBuilder(maxSize).softValues().<K, V>build().asMap();
  }

  private static CacheBuilder<Object, Object> defaultBuilder(final int maxSize) {
    return CacheBuilder.newBuilder().maximumSize(maxSize);
  }
}
