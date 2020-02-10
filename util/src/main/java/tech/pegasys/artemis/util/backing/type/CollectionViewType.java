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

package tech.pegasys.artemis.util.backing.type;

import java.util.Objects;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewType;

public abstract class CollectionViewType<C extends ViewRead> implements CompositeViewType {

  private final long maxLength;
  private final ViewType elementType;

  CollectionViewType(long maxLength, ViewType elementType) {
    this.maxLength = maxLength;
    this.elementType = elementType;
  }

  @Override
  public long getMaxLength() {
    return maxLength;
  }

  public ViewType getElementType() {
    return elementType;
  }

  @Override
  public ViewType getChildType(int index) {
    return getElementType();
  }

  @Override
  public int getBitsPerElement() {
    return getElementType().getBitsSize();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CollectionViewType<?> that = (CollectionViewType<?>) o;
    return maxLength == that.maxLength && elementType.equals(that.elementType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxLength, elementType);
  }
}
