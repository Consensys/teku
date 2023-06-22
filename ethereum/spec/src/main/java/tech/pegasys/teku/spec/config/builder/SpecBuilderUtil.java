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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfigFormatter.camelToSnakeCase;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SpecBuilderUtil {
  private static final Map<Class<?>, Object> DEFAULT_ZERO_VALUES =
      Map.of(
          UInt64.class,
          UInt64.ZERO,
          UInt256.class,
          UInt256.ZERO,
          Integer.class,
          0,
          Long.class,
          0,
          Bytes4.class,
          Bytes4.leftPad(Bytes.EMPTY),
          Bytes32.class,
          Bytes32.leftPad(Bytes.EMPTY));

  // Placeholder version explicitly doesn't match MainNet (or any other known testnet)
  static final Bytes4 PLACEHOLDER_FORK_VERSION = Bytes4.fromHexString("0x99999999");

  static void validateConstant(final String name, final Object value) {
    validateNotNull(name, value);
  }

  static void validateConstant(final String name, final Long value) {
    validateNotNull(name, value);
    checkArgument(value >= 0, "Long values must be positive");
  }

  static void validateConstant(final String name, final Integer value) {
    validateNotNull(name, value);
    checkArgument(value >= 0, "Integer values must be positive");
  }

  static void validateNotNull(final String name, final Object value) {
    checkArgument(value != null, "Missing value for spec constant '%s'", camelToSnakeCase(name));
  }

  static <T> void validateRequiredOptional(final String name, final Optional<T> value) {
    checkArgument(value.isPresent(), "Missing value for required '%s'", name);
  }

  static void fillMissingValuesWithZeros(final ForkConfigBuilder<?, ?> builder) {
    Arrays.stream(builder.getClass().getDeclaredFields())
        // skip constants
        .filter(field -> !Modifier.isFinal(field.getModifiers()))
        // skip non-null fields
        .filter(
            field -> {
              try {
                field.setAccessible(true);
                return field.get(builder) == null;
              } catch (IllegalAccessException | IllegalArgumentException ex) {
                throw new RuntimeException(String.format("Cannot check status of field %s", field));
              }
            })
        // fill with default values
        .forEach(
            field -> {
              try {
                field.setAccessible(true);
                final Object fillValue = DEFAULT_ZERO_VALUES.get(field.getType());
                if (fillValue == null) {
                  throw new RuntimeException(
                      String.format(
                          "Cannot fill the field %s, no default value for this type", field));
                }
                field.set(builder, fillValue);
              } catch (IllegalAccessException | IllegalArgumentException ex) {
                throw new RuntimeException(String.format("Cannot set field %s", field));
              }
            });
  }
}
