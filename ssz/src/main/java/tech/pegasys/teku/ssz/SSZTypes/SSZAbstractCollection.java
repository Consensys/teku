/*
 * Copyright 2020 ConsenSys AG.
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

import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class SSZAbstractCollection<C> implements SSZMutableCollection<C> {

  protected final Class<? extends C> classInfo;

  protected SSZAbstractCollection(Class<? extends C> classInfo) {
    this.classInfo = classInfo;
  }

  @Override
  public Class<? extends C> getElementType() {
    return classInfo;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Iterable) {
      Iterator<?> thisIt = iterator();
      Iterator<?> thatIt = ((Iterable) obj).iterator();
      while (thisIt.hasNext()) {
        if (!thatIt.hasNext() || !thisIt.next().equals(thatIt.next())) {
          return false;
        }
      }
      return !thatIt.hasNext();
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(getElementType(), getMaxSize(), size());
  }

  @Override
  public String toString() {
    return "[" + stream().map(Object::toString).collect(Collectors.joining(", ")) + "]";
  }
}
