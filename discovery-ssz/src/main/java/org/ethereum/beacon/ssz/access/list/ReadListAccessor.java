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

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.function.Function;
import org.ethereum.beacon.ssz.SSZSerializeException;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.type.SSZType;
import org.ethereum.beacon.ssz.type.list.SSZListType;
import tech.pegasys.artemis.util.collections.ReadList;
import tech.pegasys.artemis.util.collections.ReadVector;

public class ReadListAccessor extends AbstractListAccessor {

  @Override
  public boolean isSupported(SSZField field) {
    return ReadList.class.isAssignableFrom(field.getRawClass());
  }

  @Override
  public SSZField getListElementType(SSZField listTypeDescriptor) {
    return extractElementType(listTypeDescriptor, 1);
  }

  protected Function<Integer, ? extends Number> resolveIndexConverter(Class<?> indexClass) {
    try {
      Constructor intCtor = indexClass.getConstructor(int.class);
      Function<Integer, ? extends Number> ret =
          i -> {
            try {
              return (Number) intCtor.newInstance(i);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          };
      return ret;
    } catch (NoSuchMethodException e) {
    }
    throw new SSZSerializeException("Index converter not found for " + indexClass);
  }

  @Override
  public int getChildrenCount(Object complexObject) {
    return ((ReadList) complexObject).size().intValue();
  }

  @Override
  public Object getChildValue(Object complexObject, int index) {
    return ((ReadList) complexObject).get(index);
  }

  @Override
  public ListInstanceBuilder createInstanceBuilder(SSZType sszType) {
    return new SimpleInstanceBuilder() {
      @Override
      protected Object buildImpl(List<Object> children) {
        SSZListType listType = (SSZListType) sszType;
        return listType.isVector()
            ? ReadVector.wrap(
                children,
                resolveIndexConverter(
                    (Class<?>)
                        sszType
                            .getTypeDescriptor()
                            .getParametrizedType()
                            .getActualTypeArguments()[0]),
                listType.getVectorLength())
            : ReadList.wrap(
                children,
                resolveIndexConverter(
                    (Class<?>)
                        sszType
                            .getTypeDescriptor()
                            .getParametrizedType()
                            .getActualTypeArguments()[0]),
                listType.getMaxSize());
      }
    };
  }
}
