/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.ssz.backing;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import tech.pegasys.teku.ssz.backing.type.ViewType;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class Utils {

  private static final Map<Class<?>, Supplier<Optional<ViewType<?>>>> classToSszTypeMap =
      new ConcurrentHashMap<>();

  public static Optional<ViewType<?>> getSszType(Class<?> clazz) {
    return classToSszTypeMap.computeIfAbsent(clazz, Utils::createSszTypeFactory).get();
  }

  private static Supplier<Optional<ViewType<?>>> createSszTypeFactory(Class<?> clazz) {
    Optional<Method> maybeMethod =
        getAllPredecessors(clazz).stream()
            .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
            .filter(f -> Modifier.isStatic(f.getModifiers()))
            .filter(f -> f.isAnnotationPresent(SszTypeDescriptor.class))
            .findFirst();
    Optional<Field> maybeField =
        getAllPredecessors(clazz).stream()
            .flatMap(c -> Arrays.stream(c.getDeclaredFields()))
            .filter(f -> Modifier.isStatic(f.getModifiers()))
            .filter(f -> f.isAnnotationPresent(SszTypeDescriptor.class))
            .findFirst();

    Function<Method, Optional<ViewType<?>>> methodFactory =
        method -> {
          try {
            return Optional.of((ViewType<?>) method.invoke(null));
          } catch (IllegalAccessException | InvocationTargetException e) {
            return Optional.empty();
          }
        };
    Function<Field, Optional<ViewType<?>>> fieldFactory =
        field -> {
          try {
            return Optional.of((ViewType<?>) field.get(null));
          } catch (IllegalAccessException e) {
            return Optional.empty();
          }
        };

    return () -> maybeMethod.flatMap(methodFactory).or(() -> maybeField.flatMap(fieldFactory));
  }

  private static List<Class<?>> getAllPredecessors(Class<?> clazz) {
    List<Class<?>> ret = new ArrayList<>();
    Class<?> c = clazz;
    do {
      ret.add(c);
      c = c.getSuperclass();
    } while (c != null);
    ret.addAll(Arrays.asList(clazz.getInterfaces()));
    return ret;
  }

  public static long nextPowerOf2(long x) {
    return x <= 1 ? 1 : Long.highestOneBit(x - 1) << 1;
  }

}
