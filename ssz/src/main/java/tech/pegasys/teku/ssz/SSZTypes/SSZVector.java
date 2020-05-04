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

import java.util.List;

public interface SSZVector<T> extends SSZImmutableCollection<T> {

  static <T> SSZMutableVector<T> createMutable(int size, T object) {
    return new SSZArrayCollection<T>(size, object, true);
  }

  static <T> SSZMutableVector<T> createMutable(Class<T> classInfo, int size) {
    return new SSZArrayCollection<T>(classInfo, size, true);
  }

  static <T> SSZMutableVector<T> createMutable(List<T> list, Class<T> classInfo) {
    return new SSZArrayCollection<>(list, list.size(), classInfo, true);
  }

  static <T> SSZVector<T> copy(SSZVector<T> vector) {
    return new SSZArrayCollection<T>(vector.asList(), vector.size(), vector.getElementType(), true);
  }
}
