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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.ethereum.beacon.ssz.SSZSchemeException;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.annotation.MCVEReflect;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.ssz.annotation.SSZTransient;
import org.ethereum.beacon.util.cache.Cache;
import org.ethereum.beacon.util.cache.LRUCache;
import org.ethereum.beacon.util.cache.MockCache;
import org.javatuples.Pair;

/**
 * Builds scheme of SSZ serializable model using Java class with annotations markup
 *
 * <p>Scheme builder could be initialized only once with one class, create new instance for each new
 * type
 *
 * <p>Following annotations are used for this:
 *
 * <ul>
 *   <li>{@link SSZSerializable} - Class which stores SSZ serializable data should be annotated with
 *       it, any type which is not java.lang.*
 *   <li>{@link SSZ} - any field with Java type couldn't be automatically mapped to SSZ type, or if
 *       `explicitFieldAnnotation` is set to true, every field which should be added to the model.
 *       Also cases when mapping that overrides default, should be annotated with it. For standard
 *       mappings check {@link SSZ#type()} Javadoc.
 *   <li>{@link SSZTransient} - Fields that should not be used in serialization should be marked
 *       with such annotation
 * </ul>
 */
public class SSZAnnotationSchemeBuilder implements SSZSchemeBuilder {

  private static final String TYPE_REGEX = "^(\\D+)((\\d+)?)$";

  private Logger logger = null;

  private boolean explicitFieldAnnotation = true;

  private Cache<Class, SSZScheme> cache = new MockCache<>();

  public SSZAnnotationSchemeBuilder() {}

  /**
   * Whether to require {@link SSZ} annotation for field to be included, non-transient or not.
   * Default: {@link SSZ} required for each field When `explicitFieldAnnotation` set to false, all
   * fields are included, unless marked with {@link SSZTransient}
   *
   * @param explicitFieldAnnotation Require {@link SSZ} annotation for field to be included in
   *     scheme
   */
  public SSZAnnotationSchemeBuilder(boolean explicitFieldAnnotation) {
    this.explicitFieldAnnotation = explicitFieldAnnotation;
  }

  private static Pair<String, Integer> extractType(String extra, Class clazz) {
    String extraType;
    Integer extraSize = null;
    Pattern pattern = Pattern.compile(TYPE_REGEX);
    Matcher matcher = pattern.matcher(extra);
    if (matcher.find()) {
      String type = matcher.group(1);
      String endNumber = matcher.group(3);
      extraType = type;

      if (endNumber != null) {
        extraSize = Integer.valueOf(endNumber);
      }
    } else {
      String error =
          String.format(
              "Type annotation \"%s\" for class %s is not correct", extra, clazz.getName());
      throw new SSZSchemeException(error);
    }

    return new Pair<>(extraType, extraSize);
  }

  /**
   * Initializes cache, unlimited in size, 1 scheme record per each class
   *
   * @param capacity cache capacity
   * @return this scheme builder with cache added
   */
  public SSZAnnotationSchemeBuilder withCache(int capacity) {
    this.cache = new LRUCache<>(capacity);
    return this;
  }

  /**
   * Add logger to {@link SSZAnnotationSchemeBuilder}
   *
   * @param logger Java logger
   * @return this
   */
  public SSZAnnotationSchemeBuilder withLogger(Logger logger) {
    this.logger = logger;
    return this;
  }

  /**
   * Builds SSZ scheme of provided Java class and returns result.
   *
   * <p>Class should be marked with annotations, check top Javadoc for more info.
   *
   * @return scheme of SSZ model
   */
  @Override
  public SSZScheme build(Class clazz) {
    return cache.get(clazz, this::buildImpl);
  }

  private SSZScheme buildImpl(Class clazz) {
    SSZScheme scheme = new SSZScheme();
    SSZSerializable mainAnnotation = (SSZSerializable) clazz.getAnnotation(SSZSerializable.class);

    // No encode parameter, build scheme field by field
    Map<String, Method> fieldGetters = new LinkedHashMap<>();
    try {
      for (PropertyDescriptor pd : Introspector.getBeanInfo(clazz).getPropertyDescriptors()) {
        if (pd.getName() != null && pd.getReadMethod() != null) {
          fieldGetters.put(pd.getName(), pd.getReadMethod());
        }
      }
    } catch (IntrospectionException e) {
      String error = String.format("Couldn't enumerate all getters in class %s", clazz.getName());
      throw new SSZSchemeException(error, e);
    }

    for (Field field : clazz.getDeclaredFields()) {

      // Skip SSZTransient
      if (field.getAnnotation(SSZTransient.class) != null) {
        continue;
      }

      // Check for SSZ annotation and read its parameters
      Class type = field.getType();
      SSZ annotation = null;
      if (field.isAnnotationPresent(SSZ.class)) {
        annotation = field.getAnnotation(SSZ.class);
      } else {
        if (explicitFieldAnnotation) { // Skip field if explicit annotation is required
          continue;
        }
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        if (isStatic) { // Skip static fields if it's no marked by @SSZ annotation
          continue;
        }
      }
      String typeAnnotation = null;
      if (annotation != null && !annotation.type().isEmpty()) {
        typeAnnotation = annotation.type();
      }

      // Construct SSZField
      Type fieldType = field.getGenericType();
      String name = field.getName();
      String extraType = null;
      Integer extraSize = null;
      if (typeAnnotation != null) {
        Pair<String, Integer> extra = extractType(typeAnnotation, type);
        extraType = extra.getValue0();
        extraSize = extra.getValue1();
      }
      String getter = fieldGetters.containsKey(name) ? fieldGetters.get(name).getName() : null;
      scheme
          .getFields()
          .add(new SSZField(fieldType, annotation, extraType, extraSize, name, getter));
    }

    if (explicitFieldAnnotation) {
      class Data {
        String name;
        Method getter;
        SSZ annotation;
        Method annotationMethod;

        public Data(String name, Method getter, SSZ annotation, Method annotationMethod) {
          this.name = name;
          this.getter = getter;
          this.annotation = annotation;
          this.annotationMethod = annotationMethod;
        }
      }

      List<Data> fields =
          fieldGetters.entrySet().stream()
              .map(
                  e -> {
                    Pair<SSZ, Method> pair = MCVEReflect.getAnnotation(e.getValue(), SSZ.class);
                    if (pair != null) {
                      if (pair.getValue0().order() < 0) {
                        throw new SSZSchemeException(
                            "Order of SSZ fields declared by interface should be defined via 'order' @SSZ attribute: "
                                + pair.getValue1());
                      }
                      return new Data(e.getKey(), e.getValue(), pair.getValue0(), pair.getValue1());
                    } else {
                      return null;
                    }
                  })
              .filter(Objects::nonNull)
              .sorted(
                  (d1, d2) -> {
                    if (!d1.annotationMethod
                        .getDeclaringClass()
                        .equals(d2.annotationMethod.getDeclaringClass())) {
                      throw new SSZSchemeException(
                          "Declaring SSZ properties from distinct interfaces is not supported yet: "
                              + d1.annotationMethod
                              + ", "
                              + d2.annotationMethod);
                    }
                    if (d1.annotation.order() == d2.annotation.order()) {
                      throw new SSZSchemeException(
                          "Duplicate @SSZ.order "
                              + d1.annotationMethod
                              + ", "
                              + d2.annotationMethod);
                    }
                    return Integer.compare(d1.annotation.order(), d2.annotation.order());
                  })
              .collect(Collectors.toList());

      for (Data field : fields) {
        SSZ annotation = field.annotation;
        if (annotation != null) {

          String typeAnnotation = null;
          if (!annotation.type().isEmpty()) {
            typeAnnotation = annotation.type();
          }

          // Construct SSZField
          Type fieldType = field.getter.getGenericReturnType();
          String name = field.name;
          String extraType = null;
          Integer extraSize = null;
          if (typeAnnotation != null) {
            Pair<String, Integer> extra = extractType(typeAnnotation, field.getter.getReturnType());
            extraType = extra.getValue0();
            extraSize = extra.getValue1();
          }
          String getter = field.getter.getName();
          scheme
              .getFields()
              .add(new SSZField(fieldType, annotation, extraType, extraSize, name, getter));
        }
      }
    }

    return logAndReturnScheme(clazz, scheme);
  }

  private SSZScheme logAndReturnScheme(Class clazz, SSZScheme scheme) {
    if (logger == null) {
      return scheme;
    }
    String overview =
        String.format(
            "Scheme for class %s consists of %s field(s)",
            clazz.getName(), scheme.getFields().size());
    logger.info(overview);
    for (SSZField field : scheme.getFields()) {
      logger.info(field.toString());
    }

    return scheme;
  }
}
