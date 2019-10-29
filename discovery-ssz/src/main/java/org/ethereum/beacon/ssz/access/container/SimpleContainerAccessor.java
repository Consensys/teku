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

package org.ethereum.beacon.ssz.access.container;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ethereum.beacon.ssz.SSZSchemeException;
import org.ethereum.beacon.ssz.SSZSerializeException;
import org.ethereum.beacon.ssz.access.SSZContainerAccessor;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.access.container.SSZSchemeBuilder.SSZScheme;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.ssz.creator.ObjectCreator;
import org.ethereum.beacon.ssz.type.SSZType;
import org.javatuples.Pair;

public class SimpleContainerAccessor implements SSZContainerAccessor {

  protected class BasicInstanceAccessor implements ContainerInstanceAccessor {
    private final SSZField containerDescriptor;
    private final SSZScheme scheme;
    private final Map<String, Method> getters;

    public BasicInstanceAccessor(SSZField containerDescriptor) {
      this.containerDescriptor = containerDescriptor;
      scheme = sszSchemeBuilder.build(containerDescriptor.getRawClass());
      getters = new HashMap<>();
      try {
        for (PropertyDescriptor pd :
            Introspector.getBeanInfo(containerDescriptor.getRawClass()).getPropertyDescriptors()) {
          if (pd.getReadMethod() != null) {
            getters.put(pd.getReadMethod().getName(), pd.getReadMethod());
          }
        }
      } catch (IntrospectionException e) {
        throw new RuntimeException(
            String.format(
                "Couldn't enumerate all getters in class %s",
                containerDescriptor.getRawClass().getName()),
            e);
      }
    }

    @Override
    public List<SSZField> getChildDescriptors() {
      return scheme.getFields();
    }

    protected Object getContainerInstance(Object value) {
      return value;
    }

    @Override
    public Object getChildValue(Object containerInstance, int childIndex) {
      SSZField field = getChildDescriptors().get(childIndex);
      Method getter = getters.get(field.getGetter());
      try {
        if (getter != null) { // We have getter
          return getter.invoke(getContainerInstance(containerInstance));
        } else { // Trying to access field directly
          return containerDescriptor
              .getRawClass()
              .getField(field.getName())
              .get(getContainerInstance(containerInstance));
        }
      } catch (Exception e) {
        throw new SSZSchemeException(
            String.format(
                "Failed to get value from field %s, "
                    + "you should either have public field or public getter for it",
                field.getName()),
            e);
      }
    }
  }

  protected class BasicInstanceBuilder implements CompositeInstanceBuilder {
    private final SSZField containerDescriptor;
    private final Map<SSZField, Object> children = new LinkedHashMap<>();
    private final List<SSZField> childDescriptors;

    public BasicInstanceBuilder(SSZField containerDescriptor) {
      this.containerDescriptor = containerDescriptor;
      childDescriptors = getInstanceAccessor(containerDescriptor).getChildDescriptors();
    }

    @Override
    public void setChild(int idx, Object childValue) {
      children.put(childDescriptors.get(idx), childValue);
    }

    @Override
    public Object build() {
      List<Pair<SSZField, Object>> values = new ArrayList<>();
      for (SSZField childDescriptor : childDescriptors) {
        Object value = children.get(childDescriptor);
        if (value == null) {
          throw new SSZSerializeException(
              "Can't create "
                  + containerDescriptor
                  + " container instance, missing field "
                  + childDescriptor);
        }
        values.add(Pair.with(childDescriptor, value));
      }
      return objectCreator.createObject(containerDescriptor.getRawClass(), values);
    }
  }

  private final SSZSchemeBuilder sszSchemeBuilder;
  private final ObjectCreator objectCreator;

  public SimpleContainerAccessor(SSZSchemeBuilder sszSchemeBuilder, ObjectCreator objectCreator) {
    this.sszSchemeBuilder = sszSchemeBuilder;
    this.objectCreator = objectCreator;
  }

  @Override
  public boolean isSupported(SSZField containerDescriptor) {
    if (!containerDescriptor.getRawClass().isAnnotationPresent(SSZSerializable.class)) {
      return false;
    }
    if (getInstanceAccessor(containerDescriptor).getChildDescriptors().isEmpty()) {
      return false;
    }
    return true;
  }

  @Override
  public ContainerInstanceAccessor getInstanceAccessor(SSZField containerDescriptor) {
    return new BasicInstanceAccessor(containerDescriptor);
  }

  @Override
  public CompositeInstanceBuilder createInstanceBuilder(SSZType sszType) {
    return new BasicInstanceBuilder(sszType.getTypeDescriptor());
  }
}
