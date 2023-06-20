/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.config.builder;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;

interface ForkConfigBuilder<ParentType extends SpecConfig, ForkType extends ParentType> {
  Map<Class<?>, Object> DEFAULT_FILL =
      Map.of(
          UInt64.class,
          UInt64.ZERO,
          UInt256.class,
          UInt256.ZERO,
          Integer.class,
          0,
          Bytes4.class,
          Bytes4.leftPad(Bytes.EMPTY),
          Bytes32.class,
          Bytes32.leftPad(Bytes.EMPTY));

  ForkType build(ParentType specConfig);

  void validate();

  void addOverridableItemsToRawConfig(BiConsumer<String, Object> rawConfig);

  default void fillMissingValues() {
    Arrays.stream(this.getClass().getDeclaredFields())
        .filter(field -> !Modifier.isFinal(field.getModifiers()))
        .filter(
            field -> {
              try {
                field.setAccessible(true);
                return field.get(this) == null;
              } catch (IllegalAccessException | IllegalArgumentException ex) {
                throw new RuntimeException(String.format("Cannot check status of field %s", field));
              }
            })
        .forEach(
            field -> {
              try {
                field.setAccessible(true);
                final Object fillValue = DEFAULT_FILL.get(field.getType());
                if (fillValue == null) {
                  throw new RuntimeException(
                      String.format(
                          "Cannot fill the field %s, no default value for this type", field));
                }
                field.set(this, fillValue);
              } catch (IllegalAccessException | IllegalArgumentException ex) {
                throw new RuntimeException(String.format("Cannot set field %s", field));
              }
            });
  }
}
