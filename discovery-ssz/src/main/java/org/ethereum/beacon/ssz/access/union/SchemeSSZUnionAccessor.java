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

import java.util.List;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.access.SSZUnionAccessor;
import org.ethereum.beacon.ssz.access.container.SSZSchemeBuilder;
import org.ethereum.beacon.ssz.access.container.SSZSchemeBuilder.SSZScheme;
import org.ethereum.beacon.ssz.type.SSZType;
import tech.pegasys.artemis.util.collections.MutableUnion;
import tech.pegasys.artemis.util.collections.Union;

/**
 * Gathers information about Union member types from declared @{@link
 * org.ethereum.beacon.ssz.annotation.SSZ} fields of the {@link Union} subclass like it is done with
 * regular SSZ containers See javadoc at {@link Union} for more details
 */
public class SchemeSSZUnionAccessor implements SSZUnionAccessor {

  class SchemeUnionInstanceInstanceAccessor implements UnionInstanceAccessor {
    private final SSZScheme scheme;

    public SchemeUnionInstanceInstanceAccessor(SSZField unionDescriptor) {
      scheme = sszSchemeBuilder.build(unionDescriptor.getRawClass());
    }

    @Override
    public List<SSZField> getChildDescriptors() {
      return scheme.getFields();
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

  private final SSZSchemeBuilder sszSchemeBuilder;

  public SchemeSSZUnionAccessor(SSZSchemeBuilder sszSchemeBuilder) {
    this.sszSchemeBuilder = sszSchemeBuilder;
  }

  @Override
  public boolean isSupported(SSZField field) {
    return Union.class.isAssignableFrom(field.getRawClass());
  }

  @Override
  public CompositeInstanceBuilder createInstanceBuilder(SSZType sszType) {
    return new CompositeInstanceBuilder() {
      private MutableUnion union;

      @Override
      public void setChild(int idx, Object childValue) {
        try {
          SSZField compositeDescriptor = sszType.getTypeDescriptor();
          union = (MutableUnion) compositeDescriptor.getRawClass().newInstance();
          union.setValue(idx, childValue);
        } catch (InstantiationException | IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public Object build() {
        return union;
      }
    };
  }

  @Override
  public UnionInstanceAccessor getInstanceAccessor(SSZField compositeDescriptor) {
    return new SchemeUnionInstanceInstanceAccessor(compositeDescriptor);
  }
}
