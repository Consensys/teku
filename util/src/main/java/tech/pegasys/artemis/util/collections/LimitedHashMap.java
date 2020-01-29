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

package tech.pegasys.artemis.util.collections;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

/** Map with limited capacity. When map size overflows max capacity the eldest entry is dropped */
public class LimitedHashMap<K, V> extends LinkedHashMap<K, V> {
  private final int maxSize;

  public LimitedHashMap(int maxSize) {
    this.maxSize = maxSize;
  }

  @Override
  protected boolean removeEldestEntry(Entry<K, V> eldest) {
    return size() > maxSize;
  }
}
