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

package org.ethereum.beacon.ssz.access.union;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.access.SSZUnionAccessor;
import org.ethereum.beacon.ssz.type.SSZType;
import tech.pegasys.artemis.util.collections.GenericUnionImpl;
import tech.pegasys.artemis.util.collections.MutableUnion;
import tech.pegasys.artemis.util.collections.Union;

/**
 * Gathers information about Union member types from the generic types arguments see {@link
 * MutableUnion.U2}, {@link MutableUnion.U3}, etc. Also see javadoc at {@link Union}
 */
public class GenericTypeSSZUnionAccessor implements SSZUnionAccessor {

  class GenericUnionInstanceInstanceAccessor implements UnionInstanceAccessor {
    private final List<SSZField> descriptors;

    public GenericUnionInstanceInstanceAccessor(SSZField unionDescriptor) {
      descriptors = getChildDescriptorsFromGenericType(unionDescriptor);
    }

    @Override
    public List<SSZField> getChildDescriptors() {
      return descriptors;
    }

    @Override
    public Object getChildValue(Object compositeInstance, int childIndex) {
      return ((Union) compositeInstance).getValue();
    }

    @Override
    public int getTypeIndex(Object unionInstance) {
      return ((Union) unionInstance).getTypeIndex();
    }
  }

  @Override
  public boolean isSupported(SSZField field) {
    return Union.GenericTypedUnion.class.isAssignableFrom(field.getRawClass());
  }

  @Override
  public CompositeInstanceBuilder createInstanceBuilder(SSZType sszType) {
    return new CompositeInstanceBuilder() {
      private MutableUnion union;

      @Override
      public void setChild(int idx, Object childValue) {
        union = new GenericUnionImpl();
        union.setValue(idx, childValue);
      }

      @Override
      public Object build() {
        return union;
      }
    };
  }

  @Override
  public UnionInstanceAccessor getInstanceAccessor(SSZField compositeDescriptor) {
    return new GenericUnionInstanceInstanceAccessor(compositeDescriptor);
  }

  private List<SSZField> getChildDescriptorsFromGenericType(SSZField unionDescriptor) {
    if (!isSupported(unionDescriptor)) {
      throw new IllegalArgumentException("Unknown union class: " + unionDescriptor);
    }
    Type[] typeArguments = unionDescriptor.getParametrizedType().getActualTypeArguments();
    return Arrays.stream(typeArguments).map(SSZField::new).collect(Collectors.toList());
  }
}
