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

import java.lang.reflect.Constructor;
import java.util.List;
import org.ethereum.beacon.ssz.access.SSZField;
import org.javatuples.Pair;

/**
 * Tries to create object instance by one constructor with all input fields and added extraValue to
 * the end
 */
public class ConstructorExtraObjCreator implements ObjectCreator {
  private final Class extraType;
  private final Object extraValue;

  public ConstructorExtraObjCreator(Class extraType, Object extraValue) {
    this.extraType = extraType;
    this.extraValue = extraValue;
  }

  public <C> C createInstanceWithConstructor(
      Class<? extends C> clazz, Class[] params, Object[] values) {
    // Find constructor for params
    Constructor<? extends C> constructor;
    try {
      Class[] merged = new Class[params.length + 1];
      System.arraycopy(params, 0, merged, 0, params.length);
      merged[params.length] = extraType;
      constructor = clazz.getConstructor(merged);
    } catch (NoSuchMethodException e) {
      return null;
    }

    // Invoke constructor using values as params
    C result;
    try {
      Object[] merged = new Object[values.length + 1];
      System.arraycopy(values, 0, merged, 0, values.length);
      merged[values.length] = extraValue;
      result = constructor.newInstance(merged);
    } catch (Exception e) {
      return null;
    }

    return result;
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
    Class[] params = new Class[fieldValuePairs.size()];
    for (int i = 0; i < fieldValuePairs.size(); i++) {
      Pair<SSZField, Object> pair = fieldValuePairs.get(i);
      SSZField field = pair.getValue0();
      params[i] = field.getRawClass();
    }
    Object[] values = fieldValuePairs.stream().map(Pair::getValue1).toArray();

    return createInstanceWithConstructor(clazz, params, values);
  }
}
