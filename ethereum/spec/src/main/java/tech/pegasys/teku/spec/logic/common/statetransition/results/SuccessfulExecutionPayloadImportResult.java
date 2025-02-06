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

package tech.pegasys.teku.spec.logic.common.statetransition.results;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;

public class SuccessfulExecutionPayloadImportResult implements ExecutionPayloadImportResult {

  private final SignedExecutionPayloadEnvelope executionPayload;

  public SuccessfulExecutionPayloadImportResult(
      final SignedExecutionPayloadEnvelope executionPayload) {
    this.executionPayload = executionPayload;
  }

  @Override
  public boolean isSuccessful() {
    return true;
  }

  @Override
  public SignedExecutionPayloadEnvelope getExecutionPayload() {
    return executionPayload;
  }

  @Override
  public FailureReason getFailureReason() {
    return null;
  }

  @Override
  public Optional<Throwable> getFailureCause() {
    return Optional.empty();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SuccessfulExecutionPayloadImportResult that = (SuccessfulExecutionPayloadImportResult) o;
    return Objects.equals(executionPayload, that.executionPayload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(executionPayload);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("executionPayload", executionPayload).toString();
  }
}
