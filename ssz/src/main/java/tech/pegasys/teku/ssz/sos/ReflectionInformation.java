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

package tech.pegasys.teku.ssz.sos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import jdk.jfr.Label;

@SuppressWarnings("rawtypes")
public class ReflectionInformation {

  private Class classInfo;
  private Field[] fields;
  private Constructor constructor;
  private int parameterCount;
  private boolean isVariable;
  private List<Integer> vectorLengths;
  private List<Class> vectorElementTypes;
  private List<Class> listElementTypes;
  private List<Long> listElementMaxSizes;
  private List<Long> bitlistElementMaxSizes;
  private List<Integer> bitvectorSizes;

  @SuppressWarnings("unchecked")
  public ReflectionInformation(Class classInfo) {
    try {
      this.classInfo = classInfo;
      this.fields = getFields(classInfo);
      final Class[] paramClasses = getTypes(fields);
      this.constructor = classInfo.getConstructor(paramClasses);
      this.parameterCount = constructor.getParameterCount();
      this.isVariable = ReflectionInformationUtil.isVariable(this);
      this.vectorLengths = ReflectionInformationUtil.getVectorLengths(this);
      this.vectorElementTypes = ReflectionInformationUtil.getVectorElementTypes(this);
      this.listElementTypes = ReflectionInformationUtil.getListElementTypes(this);
      this.listElementMaxSizes = ReflectionInformationUtil.getListElementMaxSizes(this);
      this.bitlistElementMaxSizes = ReflectionInformationUtil.getBitlistElementMaxSizes(this);
      this.bitvectorSizes = ReflectionInformationUtil.getBitvectorSizes(this);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Failed to determine SSZ reflection information", e);
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

  public List<Class> getListElementTypes() {
    return listElementTypes;
  }

  public List<Long> getListElementMaxSizes() {
    return listElementMaxSizes;
  }

  public List<Long> getBitlistElementMaxSizes() {
    return bitlistElementMaxSizes;
  }

  public List<Integer> getBitvectorSizes() {
    return bitvectorSizes;
  }

  private Field[] getFields(Class classInfo) {
    return Arrays.stream(classInfo.getDeclaredFields())
        .filter(f -> !Modifier.isStatic(f.getModifiers()))
        .filter(
            f ->
                f.getAnnotation(Label.class) == null
                    || !"sos-ignore".equals(f.getAnnotation(Label.class).value()))
        .toArray(Field[]::new);
  }

  private Class[] getTypes(Field[] fields) {
    return Arrays.stream(fields).map(Field::getType).toArray(Class[]::new);
  }
}
