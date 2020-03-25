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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class HashMapCache<K, V> implements Cache<K, V> {

  private Map<K, V> holder;

  private HashMapCache(Map<K, V> holder) {
    this.holder = holder;
  }

  public HashMapCache() {
    this(new HashMap<>());
  }

  @Override
  public synchronized V get(K key, Function<K, V> fallback) {
    return holder.computeIfAbsent(key, fallback);
  }

  @Override
  public synchronized Optional<V> getCached(K key) {
    return Optional.ofNullable(holder.get(key));
  }

  @Override
  public synchronized Cache<K, V> copy() {
    return new HashMapCache<>(new HashMap<>(holder));
  }

  @Override
  public synchronized Cache<K, V> transfer() {
    HashMapCache<K, V> ret = new HashMapCache<>(holder);
    holder = new HashMap<>();
    return ret;
  }

  @Override
  public synchronized void invalidate(K key) {
    holder.remove(key);
  }

  @Override
  public synchronized void invalidateWithNewValue(K key, V newValue) {
    holder.put(key, newValue);
  }

  @Override
  public synchronized void clear() {
    holder.clear();
  }
}
