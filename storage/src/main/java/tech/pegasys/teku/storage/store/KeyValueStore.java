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

package tech.pegasys.teku.storage.store;

import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Generic simple key-value store interface Both key and value are not allowed to be null
 *
 * @param <TKey> key type
 * @param <TValue> value type
 */
public interface KeyValueStore<TKey, TValue> {

  /** Puts a new value. If the value is {@code null} then the entry is removed if exist */
  void put(@NotNull TKey key, TValue value);

  /**
   * Returns a value corresponding to the key or {{@link Optional#empty()}} if entry doesn't exist
   * in the store
   */
  Optional<TValue> get(@NotNull TKey key);

  /**
   * Performs batch store update. If a {@link KeyValue} has {@code null} value then the store entry
   * is to be removed
   *
   * <p>The implementation may override this default method and declare it to be an atomic store
   * update. Though this generic interface makes no restrictions on atomicity of this method
   */
  default void putAll(Iterable<KeyValue<? extends TKey, ? extends TValue>> data) {
    data.forEach(kv -> put(kv.getKey(), kv.getValue()));
  }

  class KeyValue<K, V> {
    private final K key;
    private final V value;

    public KeyValue(@NotNull K key, V value) {
      this.key = key;
      this.value = value;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }
  }
}
