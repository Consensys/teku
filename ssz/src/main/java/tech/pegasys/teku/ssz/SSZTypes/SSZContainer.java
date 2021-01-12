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

package tech.pegasys.teku.ssz.SSZTypes;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import tech.pegasys.teku.ssz.backing.type.ViewType;

public interface SSZContainer {

  class Field {
    private final int index;
    private final Supplier<ViewType<?>> viewType;

    public Field(int index, ViewType<?> viewType) {
      this(index, () -> viewType);
    }

    public Field(int index, Supplier<ViewType<?>> viewType) {
      this.index = index;
      this.viewType = viewType;
    }

    public int getIndex() {
      return index;
    }

    public Supplier<ViewType<?>> getViewType() {
      return viewType;
    }
  }

  static List<Field> listFields(Class<?> clazz) {
    List<Field> ret =
        Arrays.stream(clazz.getDeclaredFields())
            .filter(f -> Modifier.isStatic(f.getModifiers()))
            .filter(f -> f.getType() == Field.class)
            .map(
                f -> {
                  try {
                    return (Field) f.get(null);
                  } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                  }
                })
            .sorted(Comparator.comparing(Field::getIndex))
            .collect(Collectors.toList());
    for (int i = 0; i < ret.size(); i++) {
      if (i != ret.get(i).getIndex()) {
        throw new IllegalArgumentException("Wrong fields ordering: " + ret);
      }
    }
    return ret;
  }
}
