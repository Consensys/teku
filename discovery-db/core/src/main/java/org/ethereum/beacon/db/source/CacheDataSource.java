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

package org.ethereum.beacon.db.source;

import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Represents {@link DataSource} which caches upstream key-value entries (either on read, write or
 * both)
 */
public interface CacheDataSource<KeyType, ValueType>
    extends LinkedDataSource<KeyType, ValueType, KeyType, ValueType> {

  /**
   * Returns the entry if it's currently in the cache. Shouldn't query upsource for data if entry
   * not found in the cache.
   *
   * <p>If the value is not cached returns <code>Optional.empty()</code> If the value cached and the
   * value is null returns <code>Optional.of(Optional.empty())</code> If the value cached and the
   * value is not null returns <code>Optional.of(Optional.of(value))</code>
   */
  Optional<Optional<ValueType>> getCacheEntry(@Nonnull KeyType key);

  /**
   * Evaluates a number of bytes occupied by cached objects in memory.
   *
   * @return size in memory.
   */
  long evaluateSize();
}
