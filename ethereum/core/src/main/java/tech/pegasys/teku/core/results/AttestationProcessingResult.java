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

package tech.pegasys.teku.core.results;

import java.util.function.Supplier;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;

public interface AttestationProcessingResult {
  AttestationProcessingResult SUCCESSFUL = new Successful();
  AttestationProcessingResult FAILED_UNKNOWN_BLOCK = new Failure(FailureReason.UNKNOWN_BLOCK);
  AttestationProcessingResult FAILED_NOT_FROM_PAST =
      new Failure(FailureReason.ATTESTATION_IS_NOT_FROM_PREVIOUS_SLOT);
  AttestationProcessingResult FAILED_FUTURE_EPOCH = new Failure(FailureReason.FOR_FUTURE_EPOCH);

  static AttestationProcessingResult invalid(final String message) {
    return new Failure(FailureReason.ATTESTATION_IS_INVALID, message);
  }

  static AttestationProcessingResult failedStateTransition(final SlotProcessingException cause) {
    return new Failure(FailureReason.FAILED_STATE_TRANSITION, cause.getMessage());
  }

  static AttestationProcessingResult failedStateTransition(final EpochProcessingException cause) {
    return new Failure(FailureReason.FAILED_STATE_TRANSITION, cause.getMessage());
  }

  boolean isSuccessful();

  default AttestationProcessingResult ifSuccessful(Supplier<AttestationProcessingResult> nextStep) {
    return isSuccessful() ? nextStep.get() : this;
  }

  /** @return If failed, returns a non-null failure reason, otherwise returns null. */
  FailureReason getFailureReason();

  /** @return If failed, returns a non-null failure message, otherwise returns null. */
  String getFailureMessage();

  enum FailureReason {
    UNKNOWN_BLOCK,
    ATTESTATION_IS_NOT_FROM_PREVIOUS_SLOT,
    FOR_FUTURE_EPOCH,
    FAILED_STATE_TRANSITION,
    ATTESTATION_IS_INVALID
  }

  class Successful implements AttestationProcessingResult {

    private Successful() {}

    @Override
    public boolean isSuccessful() {
      return true;
    }

    @Override
    public FailureReason getFailureReason() {
      return null;
    }

    @Override
    public String getFailureMessage() {
      return null;
    }
  }

  class Failure implements AttestationProcessingResult {
    private final FailureReason failureReason;
    private final String failureMessage;

    private Failure(final FailureReason failureReason) {
      this(failureReason, failureReason.name());
    }

    private Failure(final FailureReason failureReason, final String failureMessage) {
      this.failureReason = failureReason;
      this.failureMessage = failureMessage;
    }

    @Override
    public boolean isSuccessful() {
      return false;
    }

    @Override
    public FailureReason getFailureReason() {
      return failureReason;
    }

    @Override
    public String getFailureMessage() {
      return failureMessage;
    }
  }
}
