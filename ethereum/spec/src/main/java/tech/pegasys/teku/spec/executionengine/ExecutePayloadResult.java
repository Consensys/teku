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

package tech.pegasys.teku.spec.executionengine;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;

public class ExecutePayloadResult {
  public static final ExecutePayloadResult VALID =
      new ExecutePayloadResult(
          Optional.of(ExecutionPayloadStatus.VALID),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
  public static final ExecutePayloadResult SYNCING =
      new ExecutePayloadResult(
          Optional.of(ExecutionPayloadStatus.SYNCING),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());

  public static ExecutePayloadResult failedExecution(final Throwable cause) {
    return new ExecutePayloadResult(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(cause));
  }

  public static ExecutePayloadResult invalid(
      Optional<Bytes32> latestValidHash, Optional<String> validationError) {
    return new ExecutePayloadResult(
        Optional.of(ExecutionPayloadStatus.INVALID),
        latestValidHash,
        validationError,
        Optional.empty());
  }

  public static ExecutePayloadResult create(
      ExecutionPayloadStatus status,
      Optional<Bytes32> latestValidHash,
      Optional<String> validationError) {
    return new ExecutePayloadResult(
        Optional.of(status), latestValidHash, validationError, Optional.empty());
  }

  private final Optional<ExecutionPayloadStatus> status;
  private final Optional<Bytes32> latestValidHash;
  private final Optional<String> validationError;
  private final Optional<Throwable> failureCause;

  private ExecutePayloadResult(
      Optional<ExecutionPayloadStatus> status,
      Optional<Bytes32> latestValidHash,
      Optional<String> validationError,
      Optional<Throwable> failureCause) {
    this.status = status;
    this.latestValidHash = latestValidHash;
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

  public Optional<Bytes32> getLatestValidHash() {
    return latestValidHash;
  }

  public Optional<String> getValidationError() {
    return validationError;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ExecutePayloadResult that = (ExecutePayloadResult) o;
    return Objects.equals(status, that.status)
        && Objects.equals(latestValidHash, that.latestValidHash)
        && Objects.equals(validationError, that.validationError);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, latestValidHash, validationError);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("status", status)
        .add("latestValidHash", latestValidHash)
        .add("validationError", validationError)
        .toString();
  }
}
