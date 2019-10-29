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

import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.access.SSZListAccessor;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.ssz.creator.ConstructorObjCreator;
import org.ethereum.beacon.ssz.type.SSZType;

public class SubclassListAccessor implements SSZListAccessor {
  private final SSZListAccessor superclassAccessor;

  public SubclassListAccessor(SSZListAccessor superclassAccessor) {
    this.superclassAccessor = superclassAccessor;
  }

  @Override
  public int getChildrenCount(Object value) {
    return superclassAccessor.getChildrenCount(value);
  }

  @Override
  public Object getChildValue(Object value, int idx) {
    return superclassAccessor.getChildValue(value, idx);
  }

  @Override
  public SSZField getListElementType(SSZField listTypeDescriptor) {
    return superclassAccessor.getListElementType(
        new SSZField(getSerializableClass(listTypeDescriptor.getRawClass())));
  }

  @Override
  public ListInstanceBuilder createInstanceBuilder(SSZType sszType) {
    SSZField listType = sszType.getTypeDescriptor();
    ListInstanceBuilder instanceBuilder = superclassAccessor.createInstanceBuilder(sszType);
    return new ListInstanceBuilder() {
      @Override
      public void addChild(Object childValue) {
        instanceBuilder.addChild(childValue);
      }

      @Override
      public void setChild(int idx, Object childValue) {
        instanceBuilder.setChild(idx, childValue);
      }

      @Override
      public Object build() {
        Object superclassInstance = instanceBuilder.build();
        SSZField serializableField = getSerializableField(listType);
        return ConstructorObjCreator.createInstanceWithConstructor(
            listType.getRawClass(),
            new Class[] {serializableField.getRawClass()},
            new Object[] {superclassInstance});
      }
    };
  }

  public static SSZField getSerializableField(SSZField field) {
    return new SSZField(
        getSerializableClass(field.getRawClass()),
        field.getFieldAnnotation(),
        field.getExtraType(),
        field.getExtraSize(),
        field.getName(),
        field.getGetter());
  }

  @Override
  public CompositeInstanceAccessor getInstanceAccessor(SSZField compositeDescriptor) {
    return superclassAccessor.getInstanceAccessor(compositeDescriptor);
  }

  @Override
  public boolean isSupported(SSZField field) {
    return superclassAccessor.isSupported(field);
  }

  /**
   * If the field class specifies {@link SSZSerializable#serializeAs()} attribute returns the
   * specified class. Else returns type value.
   */
  public static Class<?> getSerializableClass(Class<?> type) {
    SSZSerializable fieldClassAnnotation = type.getAnnotation(SSZSerializable.class);
    if (fieldClassAnnotation != null && fieldClassAnnotation.serializeAs() != void.class) {
      // the class of the field wants to be serialized as another class
      return fieldClassAnnotation.serializeAs();
    } else {
      return type;
    }
  }
}
