/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.discovery.database;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/** Created by Anton Nashatyrev on 19.11.2018. */
public class HashMapDataSource<K, V> implements DataSource<K, V> {

  Map<K, V> store = new ConcurrentHashMap<>();

  @Override
  public Optional<V> get(@Nonnull K key) {
    return Optional.ofNullable(store.get(key));
  }

  @Override
  public void put(@Nonnull K key, @Nonnull V value) {
    store.put(key, value);
  }

  @Override
  public void remove(@Nonnull K key) {
    store.remove(key);
  }

  @Override
  public void flush() {
    // nothing to do
  }

  public Map<K, V> getStore() {
    return store;
  }
}
