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
 */
public sealed interface DataColumnSidecarValidationError
    permits DataColumnSidecarValidationError.Critical,
        DataColumnSidecarValidationError.Transient,
        DataColumnSidecarValidationError.Timing {

  String description();

  // Malformed or cryptographically invalid data
  record Critical(String details) implements DataColumnSidecarValidationError {
    @Override
    public String description() {
      return details;
    }
  }

  // Temporarily unavailable data
  record Transient(String details) implements DataColumnSidecarValidationError {
    @Override
    public String description() {
      return details;
    }
  }

  // Timing-related issues requiring deferred processing
  record Timing(String details) implements DataColumnSidecarValidationError {
    @Override
    public String description() {
      return details;
    }
  }
}
