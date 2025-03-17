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
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionList;

@SuppressWarnings("ClassInitializationDeadlock")
public interface InclusionListImportResult {

  InclusionListImportResult FAILED_INCLUSION_LIST_SLOT =
      new FailedInclusionListImportResult(FailureReason.INCLUSION_LIST_SLOT, Optional.empty());
  InclusionListImportResult FAILED_PAST_ATTESTATION_DEADLINE =
      new FailedInclusionListImportResult(
          FailureReason.PAST_ATTESTATION_DEADLINE, Optional.empty());
  InclusionListImportResult FAILED_COMMITTEE_ROOT_MISMATCH =
      new FailedInclusionListImportResult(FailureReason.COMMITTEE_ROOT_MISMATCH, Optional.empty());
  InclusionListImportResult FAILED_VALIDATOR_NOT_IN_COMMITTEE =
      new FailedInclusionListImportResult(
          FailureReason.VALIDATOR_NOT_IN_COMMITTEE, Optional.empty());
  InclusionListImportResult FAILED_INVALID_SIGNATURE =
      new FailedInclusionListImportResult(FailureReason.INVALID_SIGNATURE, Optional.empty());
  InclusionListImportResult FAILED_EQUIVOCATED =
      new FailedInclusionListImportResult(FailureReason.INVALID_SIGNATURE, Optional.empty());
  InclusionListImportResult FAILED_STATE_UNAVAILABLE =
      new FailedInclusionListImportResult(FailureReason.STATE_UNAVAILABLE, Optional.empty());

  static InclusionListImportResult failedSlotCheckInvalid(final Optional<Throwable> cause) {
    return new FailedInclusionListImportResult(FailureReason.INCLUSION_LIST_SLOT, cause);
  }

  static InclusionListImportResult failedAttestationDeadline(final Optional<Throwable> cause) {
    return new FailedInclusionListImportResult(FailureReason.PAST_ATTESTATION_DEADLINE, cause);
  }

  static InclusionListImportResult failedCommitteeRootInvalid(final Optional<Throwable> cause) {
    return new FailedInclusionListImportResult(FailureReason.COMMITTEE_ROOT_MISMATCH, cause);
  }

  static InclusionListImportResult failedValidatorNotInCommittee(final Optional<Throwable> cause) {
    return new FailedInclusionListImportResult(FailureReason.VALIDATOR_NOT_IN_COMMITTEE, cause);
  }

  static InclusionListImportResult failedSignatureCheckInvalid(final Optional<Throwable> cause) {
    return new FailedInclusionListImportResult(FailureReason.INVALID_SIGNATURE, cause);
  }

  static InclusionListImportResult failedEquivocationCheck(final Optional<Throwable> cause) {
    return new FailedInclusionListImportResult(FailureReason.EQUIVOCATED, cause);
  }

  static InclusionListImportResult success(final SignedInclusionList signedInclusionList) {
    return new SuccessfulInclusionListImport(signedInclusionList);
  }

  enum FailureReason {
    INCLUSION_LIST_SLOT,
    PAST_ATTESTATION_DEADLINE,
    COMMITTEE_ROOT_MISMATCH,
    VALIDATOR_NOT_IN_COMMITTEE,
    INVALID_SIGNATURE,
    STATE_UNAVAILABLE,
    EQUIVOCATED,
    INTERNAL_ERROR // A catch-all category for unexpected errors (bugs)
  }

  boolean isSuccessful();

  default Optional<SignedInclusionList> getSignedInclusionList() {
    return Optional.empty();
  }

  default Optional<FailureReason> getFailureReason() {
    return Optional.empty();
  }

  default Optional<Throwable> getFailureCause() {
    return Optional.empty();
  }
}
