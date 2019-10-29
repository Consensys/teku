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

import java.util.List;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.type.SSZType;

public class ListAccessor extends AbstractListAccessor {

  @Override
  public boolean isSupported(SSZField field) {
    return List.class.isAssignableFrom(field.getRawClass());
  }

  @Override
  public SSZField getListElementType(SSZField listTypeDescriptor) {
    return extractElementType(listTypeDescriptor, 0);
  }

  @Override
  public int getChildrenCount(Object complexObject) {
    return ((List) complexObject).size();
  }

  @Override
  public Object getChildValue(Object complexObject, int index) {
    return ((List) complexObject).get((int) index);
  }

  @Override
  public ListInstanceBuilder createInstanceBuilder(SSZType sszType) {
    return new SimpleInstanceBuilder() {
      @Override
      protected Object buildImpl(List<Object> children) {
        return children;
      }
    };
  }
}
