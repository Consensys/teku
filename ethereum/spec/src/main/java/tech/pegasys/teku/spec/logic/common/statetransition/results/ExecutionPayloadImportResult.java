/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.logic.common.statetransition.results;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;

public interface ExecutionPayloadImportResult {

  ExecutionPayloadImportResult FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING =
      new FailedExecutionPayloadImportResult(
          FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING, Optional.empty());

  static ExecutionPayloadImportResult failedDataAvailabilityCheckInvalid(
      final Optional<Throwable> cause) {
    return new FailedExecutionPayloadImportResult(
        FailureReason.FAILED_DATA_AVAILABILITY_CHECK_INVALID, cause);
  }

  static ExecutionPayloadImportResult failedDataAvailabilityCheckNotAvailable(
      final Optional<Throwable> cause) {
    return new FailedExecutionPayloadImportResult(
        FailureReason.FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE, cause);
  }

  static ExecutionPayloadImportResult failedExecutionPayloadExecution(final Throwable cause) {
    return new FailedExecutionPayloadImportResult(
        FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION, Optional.of(cause));
  }

  static ExecutionPayloadImportResult failedStateTransition(final Exception cause) {
    return new FailedExecutionPayloadImportResult(
        FailureReason.FAILED_STATE_TRANSITION, Optional.of(cause));
  }

  static ExecutionPayloadImportResult successful(
      final SignedExecutionPayloadEnvelope executionPayload) {
    return new SuccessfulExecutionPayloadImportResult(executionPayload);
  }

  static ExecutionPayloadImportResult optimisticallySuccessful(
      final SignedExecutionPayloadEnvelope executionPayload) {
    return new OptimisticSuccessfulExecutionPayloadImportResult(executionPayload);
  }

  enum FailureReason {
    FAILED_STATE_TRANSITION,
    FAILED_EXECUTION_PAYLOAD_EXECUTION,
    FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING,
    FAILED_DATA_AVAILABILITY_CHECK_INVALID,
    FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE
  }

  boolean isSuccessful();

  SignedExecutionPayloadEnvelope getExecutionPayload();

  /**
   * @return If failed, returns a non-null failure reason, otherwise returns null.
   */
  FailureReason getFailureReason();

  /**
   * @return If failed, may return a {@code Throwable} cause. If successful, this value is always
   *     empty.
   */
  Optional<Throwable> getFailureCause();

  default boolean isImportedOptimistically() {
    return false;
  }

  default boolean hasFailedExecutingExecutionPayload() {
    return false;
  }
}
