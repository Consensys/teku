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

package tech.pegasys.artemis.util.SSZTypes;

import com.google.common.base.Preconditions;
import java.util.List;

public interface SSZVector<T> extends List<T> {

  static <T> SSZVector<T> create(int size, T object) {
    return new SSZArrayVector<>(size, object);
  }

  static <T> SSZVector<T> create(List<T> list, Class<T> classInfo) {
    return new SSZArrayVector<>(list, classInfo);
  }

  static <T> SSZVector<T> copy(SSZVector<T> vector) {
    return new SSZArrayVector<T>(vector, vector.getElementType());
  }

  Class<T> getElementType();

  default void setAll(SSZVector<T> other) {
    Preconditions.checkArgument(other.size() == size(), "Incompatible vector size");
    for (int i = 0; i < other.size(); i++) {
      set(i, other.get(i));
    }
  }
}
