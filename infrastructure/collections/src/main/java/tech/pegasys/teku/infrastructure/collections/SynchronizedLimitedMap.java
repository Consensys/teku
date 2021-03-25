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
import java.util.LinkedHashMap;
import java.util.Map;

/** Helper that creates a thread-safe map with a maximum capacity. */
final class SynchronizedLimitedMap<K, V> extends AbstractLimitedMap<K, V> {

  private static <K, V> Map<K, V> createLimitedMap(final int maxSize) {
    return new LinkedHashMap<>(16, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
        return size() > maxSize;
      }
    };
  }

  private final int maxSize;

  public SynchronizedLimitedMap(final int maxSize) {
    super(Collections.synchronizedMap(createLimitedMap(maxSize)));
    this.maxSize = maxSize;
  }

  @Override
  public int getMaxSize() {
    return maxSize;
  }

  @Override
  public LimitedMap<K, V> copy() {
    SynchronizedLimitedMap<K, V> map = new SynchronizedLimitedMap<>(getMaxSize());
    synchronized (delegate) {
      map.putAll(delegate);
    }
    return map;
  }
}
