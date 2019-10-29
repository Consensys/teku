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

import javax.annotation.Nullable;

public class UnionImpl implements MutableUnion {

  private int typeIndex = 0;
  private Object value;

  @Override
  public <C> void setValue(int index, @Nullable C value) {
    typeIndex = index;
    this.value = value;
  }

  @Override
  public int getTypeIndex() {
    return typeIndex;
  }

  @Nullable
  @Override
  public <C> C getValue() {
    return (C) value;
  }

  @Override
  public String toString() {
    return "UnionImpl{" + "typeIndex=" + typeIndex + ", value=" + value + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    UnionImpl union = (UnionImpl) o;

    if (typeIndex != union.typeIndex) {
      return false;
    }
    return value != null ? value.equals(union.value) : union.value == null;
  }

  @Override
  public int hashCode() {
    int result = typeIndex;
    result = 31 * result + (value != null ? value.hashCode() : 0);
    return result;
  }
}
