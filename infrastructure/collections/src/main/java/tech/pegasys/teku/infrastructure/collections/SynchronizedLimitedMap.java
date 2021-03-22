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

import java.util.Collections;
import java.util.Map;

/** Helper that creates a thread-safe map with a maximum capacity. */
public final class SynchronizedLimitedMap {
  private SynchronizedLimitedMap() {}

  public static <K, V> Map<K, V> createEmpty() {
    return Collections.emptyMap();
  }

  /**
   * Creates a limited map with a default initial capacity.
   *
   * @param maxSize The maximum number of elements to keep in the map.
   * @param mode A mode that determines which element is evicted when the map exceeds its max size.
   * @param <K> The key type of the map.
   * @param <V> The value type of the map.
   * @return A thread-safe map that will evict elements when the max size is exceeded.
   */
  public static <K, V> Map<K, V> create(final int maxSize, final LimitStrategy mode) {
    return Collections.synchronizedMap(LimitedHashMap.create(maxSize, mode));
  }

  /**
   * Creates a limited map with initial entries.
   *
   * @param maxSize The maximum number of elements to keep in the map.
   * @param mode A mode that determines which element is evicted when the map exceeds its max size.
   * @param initSynchronizedLimitedMap initial entries. For this content to be copied safely without
   *     potential {@link java.util.ConcurrentModificationException} and concurrency issues the
   *     passed collection should be an instance created by one of {@link SynchronizedLimitedMap}
   *     methods
   * @param <K> The key type of the map.
   * @param <V> The value type of the map.
   * @return A thread-safe map that will evict elements when the max size is exceeded.
   */
  public static <K, V> Map<K, V> create(
      final int maxSize, final LimitStrategy mode, Map<K, V> initSynchronizedLimitedMap) {
    Map<K, V> map = Collections.synchronizedMap(LimitedHashMap.create(maxSize, mode));
    synchronized (initSynchronizedLimitedMap) {
      map.putAll(initSynchronizedLimitedMap);
    }
    return map;
  }

  /**
   * Creates a limited map.
   *
   * @param initialCapacity The initial size to allocate for the map.
   * @param maxSize The maximum number of elements to keep in the map.
   * @param mode A mode that determines which element is evicted when the map exceeds its max size.
   * @param <K> The key type of the map.
   * @param <V> The value type of the map.
   * @return A thread-safe map that will evict elements when the max size is exceeded.
   */
  public static <K, V> Map<K, V> create(
      final int initialCapacity, final int maxSize, final LimitStrategy mode) {
    return Collections.synchronizedMap(LimitedHashMap.create(initialCapacity, maxSize, mode));
  }
}
