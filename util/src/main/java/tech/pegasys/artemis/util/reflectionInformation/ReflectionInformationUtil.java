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
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

public class ReflectionInformationUtil {
  @SuppressWarnings("rawtypes")
  public static boolean isVariable(ReflectionInformation reflectionInformation)
      throws SecurityException {
    for (Field field : reflectionInformation.getFields()) {
      Class type = field.getType();
      if (type.equals(SSZList.class) || type.equals(Bitlist.class)) {
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
  private static boolean containsClass(Field[] fields, Class classInfo) {
    for (Field field : fields) {
      Class type = field.getType();
      if (type.equals(classInfo)) {
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
      if (containsClass(fields, SSZVector.class)) {
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
    List<Class> vectorElementTypes = new ArrayList<>();
    try {
      Field[] fields = reflectionInformation.getFields();
      if (containsClass(fields, SSZVector.class)) {
        Object object = reflectionInformation.getClassInfo().getConstructor().newInstance();
        List<Field> vectorVariables =
            Arrays.stream(fields)
                .filter(f -> f.getType().equals(SSZVector.class))
                .collect(Collectors.toList());

        vectorVariables.forEach(f -> f.setAccessible(true));
        for (Field vectorVariable : vectorVariables) {
          vectorElementTypes.add(((SSZVector) vectorVariable.get(object)).getElementType());
        }
        vectorVariables.forEach(f -> f.setAccessible(false));
        return vectorElementTypes;
      } else {
        return vectorElementTypes;
      }
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      System.out.println(e);
    }
    return vectorElementTypes;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static List<Class> getListElementTypes(ReflectionInformation reflectionInformation) {
    List<Class> listElementTypes = new ArrayList<>();
    try {
      Field[] fields = reflectionInformation.getFields();
      if (containsClass(fields, SSZList.class)) {
        Object object = reflectionInformation.getClassInfo().getConstructor().newInstance();
        List<Field> listVariables =
            Arrays.stream(fields)
                .filter(f -> f.getType().equals(SSZList.class))
                .collect(Collectors.toList());

        listVariables.forEach(f -> f.setAccessible(true));
        for (Field listVariable : listVariables) {
          listElementTypes.add(((SSZList) listVariable.get(object)).getElementType());
        }
        listVariables.forEach(f -> f.setAccessible(false));
        return listElementTypes;
      } else {
        return listElementTypes;
      }
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      System.out.println(e);
    }
    return listElementTypes;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static List<Long> getListElementMaxSizes(ReflectionInformation reflectionInformation) {
    List<Long> listElementMaxSizes = new ArrayList<>();
    try {
      Field[] fields = reflectionInformation.getFields();
      if (containsClass(fields, SSZList.class)) {
        Object object = reflectionInformation.getClassInfo().getConstructor().newInstance();
        List<Field> listVariables =
            Arrays.stream(fields)
                .filter(f -> f.getType().equals(SSZList.class))
                .collect(Collectors.toList());

        listVariables.forEach(f -> f.setAccessible(true));
        for (Field listVariable : listVariables) {
          listElementMaxSizes.add(((SSZList) listVariable.get(object)).getMaxSize());
        }
        listVariables.forEach(f -> f.setAccessible(false));
        return listElementMaxSizes;
      } else {
        return listElementMaxSizes;
      }
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      System.out.println(e);
    }
    return listElementMaxSizes;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static List<Long> getBitlistElementMaxSizes(ReflectionInformation reflectionInformation) {
    List<Long> bitlistElementsMaxSizes = new ArrayList<>();
    try {
      Field[] fields = reflectionInformation.getFields();
      if (containsClass(fields, Bitlist.class)) {
        Object object = reflectionInformation.getClassInfo().getConstructor().newInstance();
        List<Field> listVariables =
            Arrays.stream(fields)
                .filter(f -> f.getType().equals(Bitlist.class))
                .collect(Collectors.toList());

        listVariables.forEach(f -> f.setAccessible(true));
        for (Field listVariable : listVariables) {
          bitlistElementsMaxSizes.add(((Bitlist) listVariable.get(object)).getMaxSize());
        }
        listVariables.forEach(f -> f.setAccessible(false));
        return bitlistElementsMaxSizes;
      } else {
        return bitlistElementsMaxSizes;
      }
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      System.out.println(e);
    }
    return bitlistElementsMaxSizes;
  }

  @SuppressWarnings("unchecked")
  public static List<Integer> getBitvectorSizes(ReflectionInformation reflectionInformation) {
    List<Integer> vectorLengths = new ArrayList<>();
    try {
      Field[] fields = reflectionInformation.getFields();
      if (containsClass(fields, Bitvector.class)) {
        Object object = reflectionInformation.getClassInfo().getConstructor().newInstance();
        List<Field> vectorVariables =
            Arrays.stream(fields)
                .filter(f -> f.getType().equals(Bitvector.class))
                .collect(Collectors.toList());

        vectorVariables.forEach(f -> f.setAccessible(true));
        for (Field vectorVariable : vectorVariables) {
          vectorLengths.add(((Bitvector) vectorVariable.get(object)).getSize());
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
