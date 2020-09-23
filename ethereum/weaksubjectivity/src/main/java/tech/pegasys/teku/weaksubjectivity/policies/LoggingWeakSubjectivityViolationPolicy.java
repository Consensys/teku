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

import java.util.Objects;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class LoggingWeakSubjectivityViolationPolicy implements WeakSubjectivityViolationPolicy {

  private static final Logger LOG = LogManager.getLogger();

  private final Level level;

  public LoggingWeakSubjectivityViolationPolicy(Level level) {
    this.level = level;
  }

  @Override
  public void onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
      final CheckpointState latestFinalizedCheckpoint,
      final int activeValidatorCount,
      final UInt64 currentSlot) {
    final UInt64 currentEpoch = compute_epoch_at_slot(currentSlot);
    LOG.log(
        level,
        "As of the current epoch {}, the latest finalized checkpoint at epoch {} ({} active validators) is outside of the weak subjectivity period.",
        currentEpoch,
        latestFinalizedCheckpoint.getEpoch(),
        activeValidatorCount);
  }

  @Override
  public void onChainInconsistentWithWeakSubjectivityCheckpoint(
      Checkpoint wsCheckpoint, SignedBeaconBlock block) {
    LOG.log(
        level,
        "Block {} at slot {} is inconsistent with weak subjectivity checkpoint {}",
        block.getRoot(),
        block.getSlot(),
        wsCheckpoint);
  }

  @Override
  public void onFailedToPerformValidation(final String message) {
    LOG.log(level, "Failed to perform weak subjectivity validation: {}", message);
  }

  @Override
  public void onFailedToPerformValidation(final String message, final Throwable error) {
    LOG.log(level, "Failed to perform weak subjectivity validation: " + message, error);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LoggingWeakSubjectivityViolationPolicy that = (LoggingWeakSubjectivityViolationPolicy) o;
    return level.equals(that.level);
  }

  @Override
  public int hashCode() {
    return Objects.hash(level);
  }
}
