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

import static java.util.stream.Collectors.toList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("rawtypes")
public class ReflectionInformation {

  private Class classInfo;
  private Field[] fields;
  private Class[] paramClasses;
  private Constructor constructor;
  private int parameterCount;
  private boolean isVariable;
  private List<Integer> vectorLengths;

  private List<Class> vectorElementTypes;

  @SuppressWarnings("unchecked")
  public ReflectionInformation(Class classInfo) {
    try {
      this.classInfo = classInfo;
      this.fields = getFields(classInfo);
      this.paramClasses = getTypes(fields);
      this.constructor = classInfo.getConstructor(paramClasses);
      this.parameterCount = constructor.getParameterCount();
      this.isVariable = ReflectionInformationUtil.isVariable(this);
      this.vectorLengths = ReflectionInformationUtil.getVectorLengths(this);
      this.vectorElementTypes = ReflectionInformationUtil.getVectorElementTypes(this);
    } catch (NoSuchMethodException e) {
      System.out.println(e);
    }
  }

  public Class getClassInfo() {
    return classInfo;
  }

  public Field[] getFields() {
    return fields;
  }

  public Constructor getConstructor() {
    return constructor;
  }

  public int getParameterCount() {
    return parameterCount;
  }

  public boolean isVariable() {
    return isVariable;
  }

  public List<Integer> getVectorLengths() {
    return vectorLengths;
  }

  public List<Class> getVectorElementTypes() {
    return vectorElementTypes;
  }

  private Field[] getFields(Class classInfo) {
    return Arrays.stream(classInfo.getDeclaredFields())
        .filter(f -> !Modifier.isStatic(f.getModifiers()))
        .collect(toList())
        .toArray(new Field[0]);
  }

  private Class[] getTypes(Field[] fields) {
    return Arrays.stream(fields).map(f -> f.getType()).collect(toList()).toArray(new Class[0]);
  }
}
