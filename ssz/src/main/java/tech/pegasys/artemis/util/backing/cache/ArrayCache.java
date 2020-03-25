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

package tech.pegasys.artemis.util.backing.cache;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.IntFunction;

public final class ArrayCache<V> implements IntCache<V> {
  private V[] values;

  public ArrayCache() {
    this(16);
  }

  public ArrayCache(int initialSize) {
    this.values = createArray(initialSize);
  }

  private ArrayCache(V[] values) {
    this.values = values;
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
  public synchronized V getInt(int key, IntFunction<V> fallback) {
    extend(key);
    V val = values[key];
    if (val == null) {
      val = fallback.apply(key);
      values[key] = val;
    }
    return val;
  }

  @Override
  public synchronized Optional<V> getCached(Integer key) {
    return key >= values.length ? Optional.empty() : Optional.ofNullable(values[key]);
  }

  @Override
  public synchronized IntCache<V> copy() {
    return new ArrayCache<>(Arrays.copyOf(values, values.length));
  }

  @Override
  public synchronized IntCache<V> transfer() {
    ArrayCache<V> ret = new ArrayCache<>(values);
    values = createArray(16);
    return ret;
  }

  @Override
  public synchronized void invalidateWithNewValueInt(int key, V newValue) {
    extend(key);
    values[key] = newValue;
  }

  @Override
  public synchronized void invalidateInt(int key) {
    extend(key);
    values[key] = null;
  }

  @Override
  public synchronized void clear() {
    values = createArray(16);
  }
}
