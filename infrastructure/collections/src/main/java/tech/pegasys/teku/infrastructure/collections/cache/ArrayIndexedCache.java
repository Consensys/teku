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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Cache optimized for dense, non-negative integer-like keys.
 *
 * <p>Concurrent growth may occasionally drop a just-written entry if another thread replaces the
 * backing array at the same time. That only causes an extra cache miss later and keeps the
 * implementation compact.
 */
public class ArrayIndexedCache<K, V> implements Cache<K, V> {
  private final ToIntFunction<K> keyToIndex;
  private volatile Object[] values;

  public ArrayIndexedCache(final ToIntFunction<K> keyToIndex) {
    this(keyToIndex, new Object[0]);
  }

  private ArrayIndexedCache(final ToIntFunction<K> keyToIndex, final Object[] values) {
    this.keyToIndex = keyToIndex;
    this.values = values;
  }

  @Override
  public V get(final K key, final Function<K, V> fallback) {
    final int index = getIndex(key);
    final V cachedValue = getValue(index);
    if (cachedValue != null) {
      return cachedValue;
    }

    final V computedValue = fallback.apply(key);
    if (computedValue == null) {
      return null;
    }

    final Object[] currentValues = getValuesWithCapacity(index + 1);
    final V existingValue = getValue(currentValues, index);
    if (existingValue != null) {
      return existingValue;
    }
    currentValues[index] = computedValue;
    return computedValue;
  }

  @Override
  public Optional<V> getCached(final K key) {
    return Optional.ofNullable(getValue(getIndex(key)));
  }

  @Override
  public Cache<K, V> copy() {
    return new ArrayIndexedCache<>(keyToIndex, Arrays.copyOf(values, values.length));
  }

  @Override
  public void invalidate(final K key) {
    final int index = getIndex(key);
    final Object[] currentValues = values;
    if (index < currentValues.length) {
      currentValues[index] = null;
    }
  }

  @Override
  public void invalidateWithNewValue(final K key, final V newValue) {
    final int index = getIndex(key);
    final Object[] currentValues = getValuesWithCapacity(index + 1);
    currentValues[index] = Objects.requireNonNull(newValue);
  }

  @Override
  public void clear() {
    values = new Object[0];
  }

  @Override
  public int size() {
    int entryCount = 0;
    for (final Object value : values) {
      if (value != null) {
        entryCount++;
      }
    }
    return entryCount;
  }

  private synchronized void ensureCapacity(final int requiredLength) {
    if (requiredLength <= values.length) {
      return;
    }
    values = Arrays.copyOf(values, requiredLength);
  }

  private Object[] getValuesWithCapacity(final int requiredLength) {
    while (true) {
      ensureCapacity(requiredLength);
      final Object[] currentValues = values;
      if (requiredLength <= currentValues.length) {
        return currentValues;
      }
    }
  }

  private int getIndex(final K key) {
    final int index = keyToIndex.applyAsInt(key);
    if (index < 0) {
      throw new IllegalArgumentException("Cache index must be non-negative");
    }
    return index;
  }

  private V getValue(final int index) {
    return getValue(values, index);
  }

  @SuppressWarnings("unchecked")
  private V getValue(final Object[] currentValues, final int index) {
    return index < currentValues.length ? (V) currentValues[index] : null;
  }
}
