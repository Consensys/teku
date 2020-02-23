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

import java.util.Optional;
import tech.pegasys.artemis.data.BlockProcessingRecord;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;

public interface BlockImportResult {
  BlockImportResult FAILED_BLOCK_IS_FROM_FUTURE =
      new FailedBlockImportResult(FailureReason.BLOCK_IS_FROM_FUTURE, Optional.empty());
  BlockImportResult FAILED_UNKNOWN_PARENT =
      new FailedBlockImportResult(FailureReason.UNKNOWN_PARENT, Optional.empty());
  BlockImportResult FAILED_INVALID_ANCESTRY =
      new FailedBlockImportResult(
          FailureReason.DOES_NOT_DESCEND_FROM_LATEST_FINALIZED, Optional.empty());

  static BlockImportResult failedStateTransition(final Exception cause) {
    return new FailedBlockImportResult(FailureReason.FAILED_STATE_TRANSITION, Optional.of(cause));
  }

  static BlockImportResult internalError(final Exception cause) {
    return new FailedBlockImportResult(FailureReason.INTERNAL_ERROR, Optional.of(cause));
  }

  static BlockImportResult successful(final BlockProcessingRecord record) {
    return new SuccessfulBlockImportResult(record.getBlock(), Optional.of(record));
  }

  static BlockImportResult knownBlock(final SignedBeaconBlock block) {
    return new SuccessfulBlockImportResult(block, Optional.empty());
  }

  enum FailureReason {
    UNKNOWN_PARENT,
    BLOCK_IS_FROM_FUTURE,
    DOES_NOT_DESCEND_FROM_LATEST_FINALIZED,
    FAILED_STATE_TRANSITION,
    INTERNAL_ERROR // A catch-all category for unexpected errors (bugs)
  }

  boolean isSuccessful();

  /**
   * @return If unsuccessful, returns an empty result. If successful, the {@code
   *     BlockProcessingRecord} will be available if such a record was produced during import. In
   *     the case where we attempt to import a known block, no {@code BlockProcessingRecord} will be
   *     produced.
   */
  Optional<BlockProcessingRecord> getBlockProcessingRecord();

  /** @return If successful, returns a {@code SignedBeaconBlock}, otherwise returns null. */
  SignedBeaconBlock getBlock();

  /** @return If failed, returns a non-null failure reason, otherwise returns null. */
  FailureReason getFailureReason();

  /**
   * @return If failed, may return a {@code Throwable} cause. If successful, this value is always
   *     empty.
   */
  Optional<Throwable> getFailureCause();
}
