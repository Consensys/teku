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

public interface WeakSubjectivityViolationPolicy {

  void onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
      final CheckpointState latestFinalizedCheckpoint,
      final int activeValidatorCount,
      final UInt64 currentSlot);

  void onChainInconsistentWithWeakSubjectivityCheckpoint(
      Checkpoint wsCheckpoint, SignedBeaconBlock block);

  void onFailedToPerformValidation(final String message);

  void onFailedToPerformValidation(final String message, Throwable error);
}
