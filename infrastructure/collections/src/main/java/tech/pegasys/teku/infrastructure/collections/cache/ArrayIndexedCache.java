/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.collections.cache;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Unbounded cache for dense, non-negative integer-like keys.
 *
 * <p>This cache avoids the per-entry overhead of a hash table by storing values directly in an
 * array slot derived from the key. It is suitable for append-only or mostly dense key spaces such
 * as validator indices.
 *
 * <p>Like {@code ArrayIntCache}, this implementation avoids synchronization on the hot path. Under
 * concurrent growth it may lose some cached entries, which is safe because the missing values are
 * recomputed on demand.
 *
 * @param <K> type of keys
 * @param <V> type of values
 */
public final class ArrayIndexedCache<K, V> implements Cache<K, V> {
  private static final int DEFAULT_INITIAL_CACHE_SIZE = 16;

  private final ToIntFunction<K> keyToIndex;
  private final int initialSize;
  private volatile Object[] values;

  public ArrayIndexedCache(final ToIntFunction<K> keyToIndex) {
    this(DEFAULT_INITIAL_CACHE_SIZE, keyToIndex);
  }

  public ArrayIndexedCache(final int initialSize, final ToIntFunction<K> keyToIndex) {
    this(keyToIndex, createArray(initialSize), initialSize);
  }

  private ArrayIndexedCache(
      final ToIntFunction<K> keyToIndex, final Object[] values, final int initialSize) {
    this.keyToIndex = keyToIndex;
    this.values = values;
    this.initialSize = initialSize;
  }

  private static Object[] createArray(final int size) {
    return new Object[size];
  }

  private Object[] extend(final int index) {
    final Object[] valuesLocal = values;
    int newSize = valuesLocal.length;
    if (index >= newSize) {
      while (index >= newSize) {
        newSize <<= 1;
      }
      final Object[] newValues = Arrays.copyOf(valuesLocal, newSize);
      values = newValues;
      return newValues;
    }
    return valuesLocal;
  }

  @SuppressWarnings("unchecked")
  private V getCachedValue(final int index) {
    final Object[] valuesLocal = values;
    return index >= valuesLocal.length ? null : (V) valuesLocal[index];
  }

  @Override
  public V get(final K key, final Function<K, V> fallback) {
    final int index = keyToIndex.applyAsInt(key);
    V value = getCachedValue(index);
    if (value == null) {
      value = fallback.apply(key);
      if (value != null) {
        extend(index)[index] = value;
      }
    }
    return value;
  }

  @Override
  public Optional<V> getCached(final K key) {
    return Optional.ofNullable(getCachedValue(keyToIndex.applyAsInt(key)));
  }

  @Override
  public Cache<K, V> copy() {
    final Object[] valuesLocal = values;
    return new ArrayIndexedCache<>(
        keyToIndex, Arrays.copyOf(valuesLocal, valuesLocal.length), initialSize);
  }

  @Override
  public void invalidate(final K key) {
    final int index = keyToIndex.applyAsInt(key);
    final Object[] valuesLocal = values;
    if (index < valuesLocal.length) {
      valuesLocal[index] = null;
    }
  }

  @Override
  public void invalidateWithNewValue(final K key, final V newValue) {
    final int index = keyToIndex.applyAsInt(key);
    extend(index)[index] = newValue;
  }

  @Override
  public void clear() {
    values = createArray(initialSize);
  }

  @Override
  public int size() {
    int count = 0;
    for (Object value : values) {
      if (value != null) {
        count++;
      }
    }
    return count;
  }
}
