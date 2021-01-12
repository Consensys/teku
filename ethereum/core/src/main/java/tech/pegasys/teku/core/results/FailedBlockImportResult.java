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

package tech.pegasys.teku.core.results;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import java.util.Optional;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;

public class FailedBlockImportResult implements BlockImportResult {
  private final FailureReason failureReason;
  private final Optional<Throwable> cause;

  @VisibleForTesting
  public FailedBlockImportResult(
      final FailureReason failureReason, final Optional<Throwable> cause) {
    this.failureReason = failureReason;
    this.cause = cause;
  }

  @Override
  public boolean isSuccessful() {
    return false;
  }

  @Override
  public SignedBeaconBlock getBlock() {
    return null;
  }

  @Override
  public FailureReason getFailureReason() {
    return failureReason;
  }

  @Override
  public Optional<Throwable> getFailureCause() {
    return cause;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("failureReason", failureReason)
        .add("cause", cause)
        .toString();
  }
}
