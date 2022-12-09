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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;

public class SpecBuilderUtil {

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
}
