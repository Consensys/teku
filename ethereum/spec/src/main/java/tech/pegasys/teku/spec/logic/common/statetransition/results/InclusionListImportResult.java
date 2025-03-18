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

  InclusionListImportResult FAILED_PAST_INCLUSION_LIST_DEADLINE =
      new FailedInclusionListImportResult(
          FailureReason.PAST_INCLUSION_LIST_DEADLINE, Optional.empty());
  InclusionListImportResult FAILED_PAST_ATTESTING_DEADLINE =
      new FailedInclusionListImportResult(
          FailureReason.PAST_ATTESTATION_DEADLINE, Optional.empty());
  InclusionListImportResult FAILED_EQUIVOCATED =
      new FailedInclusionListImportResult(FailureReason.EQUIVOCATED, Optional.empty());

  static InclusionListImportResult success(final SignedInclusionList signedInclusionList) {
    return new SuccessfulInclusionListImport(signedInclusionList);
  }

  enum FailureReason {
    PAST_INCLUSION_LIST_DEADLINE,
    PAST_ATTESTATION_DEADLINE,
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
