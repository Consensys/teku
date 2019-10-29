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

package org.ethereum.beacon.ssz.access.list;

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.access.SSZListAccessor;

public abstract class AbstractListAccessor implements SSZListAccessor {

  protected abstract class SimpleInstanceBuilder implements ListInstanceBuilder {
    private List<Object> children = new ArrayList<>();

    @Override
    public void addChild(Object childValue) {
      children.add(childValue);
    }

    @Override
    public void setChild(int idx, Object childValue) {
      if (idx == children.size()) {
        children.add(childValue);
      } else {
        children.set(idx, childValue);
      }
    }

    @Override
    public Object build() {
      return buildImpl(children);
    }

    protected abstract Object buildImpl(List<Object> children);
  }

  static SSZField extractElementType(SSZField listDescriptor, int genericTypeParamIndex) {
    if (listDescriptor.getParametrizedType() == null) {
      return new SSZField(Object.class);
    } else {
      Type listTypeArgument =
          listDescriptor.getParametrizedType().getActualTypeArguments()[genericTypeParamIndex];

      if (listTypeArgument instanceof WildcardType) {
        listTypeArgument = ((WildcardType) listTypeArgument).getLowerBounds()[0];
      }

      return new SSZField(listTypeArgument);
    }
  }
}
