/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator;

/** Represents block validation result which may contain reason exception in case of a failure */
public class BlockValidationResult {
  public static BlockValidationResult SUCCESSFUL = new BlockValidationResult(true);
  public static BlockValidationResult FAILED = new BlockValidationResult(false);

  private final boolean isValid;
  private final Exception reason;

  private BlockValidationResult(Exception reason) {
    this.isValid = false;
    this.reason = reason;
  }

  private BlockValidationResult(boolean isValid) {
    this.isValid = isValid;
    reason = null;
  }

  public static BlockValidationResult failedExceptionally(final Exception reason) {
    return new BlockValidationResult(reason);
  }

  public boolean isValid() {
    return isValid;
  }

  public Exception getReason() {
    return reason;
  }
}
