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

import java.util.function.Function;
import org.ethereum.beacon.db.source.impl.CacheSizeEvaluatorImpl;

/**
 * Evaluates a number of bytes occupied by cache or buffer in memory.
 *
 * @param <KeyType> a key type.
 * @param <ValueType> a value type.
 * @see WriteBuffer
 */
public interface CacheSizeEvaluator<KeyType, ValueType> {

  /**
   * Returns cache size in bytes.
   *
   * @return a number of bytes.
   */
  long getEvaluatedSize();

  /** This method MUST be called whenever cache gets reset. */
  void reset();

  /**
   * This method MUST be called whenever new entry is added to the cache.
   *
   * @param key a key.
   * @param value a value.
   */
  void added(KeyType key, ValueType value);

  /**
   * This method MUST be called whenever new entry is removed from the cache.
   *
   * @param key a key.
   * @param value a value.
   */
  void removed(KeyType key, ValueType value);

  static <KeyType, ValueType> CacheSizeEvaluator<KeyType, ValueType> noSizeEvaluator() {
    return getInstance((KeyType key) -> 0L, (ValueType key) -> 0L);
  }

  static <KeyValueType> CacheSizeEvaluator<KeyValueType, KeyValueType> getInstance(
      Function<KeyValueType, Long> keyValueEvaluator) {
    return getInstance(keyValueEvaluator, keyValueEvaluator);
  }

  static <KeyType, ValueType> CacheSizeEvaluator<KeyType, ValueType> getInstance(
      Function<KeyType, Long> keyEvaluator, Function<ValueType, Long> valueEvaluator) {
    return new CacheSizeEvaluatorImpl<>(keyEvaluator, valueEvaluator);
  }
}
