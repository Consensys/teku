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

import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StrictWeakSubjectivityViolationPolicy implements WeakSubjectivityViolationPolicy {

  @Override
  public void onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
      CheckpointState latestFinalizedCheckpoint, int activeValidatorCount, UInt64 currentSlot) {
    exitClient();
  }

  @Override
  public void onChainInconsistentWithWeakSubjectivityCheckpoint(
      Checkpoint wsCheckpoint, SignedBeaconBlock block) {
    exitClient();
  }

  @Override
  public void onFailedToPerformValidation(final String message) {
    exitClient();
  }

  @Override
  public void onFailedToPerformValidation(final String message, final Throwable error) {
    exitClient();
  }

  private void exitClient() {
    System.exit(2);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    return o != null && getClass() == o.getClass();
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
