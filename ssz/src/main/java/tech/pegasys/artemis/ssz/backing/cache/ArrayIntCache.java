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

package tech.pegasys.artemis.ssz.backing.cache;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.IntFunction;

public final class ArrayIntCache<V> implements IntCache<V> {
  private V[] values;
  private final int initSize;

  public ArrayIntCache() {
    this(16);
  }

  public ArrayIntCache(int initialSize) {
    this.initSize = initialSize;
    this.values = createArray(initialSize);
  }

  private ArrayIntCache(V[] values, int initSize) {
    this.values = values;
    this.initSize = initSize;
  }

  @SuppressWarnings("unchecked")
  private V[] createArray(int size) {
    return (V[]) new Object[size];
  }

  private void extend(int index) {
    int newSize = values.length;
    if (index >= newSize) {
      while (index >= newSize) {
        newSize <<= 1;
      }
      values = Arrays.copyOf(values, newSize);
    }
  }

  @Override
  public V getInt(int key, IntFunction<V> fallback) {
    V val = key >= values.length ? null : values[key];
    if (val == null) {
      val = fallback.apply(key);
      extend(key);
      values[key] = val;
    }
    return val;
  }

  @Override
  public Optional<V> getCached(Integer key) {
    return key >= values.length ? Optional.empty() : Optional.ofNullable(values[key]);
  }

  @Override
  public IntCache<V> copy() {
    return new ArrayIntCache<>(Arrays.copyOf(values, values.length), initSize);
  }

  @Override
  public IntCache<V> transfer() {
    return copy();
  }

  @Override
  public void invalidateWithNewValueInt(int key, V newValue) {
    extend(key);
    values[key] = newValue;
  }

  @Override
  public void invalidateInt(int key) {
    if (key < values.length) {
      values[key] = null;
    }
  }

  @Override
  public void clear() {
    values = createArray(initSize);
  }
}
