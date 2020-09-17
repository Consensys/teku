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

package tech.pegasys.teku.weaksubjectivity;

import java.util.List;
import org.apache.logging.log4j.Level;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;
import tech.pegasys.teku.weaksubjectivity.policies.LoggingWeakSubjectivityViolationPolicy;
import tech.pegasys.teku.weaksubjectivity.policies.StrictWeakSubjectivityViolationPolicy;
import tech.pegasys.teku.weaksubjectivity.policies.WeakSubjectivityViolationPolicy;

public class WeakSubjectivityValidator {
  private final WeakSubjectivityCalculator calculator;
  private final List<WeakSubjectivityViolationPolicy> violationPolicies;

  WeakSubjectivityValidator(
      WeakSubjectivityCalculator calculator,
      List<WeakSubjectivityViolationPolicy> violationPolicies) {
    this.calculator = calculator;
    this.violationPolicies = violationPolicies;
  }

  public static WeakSubjectivityValidator strict(final WeakSubjectivityConfig config) {
    final WeakSubjectivityCalculator calculator = WeakSubjectivityCalculator.create(config);
    final List<WeakSubjectivityViolationPolicy> policies =
        List.of(
            new LoggingWeakSubjectivityViolationPolicy(Level.FATAL),
            new StrictWeakSubjectivityViolationPolicy());
    return new WeakSubjectivityValidator(calculator, policies);
  }

  public static WeakSubjectivityValidator lenient() {
    return lenient(WeakSubjectivityConfig.defaultConfig());
  }

  public static WeakSubjectivityValidator lenient(final WeakSubjectivityConfig config) {
    final WeakSubjectivityCalculator calculator = WeakSubjectivityCalculator.create(config);
    final List<WeakSubjectivityViolationPolicy> policies =
        List.of(new LoggingWeakSubjectivityViolationPolicy(Level.TRACE));
    return new WeakSubjectivityValidator(calculator, policies);
  }

  /**
   * Validates that the latest finalized checkpoint is within the weak subjectivity period, given
   * the current slot based on clock time. If validation fails, configured policies are run to
   * handle this failure.
   *
   * @param latestFinalizedCheckpoint The latest finalized checkpoint
   * @param currentSlot The current slot based on clock time
   */
  public void validateLatestFinalizedCheckpoint(
      final CheckpointState latestFinalizedCheckpoint, final UInt64 currentSlot) {
    if (!calculator.isWithinWeakSubjectivityPeriod(latestFinalizedCheckpoint, currentSlot)) {
      final int activeValidators =
          calculator.getActiveValidators(latestFinalizedCheckpoint.getState());
      for (WeakSubjectivityViolationPolicy policy : violationPolicies) {
        policy.onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            latestFinalizedCheckpoint, activeValidators, currentSlot);
      }
    }
  }

  /**
   * A catch-all handler for managing problems encountered while executing other validations
   *
   * @param message An error message
   */
  public void handleValidationFailure(final String message) {
    for (WeakSubjectivityViolationPolicy policy : violationPolicies) {
      policy.onFailedToPerformValidation(message);
    }
  }

  /**
   * A catch-all handler for managing problems encountered while executing other validations
   *
   * @param message An error message
   * @param error The error encountered
   */
  public void handleValidationFailure(final String message, Throwable error) {
    for (WeakSubjectivityViolationPolicy policy : violationPolicies) {
      policy.onFailedToPerformValidation(message, error);
    }
  }
}
