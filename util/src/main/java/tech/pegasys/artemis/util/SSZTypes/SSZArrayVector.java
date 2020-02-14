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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;

class SSZArrayVector<T> extends ArrayList<T> implements SSZVectorWrite<T> {

  private final Class<T> classInfo;

  @SuppressWarnings("unchecked")
  SSZArrayVector(int size, T object) {
    super(Collections.nCopies(size, object));
    classInfo = (Class<T>) object.getClass();
  }

  SSZArrayVector(List<T> list, Class<T> classInfo) {
    super(list);
    this.classInfo = classInfo;
  }

  SSZArrayVector(SSZArrayVector<T> list) {
    super(list);
    this.classInfo = list.getElementType();
  }

  public Class<T> getElementType() {
    return classInfo;
  }

  @Override
  public Bytes32 hash_tree_root() {
    throw new UnsupportedOperationException();
  }
}
