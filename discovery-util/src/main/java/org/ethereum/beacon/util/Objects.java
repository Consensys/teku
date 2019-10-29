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

package org.ethereum.beacon.util;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.PropertyUtils;

public class Objects {
  /**
   * "copyProperties" method from <a
   * href="https://stackoverflow.com/a/24866702">https://stackoverflow.com/a/24866702</a>
   *
   * <p>Copies all properties from sources to destination, does not copy null values and any nested
   * objects will attempted to be either cloned or copied into the existing object. This is
   * recursive. Should not cause any infinite recursion.
   *
   * @param dest object to copy props into (will mutate)
   * @param sources
   * @param <T> dest
   * @return
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   */
  public static <T> T copyProperties(T dest, Object... sources)
      throws IllegalAccessException, InvocationTargetException {
    // to keep from any chance infinite recursion lets limit each object to 1 instance at a time in
    // the stack
    final List<Object> lookingAt = new ArrayList<>();

    BeanUtilsBean recursiveBeanUtils =
        new BeanUtilsBean() {

          /**
           * Check if the class name is an internal one
           *
           * @param name
           * @return
           */
          private boolean isInternal(String name) {
            return name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("com.sun.")
                || name.startsWith("javax.")
                || name.startsWith("oracle.");
          }

          /**
           * Override to ensure that we dont end up in infinite recursion
           *
           * @param dest
           * @param orig
           * @throws IllegalAccessException
           * @throws InvocationTargetException
           */
          @Override
          public void copyProperties(Object dest, Object orig)
              throws IllegalAccessException, InvocationTargetException {
            try {
              // if we have an object in our list, that means we hit some sort of recursion, stop
              // here.
              if (lookingAt.stream().anyMatch(o -> o == dest)) {
                return; // recursion detected
              }
              lookingAt.add(dest);
              super.copyProperties(dest, orig);
            } finally {
              lookingAt.remove(dest);
            }
          }

          @Override
          public void copyProperty(Object dest, String name, Object value)
              throws IllegalAccessException, InvocationTargetException {
            // dont copy over null values
            if (value != null) {
              // attempt to check if the value is a pojo we can clone using nested calls
              if (!value.getClass().isPrimitive()
                  && !value.getClass().isSynthetic()
                  && !isInternal(value.getClass().getName())) {
                try {
                  Object prop = super.getPropertyUtils().getProperty(dest, name);
                  // get current value, if its null then clone the value and set that to the value
                  if (prop == null) {
                    super.setProperty(dest, name, super.cloneBean(value));
                  } else {
                    // get the destination value and then recursively call
                    copyProperties(prop, value);
                  }
                } catch (NoSuchMethodException e) {
                  return;
                } catch (InstantiationException e) {
                  throw new RuntimeException("Nested property could not be cloned.", e);
                }
              } else {
                super.copyProperty(dest, name, value);
              }
            }
          }
        };

    for (Object source : sources) {
      recursiveBeanUtils.copyProperties(dest, source);
    }

    return dest;
  }

  /**
   * Sets the value of the (possibly nested) property of the specified * name, for the specified
   * bean, with no type conversions.
   */
  public static void setNestedProperty(Object bean, String propertyKey, Object propertyValue)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    PropertyUtils.setNestedProperty(bean, propertyKey, propertyValue);
  }
}
