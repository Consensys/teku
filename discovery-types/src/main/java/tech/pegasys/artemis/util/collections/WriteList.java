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
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

public interface WriteList<IndexType extends Number, ValueType>
    extends WriteVector<IndexType, ValueType> {

  static <IndexType extends Number, ValueType> WriteList<IndexType, ValueType> wrap(
      List<ValueType> srcList, Function<Integer, IndexType> indexConverter) {
    return WriteList.wrap(srcList, indexConverter, false);
  }

  static <IndexType extends Number, ValueType> WriteList<IndexType, ValueType> create(
      Function<Integer, IndexType> indexConverter) {
    return WriteList.create(indexConverter, false, VARIABLE_SIZE);
  }

  static <IndexType extends Number, ValueType> WriteList<IndexType, ValueType> create(
      Function<Integer, IndexType> indexConverter, long maxSize) {
    return WriteList.create(indexConverter, false, maxSize);
  }

  static <IndexType extends Number, ValueType> WriteList<IndexType, ValueType> create(
      Function<Integer, IndexType> indexConverter, boolean vector) {
    return WriteList.create(indexConverter, vector, VARIABLE_SIZE);
  }

  static <IndexType extends Number, ValueType> WriteList<IndexType, ValueType> wrap(
      List<ValueType> srcList, Function<Integer, IndexType> indexConverter, boolean vector) {
    return ListImpl.wrap(srcList, indexConverter, vector);
  }

  static <IndexType extends Number, ValueType> WriteList<IndexType, ValueType> create(
      Function<Integer, IndexType> indexConverter, boolean vector, long maxSize) {
    return new ListImpl<>(indexConverter, vector, maxSize);
  }

  boolean add(ValueType valueType);

  boolean remove(ValueType o);

  boolean addAll(@NotNull Iterable<? extends ValueType> c);

  boolean addAll(IndexType index, @NotNull Iterable<? extends ValueType> c);

  default void replaceAll(@NotNull Iterable<? extends ValueType> c) {
    this.clear();
    this.addAll(c);
  }

  void sort(Comparator<? super ValueType> c);

  void clear();

  ValueType set(IndexType index, ValueType element);

  void add(IndexType index, ValueType element);

  ValueType remove(IndexType index);

  void retainAll(ReadList<IndexType, ValueType> other);

  ReadList<IndexType, ValueType> createImmutableCopy();

  default ValueType update(IndexType index, Function<ValueType, ValueType> updater) {
    ValueType newValue = updater.apply(get(index));
    set(index, newValue);
    return newValue;
  }

  default void remove(Predicate<ValueType> removeFilter) {
    List<ValueType> copy = listCopy();
    clear();
    for (ValueType val : copy) {
      if (!removeFilter.test(val)) {
        add(val);
      }
    }
  }
}
