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

package tech.pegasys.teku.datastructures.util;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AttestationProcessingResult {

  public static final AttestationProcessingResult SUCCESSFUL =
      new AttestationProcessingResult(Status.SUCCESSFUL, Optional.empty());
  public static final AttestationProcessingResult SAVED_FOR_FUTURE =
      new AttestationProcessingResult(Status.SAVED_FOR_FUTURE, Optional.empty());
  public static final AttestationProcessingResult UNKNOWN_BLOCK =
      new AttestationProcessingResult(Status.UNKNOWN_BLOCK, Optional.empty());

  private final Status status;
  private final Optional<String> invalidReason;

  private AttestationProcessingResult(final Status status, final Optional<String> invalidReason) {
    this.status = status;
    this.invalidReason = invalidReason;
  }

  public static AttestationProcessingResult invalid(final String reason) {
    return new AttestationProcessingResult(Status.INVALID, Optional.of(reason));
  }

  public AttestationProcessingResult ifSuccessful(
      final Supplier<AttestationProcessingResult> nextStep) {
    return isSuccessful() ? nextStep.get() : this;
  }

  public void ifUnsuccessful(final Consumer<String> handler) {
    if (!isSuccessful()) {
      handler.accept(getInvalidReason());
    }
  }

  public void ifInvalid(final Consumer<String> handler) {
    if (status == Status.INVALID) {
      handler.accept(getInvalidReason());
    }
  }

  public boolean isSuccessful() {
    return status == Status.SUCCESSFUL;
  }

  public Status getStatus() {
    return status;
  }

  public String getInvalidReason() {
    return invalidReason.orElseGet(status::toString);
  }

  public enum Status {
    SUCCESSFUL,
    UNKNOWN_BLOCK,
    SAVED_FOR_FUTURE,
    INVALID
  }
}
