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

package org.ethereum.beacon.db.source.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.ethereum.beacon.db.source.HoleyList;

public class HashMapHoleyList<V> implements HoleyList<V> {
  private final Map<Long, V> store = new HashMap<>();
  private long size = 0;

  @Override
  public long size() {
    return size;
  }

  @Override
  public void put(long idx, V value) {
    if (value == null) return;
    size = Math.max(size, idx + 1);
    store.put(idx, value);
  }

  @Override
  public Optional<V> get(long idx) {
    return Optional.ofNullable(store.get(idx));
  }
}
