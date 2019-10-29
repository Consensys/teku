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

package org.ethereum.beacon.ssz.type;

import java.util.List;
import java.util.stream.Collectors;
import org.ethereum.beacon.ssz.SSZSchemeException;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.access.SSZUnionAccessor;
import tech.pegasys.artemis.util.collections.Union;

public class SSZUnionType implements SSZHeteroCompositeType {

  private final SSZUnionAccessor accessor;
  private final SSZField descriptor;
  private final TypeResolver typeResolver;

  private List<SSZType> childTypes;

  public SSZUnionType(SSZUnionAccessor accessor, SSZField descriptor, TypeResolver typeResolver) {
    this.accessor = accessor;
    this.descriptor = descriptor;
    this.typeResolver = typeResolver;
  }

  @Override
  public Type getType() {
    return Type.UNION;
  }

  @Override
  public int getSize() {
    return VARIABLE_SIZE;
  }

  @Override
  public SSZUnionAccessor getAccessor() {
    return accessor;
  }

  @Override
  public SSZField getTypeDescriptor() {
    return descriptor;
  }

  @Override
  public List<SSZType> getChildTypes() {
    if (childTypes == null) {
      List<SSZField> sszFields =
          accessor.getInstanceAccessor(getTypeDescriptor()).getChildDescriptors();
      if (sszFields.isEmpty()) {
        throw new SSZSchemeException("No Union members found: " + this.getTypeDescriptor());
      }
      for (int i = 1; i < sszFields.size(); i++) {
        if (sszFields.get(i).getRawClass() == Union.Null.class) {
          throw new SSZSchemeException("Union Null should be the only Null member at index 0");
        }
      }
      childTypes =
          sszFields.stream().map(typeResolver::resolveSSZType).collect(Collectors.toList());
    }
    return childTypes;
  }

  public boolean isNullable() {
    return getChildTypes().get(0).getTypeDescriptor().getRawClass() == Union.Null.class;
  }
}
