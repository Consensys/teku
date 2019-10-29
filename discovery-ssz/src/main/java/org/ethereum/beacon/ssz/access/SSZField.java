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

package org.ethereum.beacon.ssz.access;

import static org.ethereum.beacon.ssz.type.SSZType.VARIABLE_SIZE;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import org.ethereum.beacon.ssz.annotation.SSZ;
import tech.pegasys.artemis.util.collections.ReadList;

/**
 * Type descriptor which contains all possible data gathered from Class info, annotations of a class
 * itself or a field annotation from another Class (aka SSZ container) This data is required to
 * correctly identify the right accessor, children types and work with raw type instance
 */
public class SSZField {

  /**
   * In case of list types resolves the type with the respect to the list elements type (if not
   * empty list)
   */
  public static SSZField resolveFromValue(Object value, Class<?> clazz) {
    if (value instanceof List && !((List) value).isEmpty()) {
      return new SSZField(new ParametrizedTypeImpl(clazz, ((List) value).get(0).getClass()));
    }
    if (value instanceof ReadList
        && !((ReadList) value).isVector()
        && ((ReadList) value).maxSize() > VARIABLE_SIZE) {
      ReadList readList = (ReadList) value;
      Class elementClass = Long.class; // XXX: some number
      if (!readList.isEmpty()) {
        elementClass = readList.get(0).getClass();
      }
      return new SSZField(
          new ParametrizedTypeImpl(clazz, ((ReadList) value).size().getClass(), elementClass),
          new SSZListMock(VARIABLE_SIZE, readList.maxSize()),
          null,
          null,
          null,
          null);
    }
    if (value instanceof ReadList && !((ReadList) value).isEmpty()) {
      ReadList readList = (ReadList) value;
      if (readList.isVector()) {
        // XXX: because we lost this without annotations
        final int vectorSize = readList.size().intValue();
        return new SSZField(
            new ParametrizedTypeImpl(
                clazz, ((ReadList) value).size().getClass(), readList.get(0).getClass()),
            new SSZListMock(vectorSize, VARIABLE_SIZE),
            null,
            null,
            null,
            null);
      } else {
        return new SSZField(
            new ParametrizedTypeImpl(
                clazz, ((ReadList) value).size().getClass(), readList.get(0).getClass()));
      }
    }
    return new SSZField(clazz);
  }

  private static class SSZListMock implements SSZ {
    private int vectorLength;
    private long maxSize;

    public SSZListMock(int vectorLength, long maxSize) {
      this.vectorLength = vectorLength;
      this.maxSize = maxSize;
    }

    @Override
    public java.lang.String type() {
      return null;
    }

    @Override
    public int vectorLength() {
      return vectorLength;
    }

    @Override
    public java.lang.String vectorLengthVar() {
      return "";
    }

    @Override
    public long maxSize() {
      return maxSize;
    }

    @Override
    public java.lang.String maxSizeVar() {
      return "";
    }

    @Override
    public int order() {
      return 0;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return SSZ.class;
    }
  }

  /**
   * In case of list types resolves the type with the respect to the list elements type (if not
   * empty list)
   */
  public static SSZField resolveFromValue(Object value) {
    return resolveFromValue(value, value.getClass());
  }

  private final Type fieldType;
  private final SSZ fieldAnnotation;
  private final String extraType;
  private final Integer extraSize;
  private final String name;
  private final String getter;

  public SSZField(
      Type fieldType,
      SSZ fieldAnnotation,
      String extraType,
      Integer extraSize,
      String name,
      String getter) {
    this.fieldType = fieldType;
    this.fieldAnnotation = fieldAnnotation;
    this.extraType = extraType;
    this.extraSize = extraSize;
    this.name = name;
    this.getter = getter;
  }

  public SSZField(Type fieldType) {
    this(fieldType, null, null, null, null, null);
    assert fieldType instanceof Class || fieldType instanceof ParameterizedType;
  }

  public Class<?> getRawClass() {
    return (Class<?>)
        (fieldType instanceof Class ? fieldType : ((ParameterizedType) fieldType).getRawType());
  }

  public ParameterizedType getParametrizedType() {
    return fieldType instanceof ParameterizedType ? (ParameterizedType) fieldType : null;
  }

  public SSZ getFieldAnnotation() {
    return fieldAnnotation;
  }

  public String getExtraType() {
    return extraType;
  }

  public Integer getExtraSize() {
    return extraSize;
  }

  public String getName() {
    return name;
  }

  public String getGetter() {
    return getter;
  }

  @Override
  public String toString() {
    return "SSZField{"
        + "fieldClass="
        + fieldType
        + ", extraType='"
        + extraType
        + '\''
        + ", extraSize="
        + extraSize
        + ", name='"
        + name
        + '\''
        + ", getter='"
        + getter
        + '\''
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SSZField sszField = (SSZField) o;

    if (!fieldType.equals(sszField.fieldType)) {
      return false;
    }

    if (fieldAnnotation != null
        ? !fieldAnnotation.equals(sszField.fieldAnnotation)
        : sszField.fieldAnnotation != null) {
      return false;
    }
    if (extraType != null ? !extraType.equals(sszField.extraType) : sszField.extraType != null) {
      return false;
    }
    if (extraSize != null ? !extraSize.equals(sszField.extraSize) : sszField.extraSize != null) {
      return false;
    }
    if (name != null ? !name.equals(sszField.name) : sszField.name != null) {
      return false;
    }
    return getter != null ? getter.equals(sszField.getter) : sszField.getter == null;
  }

  @Override
  public int hashCode() {
    return fieldType.hashCode();
  }

  private static class ParametrizedTypeImpl implements ParameterizedType {
    private final Type rawType;
    private final Type[] actualTypeArguments;

    public ParametrizedTypeImpl(Type rawType, Type... actualTypeArguments) {
      this.rawType = rawType;
      this.actualTypeArguments = actualTypeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public Type getOwnerType() {
      return null;
    }
  }
}
