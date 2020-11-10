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

package tech.pegasys.teku.core.stategenerator;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;

public abstract class CheckpointStateGenerator {

  public static CheckpointState generate(
      final Checkpoint checkpoint, final SignedBlockAndState blockAndState) {
    checkArgument(
        blockAndState.getRoot().equals(checkpoint.getRoot()), "Block must match checkpoint root");

    // Derive checkpoint state
    final BeaconState state = regenerateCheckpointState(checkpoint, blockAndState.getState());
    return CheckpointState.create(checkpoint, blockAndState.getBlock(), state);
  }

  public static BeaconState regenerateCheckpointState(
      final Checkpoint checkpoint, BeaconState baseState) {
    if (baseState.getSlot().isGreaterThan(checkpoint.getEpochStartSlot())) {
      throw new InvalidCheckpointException(
          "Checkpoint state must be at or prior to checkpoint slot boundary");
    }
    try {
      if (baseState.getSlot().equals(checkpoint.getEpochStartSlot())) {
        return baseState;
      }

      return new StateTransition().process_slots(baseState, checkpoint.getEpochStartSlot());
    } catch (SlotProcessingException | EpochProcessingException | IllegalArgumentException e) {
      throw new InvalidCheckpointException(e);
    }
  }
}
