/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.common.util;

import com.google.errorprone.annotations.FormatMethod;
import java.util.function.Supplier;

/**
 * Domain-specific validation errors for Data Column Sidecars.
 *
 * <ul>
 *   <li>{@link Critical} - Malformed or cryptographically invalid data (REJECT)
 *   <li>{@link Transient} - Temporarily unavailable dependencies (IGNORE)
 *   <li>{@link Timing} - Timing-related issues requiring deferred processing (SAVE_FOR_FUTURE)
 * </ul>
 *
 * <p>The gossip validation layer is responsible for mapping these domain errors to network actions.
 * The fork validation layer only reports what is wrong, not how to handle it.
 *
 * <p>Error descriptions are lazily evaluated using static factory methods to avoid unnecessary
 * string formatting when the description isn't accessed.
 */
public sealed interface DataColumnSidecarValidationError
    permits DataColumnSidecarValidationError.Critical,
        DataColumnSidecarValidationError.Transient,
        DataColumnSidecarValidationError.Timing {

  Supplier<String> detailsSupplier();

  default String description() {
    return detailsSupplier().get();
  }

  // Malformed or cryptographically invalid data
  record Critical(Supplier<String> detailsSupplier) implements DataColumnSidecarValidationError {

    @FormatMethod
    public static Critical format(final String format, final Object... args) {
      return new Critical(() -> String.format(format, args));
    }
  }

  // Temporarily unavailable data
  record Transient(Supplier<String> detailsSupplier) implements DataColumnSidecarValidationError {

    @FormatMethod
    public static Transient format(final String format, final Object... args) {
      return new Transient(() -> String.format(format, args));
    }
  }

  // Timing-related issues requiring deferred processing
  record Timing(Supplier<String> detailsSupplier) implements DataColumnSidecarValidationError {

    @FormatMethod
    public static Timing format(final String format, final Object... args) {
      return new Timing(() -> String.format(format, args));
    }
  }
}
