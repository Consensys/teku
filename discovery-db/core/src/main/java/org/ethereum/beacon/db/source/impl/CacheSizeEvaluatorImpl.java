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

import java.util.function.Function;
import org.ethereum.beacon.db.source.CacheSizeEvaluator;

/**
 * A straightforward implementation of cache size evaluator.
 *
 * @param <KeyType> a key type.
 * @param <ValueType> a value type.
 */
public class CacheSizeEvaluatorImpl<KeyType, ValueType>
    implements CacheSizeEvaluator<KeyType, ValueType> {

  private final Function<KeyType, Long> keyEvaluator;
  private final Function<ValueType, Long> valueEvaluator;

  private long size;

  public CacheSizeEvaluatorImpl(
      Function<KeyType, Long> keyEvaluator, Function<ValueType, Long> valueEvaluator) {
    this.keyEvaluator = keyEvaluator;
    this.valueEvaluator = valueEvaluator;
  }

  @Override
  public long getEvaluatedSize() {
    return size;
  }

  @Override
  public void reset() {
    size = 0;
  }

  @Override
  public void added(KeyType key, ValueType value) {
    size += keyEvaluator.apply(key);
    size += valueEvaluator.apply(value);
  }

  @Override
  public void removed(KeyType key, ValueType value) {
    size -= keyEvaluator.apply(key);
    size -= valueEvaluator.apply(value);
  }
}
