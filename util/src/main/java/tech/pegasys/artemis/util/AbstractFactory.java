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

package tech.pegasys.artemis.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class AbstractFactory<T> {

  public T create(String something) throws IllegalArgumentException {
    try {
      Class clazz = this.getClassType(something);
      return (T) clazz.getDeclaredConstructor().newInstance();
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public T create(String something, Object[] args, Class[] argTypes) {
    try {
      Class clazz = this.getClassType(something);
      Constructor constructor = clazz.getConstructor(argTypes);
      return (T) constructor.newInstance(args);
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public abstract Class getClassType(String something);
}
