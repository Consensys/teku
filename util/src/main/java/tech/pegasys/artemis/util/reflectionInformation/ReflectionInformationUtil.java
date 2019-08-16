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

package tech.pegasys.artemis.util.reflectionInformation;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

public class ReflectionInformationUtil {
  @SuppressWarnings("rawtypes")
  public static boolean isVariable(ReflectionInformation reflectionInformation)
      throws SecurityException {
    for (Field field : reflectionInformation.getFields()) {
      Class type = field.getType();
      if (type.equals(List.class)) {
        return true;
      }
      if (SSZContainer.class.isAssignableFrom(type)) {
        if (isVariable(new ReflectionInformation(type))) {
          return true;
        }
      }
    }
    return false;
  }

  @SuppressWarnings("rawtypes")
  private static boolean containsVector(Field[] fields) {
    for (Field field : fields) {
      Class type = field.getType();
      if (type.equals(SSZVector.class)) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  public static List<Integer> getVectorLengths(ReflectionInformation reflectionInformation) {
    List<Integer> vectorLengths = new ArrayList<>();
    try {
      Field[] fields = reflectionInformation.getFields();
      if (containsVector(fields)) {
        Object object = reflectionInformation.getClassInfo().getConstructor().newInstance();
        List<Field> vectorVariables =
            Arrays.stream(fields)
                .filter(f -> f.getType().equals(SSZVector.class))
                .collect(Collectors.toList());

        vectorVariables.forEach(f -> f.setAccessible(true));
        for (Field vectorVariable : vectorVariables) {
          vectorLengths.add(((SSZVector) vectorVariable.get(object)).size());
        }
        vectorVariables.forEach(f -> f.setAccessible(false));
        return vectorLengths;
      } else {
        return vectorLengths;
      }
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      System.out.println(e);
    }
    return vectorLengths;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static List<Class> getVectorElementTypes(ReflectionInformation reflectionInformation) {
    List<Class> vectorLengths = new ArrayList<>();
    try {
      Field[] fields = reflectionInformation.getFields();
      if (containsVector(fields)) {
        Object object = reflectionInformation.getClassInfo().getConstructor().newInstance();
        List<Field> vectorVariables =
            Arrays.stream(fields)
                .filter(f -> f.getType().equals(SSZVector.class))
                .collect(Collectors.toList());

        vectorVariables.forEach(f -> f.setAccessible(true));
        for (Field vectorVariable : vectorVariables) {
          vectorLengths.add(((SSZVector) vectorVariable.get(object)).getElementType());
        }
        vectorVariables.forEach(f -> f.setAccessible(false));
        return vectorLengths;
      } else {
        return vectorLengths;
      }
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      System.out.println(e);
    }
    return vectorLengths;
  }
}
