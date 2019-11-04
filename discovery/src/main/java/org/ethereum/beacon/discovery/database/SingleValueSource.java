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

import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;

/** Created by Anton Nashatyrev on 19.11.2018. */
public interface SingleValueSource<V> {

  Optional<V> get();

  void set(V value);

  void remove();

  static <KeyType, ValueType> SingleValueSource<ValueType> fromDataSource(
      @Nonnull final DataSource<KeyType, ValueType> backDataSource,
      @Nonnull final KeyType valueKey) {
    return fromDataSource(backDataSource, valueKey, Function.identity(), Function.identity());
  }

  static <ValueType, KeyType, SourceValueType> SingleValueSource<ValueType> fromDataSource(
      @Nonnull final DataSource<KeyType, SourceValueType> backDataSource,
      @Nonnull final KeyType valueKey,
      @Nonnull final Function<ValueType, SourceValueType> valueCoder,
      @Nonnull final Function<SourceValueType, ValueType> valueDecoder) {

    final CodecSource.ValueOnly<KeyType, ValueType, SourceValueType> source =
        new CodecSource.ValueOnly<>(backDataSource, valueCoder, valueDecoder);

    return new SingleValueSource<ValueType>() {
      @Override
      public Optional<ValueType> get() {
        return source.get(valueKey);
      }

      @Override
      public void set(final ValueType value) {
        source.put(valueKey, value);
      }

      @Override
      public void remove() {
        source.remove(valueKey);
      }
    };
  }

  static <ValueType> SingleValueSource<ValueType> memSource() {
    return new SingleValueSource<ValueType>() {
      ValueType value;

      @Override
      public Optional<ValueType> get() {
        return Optional.ofNullable(value);
      }

      @Override
      public void set(ValueType value) {
        this.value = value;
      }

      @Override
      public void remove() {
        this.value = null;
      }
    };
  }
}
