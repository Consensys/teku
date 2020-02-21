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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.ArrayList;
import java.util.List;

@JsonSerialize(as = ArrayList.class)
public class SSZList<T> extends ArrayList<T> {

  private long maxSize;
  private Class<T> classInfo;

  public SSZList(Class<T> classInfo, long maxSize) {
    super();
    this.classInfo = classInfo;
    this.maxSize = maxSize;
  }

  public SSZList(SSZList<T> list) {
    super(list);
    maxSize = list.getMaxSize();
    this.classInfo = list.getElementType();
  }

  public SSZList(List<T> list, long maxSize, Class<T> classInfo) {
    super(list);
    this.maxSize = maxSize;
    this.classInfo = classInfo;
  }

  @Override
  public boolean add(T object) {
    if (super.size() < maxSize) {
      return super.add(object);
    } else {
      return false;
    }
  }

  public long getMaxSize() {
    return maxSize;
  }

  public Class<T> getElementType() {
    return classInfo;
  }
}
