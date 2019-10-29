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

package org.ethereum.beacon.ssz.creator;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.ethereum.beacon.ssz.SSZSchemeException;
import org.ethereum.beacon.ssz.access.SSZField;
import org.javatuples.Pair;

/**
 * Tries to instantiate object with extraType/Value constructor and set all fields directly or using
 * standard setter
 */
public class SettersExtraObjCreator implements ObjectCreator {
  private final Class extraType;
  private final Object extraValue;

  public SettersExtraObjCreator(Class extraType, Object extraValue) {
    this.extraType = extraType;
    this.extraValue = extraValue;
  }

  /**
   * Creates instance of object using field -> value data
   *
   * @param clazz Object class
   * @param fieldValuePairs Field -> value info
   * @return Pair[success or not, created instance if success or null otherwise]
   */
  @Override
  public <C> C createObject(
      Class<? extends C> clazz, List<Pair<SSZField, Object>> fieldValuePairs) {

    List<SSZField> fields =
        fieldValuePairs.stream().map(Pair::getValue0).collect(Collectors.toList());
    Object[] values = fieldValuePairs.stream().map(Pair::getValue1).toArray();
    // Find constructor with no params
    Constructor<? extends C> constructor;
    try {
      constructor = clazz.getConstructor(extraType);
    } catch (NoSuchMethodException e) {
      return null;
    }

    // Create empty instance
    C result;
    try {
      result = constructor.newInstance(extraValue);
    } catch (Exception e) {
      return null;
    }

    Map<String, Method> fieldSetters = new HashMap<>();
    try {
      for (PropertyDescriptor pd : Introspector.getBeanInfo(clazz).getPropertyDescriptors()) {
        fieldSetters.put(pd.getName(), pd.getWriteMethod());
      }
    } catch (IntrospectionException e) {
      String error = String.format("Couldn't enumerate all setters in class %s", clazz.getName());
      throw new SSZSchemeException(error, e);
    }

    // Fill up field by field
    for (int i = 0; i < fields.size(); ++i) {
      SSZField currentField = fields.get(i);
      try { // Try to set by field assignment
        clazz.getField(currentField.getName()).set(result, values[i]);
      } catch (Exception e) {
        try { // Try to set using setter
          fieldSetters.get(currentField.getName()).invoke(result, values[i]);
        } catch (Exception ex) { // Cannot set the field
          throw new SSZSchemeException(
              String.format("Setter not found for field %s", currentField.getName()), ex);
        }
      }
    }

    return result;
  }
}
