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

package tech.pegasys.artemis.util.collections;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public interface WriteVector<IndexType extends Number, ValueType>
    extends ReadVector<IndexType, ValueType> {

  static <IndexType extends Number, ValueType> WriteVector<IndexType, ValueType> wrap(
      List<ValueType> srcList, Function<Integer, IndexType> indexConverter) {
    return ListImpl.wrap(srcList, indexConverter, true);
  }

  void sort(Comparator<? super ValueType> c);

  ValueType set(IndexType index, ValueType element);

  void setAll(ValueType singleValue);

  void setAll(Iterable<ValueType> singleValue);

  ReadList<IndexType, ValueType> createImmutableCopy();

  default ValueType update(IndexType index, Function<ValueType, ValueType> updater) {
    ValueType newValue = updater.apply(get(index));
    set(index, newValue);
    return newValue;
  }
}
