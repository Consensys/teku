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

package tech.pegasys.teku.ssz.SSZTypes;

import com.google.common.base.Preconditions;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface SSZList<T> extends SSZImmutableCollection<T> {

  static <T> SSZMutableList<T> createMutable(Class<? extends T> classInfo, long maxSize) {
    return new SSZArrayCollection<>(classInfo, maxSize, false);
  }

  static <T> SSZMutableList<T> createMutable(
      Stream<T> list, long maxSize, Class<? extends T> classInfo) {
    return new SSZArrayCollection<>(list.collect(Collectors.toList()), maxSize, classInfo, false);
  }

  static <T> SSZMutableList<T> createMutable(
      Collection<T> list, long maxSize, Class<? extends T> classInfo) {
    return createMutable(list.stream(), maxSize, classInfo);
  }

  static <T> SSZMutableList<T> createMutable(SSZImmutableCollection<? extends T> list) {
    return new SSZArrayCollection<>(list.asList(), list.getMaxSize(), list.getElementType(), false);
  }

  static <T> SSZMutableList<T> concat(
      SSZImmutableCollection<? extends T> left, SSZImmutableCollection<? extends T> right) {
    Preconditions.checkArgument(
        left.getElementType().equals(right.getElementType()),
        "Incompatible list types: %s != %s",
        left.getElementType(),
        right.getElementType());
    SSZMutableList<T> ret =
        createMutable(left.getElementType(), left.getMaxSize() + right.getMaxSize());
    ret.addAll(left);
    ret.addAll(right);
    return ret;
  }

  static <T> SSZList<T> singleton(T obj) {
    return new SSZArrayCollection<>(1, obj, false);
  }

  static <T> SSZList<T> empty(Class<T> clazz) {
    return new SSZArrayCollection<>(clazz, 0, false);
  }

  default SSZList<T> reversed() {
    SSZMutableList<T> ret = createMutable(getElementType(), getMaxSize());
    for (int i = size() - 1; i >= 0; i--) {
      ret.add(get(i));
    }
    return ret;
  }

  default SSZList<T> modified(Function<Stream<T>, Stream<T>> streamer) {
    return modified(getElementType(), streamer);
  }

  default <D> SSZList<D> modified(
      Class<? extends D> newElementType, Function<Stream<T>, Stream<D>> streamer) {
    return createMutable(streamer.apply(stream()), getMaxSize(), newElementType);
  }

  default <D> SSZList<D> map(Class<? extends D> newElementType, Function<T, D> streamer) {
    return modified(newElementType, steam -> stream().map(streamer));
  }

  default SSZList<T> filter(Predicate<T> filter) {
    return modified(steam -> stream().filter(filter));
  }

  @SuppressWarnings("unchecked")
  default T[] toArray() {
    return asList().toArray((T[]) Array.newInstance(getElementType(), 0));
  }
}
