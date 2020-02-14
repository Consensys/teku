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

import java.util.List;

public interface SSZList<T> extends List<T> {

  static <T> SSZListWrite<T> create(Class<T> classInfo, long maxSize) {
    return new SSZArrayList<>(classInfo, maxSize);
  }

  static <T> SSZListWrite<T> create(List<T> list, long maxSize, Class<T> classInfo) {
    return new SSZArrayList<>(list, maxSize, classInfo);
  }

  static <T> SSZListWrite<T> create(SSZList<T> list) {
    return new SSZArrayList<T>(list, list.getMaxSize(), list.getElementType());
  }

  long getMaxSize();

  Class<? extends T> getElementType();

  default void setAll(SSZList<? extends T> other) {
    clear();
    addAll(other);
  }
}
