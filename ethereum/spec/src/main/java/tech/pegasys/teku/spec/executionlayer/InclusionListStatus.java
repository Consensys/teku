/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.executionlayer;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;

public class InclusionListStatus {
  public static final InclusionListStatus VALID =
      new InclusionListStatus(
          Optional.of(ExecutionPayloadStatus.VALID), Optional.empty(), Optional.empty());
  public static final InclusionListStatus SYNCING =
      new InclusionListStatus(
          Optional.of(ExecutionPayloadStatus.SYNCING), Optional.empty(), Optional.empty());
  public static final InclusionListStatus ACCEPTED =
      new InclusionListStatus(
          Optional.of(ExecutionPayloadStatus.ACCEPTED), Optional.empty(), Optional.empty());

  public static InclusionListStatus failedExecution(final Throwable cause) {
    return new InclusionListStatus(Optional.empty(), Optional.empty(), Optional.of(cause));
  }

  public static InclusionListStatus invalid(Optional<String> validationError) {
    return new InclusionListStatus(
        Optional.of(ExecutionPayloadStatus.INVALID), validationError, Optional.empty());
  }

  public static InclusionListStatus valid(Optional<String> validationError) {
    return new InclusionListStatus(
        Optional.of(ExecutionPayloadStatus.VALID), validationError, Optional.empty());
  }

  public static InclusionListStatus create(
      ExecutionPayloadStatus status, Optional<String> validationError) {
    return new InclusionListStatus(Optional.of(status), validationError, Optional.empty());
  }

  private final Optional<ExecutionPayloadStatus> status;
  private final Optional<String> validationError;
  private final Optional<Throwable> failureCause;

  private InclusionListStatus(
      Optional<ExecutionPayloadStatus> status,
      Optional<String> validationError,
      Optional<Throwable> failureCause) {
    this.status = status;
    this.validationError = validationError;
    this.failureCause = failureCause;
  }

  public boolean hasFailedExecution() {
    return failureCause.isPresent();
  }

  public Optional<Throwable> getFailureCause() {
    return failureCause;
  }

  public Optional<ExecutionPayloadStatus> getStatus() {
    return status;
  }

  public boolean hasStatus(ExecutionPayloadStatus status) {
    return this.status.map(s -> s == status).orElse(false);
  }

  public boolean hasValidStatus() {
    return this.status.map(ExecutionPayloadStatus::isValid).orElse(false);
  }

  public boolean hasNotValidatedStatus() {
    return this.status.map(ExecutionPayloadStatus::isNotValidated).orElse(false);
  }

  public boolean hasInvalidStatus() {
    return this.status.map(ExecutionPayloadStatus::isInvalid).orElse(false);
  }

  public Optional<String> getValidationError() {
    return validationError;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final InclusionListStatus that = (InclusionListStatus) o;
    return Objects.equals(status, that.status)
        && Objects.equals(validationError, that.validationError)
        && Objects.equals(failureCause, that.failureCause);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, validationError, failureCause);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("status", status)
        .add("validationError", validationError)
        .add("failureCause", failureCause)
        .toString();
  }
}
