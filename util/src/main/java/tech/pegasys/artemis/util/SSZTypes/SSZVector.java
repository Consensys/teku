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

@SuppressWarnings("rawtypes")
public class SSZVector<T> extends ArrayList<T> {

  private int size;
  private int counter = 0;
  private Class classInfo;

  public SSZVector() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("SSZVector must have specified size");
  }

  public SSZVector(int length, T object) {
    super(Collections.nCopies(length, object));
    size = length;
    classInfo = object.getClass();
  }

  public SSZVector(List<T> list, Class classInfo) {
    super(list);
    size = list.size();
    this.classInfo = classInfo;
  }

  public SSZVector(SSZVector<T> list) {
    super(list);
    size = list.size();
    this.classInfo = list.getElementType();
  }

  public int getSize() {
    return size;
  }

  public int getCounter() {
    return size;
  }

  @Override
  public boolean add(T object) {
    if (counter < size) {
      super.set(counter, object);
      counter++;
      return true;
    } else {
      return false;
    }
  }

  public Class getElementType() {
    return classInfo;
  }
}
