/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.statetransition.blockimport;

import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.util.exceptions.FutureBlockException;
import tech.pegasys.artemis.statetransition.util.exceptions.InvalidBlockAncestryException;
import tech.pegasys.artemis.statetransition.util.exceptions.UnknownParentBlockException;

public interface BlockImportResult {
  BlockImportResult SUCCESSFUL_RESULT = new SuccessfulBlockImportResult();

  static BlockImportResult create(FutureBlockException exception) {
    return new FailedBlockImportResult(FailureReason.BLOCK_IS_FROM_FUTURE, exception);
  }

  static BlockImportResult create(UnknownParentBlockException exception) {
    return new FailedBlockImportResult(FailureReason.UNKNOWN_PARENT, exception);
  }

  static BlockImportResult create(InvalidBlockAncestryException exception) {
    return new FailedBlockImportResult(FailureReason.INVALID_ANCESTRY, exception);
  }

  static BlockImportResult create(StateTransitionException exception) {
    return new FailedBlockImportResult(FailureReason.FAILED_STATE_TRANSITION, exception);
  }

  enum FailureReason {
    UNKNOWN_PARENT,
    BLOCK_IS_FROM_FUTURE,
    INVALID_ANCESTRY,
    FAILED_STATE_TRANSITION
  }

  boolean isSuccessful();

  FailureReason getFailureReason();

  Throwable getFailureCause();
}
