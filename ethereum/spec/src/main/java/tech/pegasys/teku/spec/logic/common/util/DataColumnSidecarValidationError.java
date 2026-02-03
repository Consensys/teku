/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.common.util;

import static tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError.ResultType.BAD_PROPOSER;
import static tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError.ResultType.BAD_SIGNATURE;
import static tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError.ResultType.BAD_SLOT;
import static tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError.ResultType.BLOCK_UNAVAILABLE;
import static tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError.ResultType.DUPLICATE;
import static tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError.ResultType.FROM_FUTURE;
import static tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError.ResultType.INVALID_BLOCK;
import static tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError.ResultType.INVALID_KZG_COMMITMENTS;
import static tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError.ResultType.SLOT_NOT_FINALIZED;
import static tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError.ResultType.STATE_UNAVAILABLE;

import java.util.function.Supplier;

public class DataColumnSidecarValidationError {

  public enum ResultType {
    BAD_SLOT,
    INVALID_BLOCK,
    INVALID_KZG_COMMITMENTS,
    DUPLICATE,
    STATE_UNAVAILABLE,
    BLOCK_UNAVAILABLE,
    BAD_SIGNATURE,
    BAD_PROPOSER,
    FROM_FUTURE,
    SLOT_NOT_FINALIZED
  }

  private final ResultType type;
  private final Supplier<String> reason;

  private DataColumnSidecarValidationError(final ResultType type, final Supplier<String> reason) {
    this.type = type;
    this.reason = reason;
  }

  public static DataColumnSidecarValidationError invalidSlot(final String reason) {
    return new DataColumnSidecarValidationError(BAD_SLOT, () -> reason);
  }

  public static DataColumnSidecarValidationError badSignature(final String reason) {
    return new DataColumnSidecarValidationError(BAD_SIGNATURE, () -> reason);
  }

  public static DataColumnSidecarValidationError badProposer(final String reason) {
    return new DataColumnSidecarValidationError(BAD_PROPOSER, () -> reason);
  }

  public static DataColumnSidecarValidationError invalidBlock(final String reason) {
    return new DataColumnSidecarValidationError(INVALID_BLOCK, () -> reason);
  }

  public static DataColumnSidecarValidationError invalidKzgCommitments(final String reason) {
    return new DataColumnSidecarValidationError(INVALID_KZG_COMMITMENTS, () -> reason);
  }

  public static DataColumnSidecarValidationError duplicate(final String reason) {
    return new DataColumnSidecarValidationError(DUPLICATE, () -> reason);
  }

  public static DataColumnSidecarValidationError stateUnavailable(final String reason) {
    return new DataColumnSidecarValidationError(STATE_UNAVAILABLE, () -> reason);
  }

  public static DataColumnSidecarValidationError blockUnavailable(final String reason) {
    return new DataColumnSidecarValidationError(BLOCK_UNAVAILABLE, () -> reason);
  }

  public static DataColumnSidecarValidationError fromFuture(final String reason) {
    return new DataColumnSidecarValidationError(FROM_FUTURE, () -> reason);
  }

  public static DataColumnSidecarValidationError slotNotFinalized(final String reason) {
    return new DataColumnSidecarValidationError(SLOT_NOT_FINALIZED, () -> reason);
  }

  public ResultType getType() {
    return type;
  }

  public String getReason() {
    return reason.get();
  }
}
