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

import java.lang.reflect.Array;
import java.util.List;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.type.SSZType;

public class ArrayAccessor extends AbstractListAccessor {

  @Override
  public boolean isSupported(SSZField field) {
    return field.getRawClass().isArray();
  }

  @Override
  public int getChildrenCount(Object complexObject) {
    return Array.getLength(complexObject);
  }

  @Override
  public Object getChildValue(Object complexObject, int index) {
    return Array.get(complexObject, index);
  }

  @Override
  public SSZField getListElementType(SSZField listTypeDescriptor) {
    return new SSZField(listTypeDescriptor.getRawClass().getComponentType());
  }

  @Override
  public ListInstanceBuilder createInstanceBuilder(SSZType sszType) {
    SSZField compositeDescriptor = sszType.getTypeDescriptor();
    return new SimpleInstanceBuilder() {
      @Override
      protected Object buildImpl(List<Object> children) {
        if (!getListElementType(compositeDescriptor).getRawClass().isPrimitive()) {
          return children.toArray(
              (Object[])
                  Array.newInstance(
                      getListElementType(compositeDescriptor).getRawClass(), children.size()));
        } else {
          if (getListElementType(compositeDescriptor).getRawClass() == byte.class) {
            Object ret = Array.newInstance(byte.class);
            for (int i = 0; i < children.size(); i++) {
              Array.setByte(ret, i, (Byte) children.get(i));
            }
            return ret;
          } else {
            throw new UnsupportedOperationException("Not implemented yet");
          }
        }
      }
    };
  }
}
