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

package tech.pegasys.teku.weaksubjectivity.policies;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.util.Objects;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class LoggingWeakSubjectivityViolationPolicy implements WeakSubjectivityViolationPolicy {
  private static final Logger LOG = LogManager.getLogger();
  private final Level level;

  public LoggingWeakSubjectivityViolationPolicy(Level level) {
    this.level = level;
  }

  @Override
  public void onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
      final CheckpointState latestFinalizedCheckpoint,
      final int activeValidatorCount,
      final UInt64 currentSlot,
      final UInt64 wsPeriod) {
    final UInt64 currentEpoch = compute_epoch_at_slot(currentSlot);
    LOG.log(
        level,
        "The latest finalized checkpoint at epoch {} ({} active validators) fell outside of the weak subjectivity period after epoch {}, which is prior to the current epoch {}.",
        latestFinalizedCheckpoint.getEpoch(),
        activeValidatorCount,
        latestFinalizedCheckpoint.getEpoch().plus(wsPeriod),
        currentEpoch);
    STATUS_LOG.finalizedCheckpointOutsideOfWeakSubjectivityPeriod(
        level, latestFinalizedCheckpoint.getEpoch());
  }

  @Override
  public void onChainInconsistentWithWeakSubjectivityCheckpoint(
      Checkpoint wsCheckpoint, SignedBeaconBlock block) {
    STATUS_LOG.chainInconsistentWithWeakSubjectivityCheckpoint(
        level, block.getRoot(), block.getSlot(), wsCheckpoint.getRoot(), wsCheckpoint.getEpoch());
  }

  @Override
  public void onFailedToPerformValidation(final String message) {
    STATUS_LOG.failedToPerformWeakSubjectivityValidation(level, message);
  }

  @Override
  public void onFailedToPerformValidation(final String message, final Throwable error) {
    STATUS_LOG.failedToPerformWeakSubjectivityValidation(level, message, error);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final LoggingWeakSubjectivityViolationPolicy that = (LoggingWeakSubjectivityViolationPolicy) o;
    return Objects.equals(level, that.level);
  }

  @Override
  public int hashCode() {
    return Objects.hash(level);
  }
}
