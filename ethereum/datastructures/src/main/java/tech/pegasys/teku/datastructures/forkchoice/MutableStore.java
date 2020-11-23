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

package tech.pegasys.teku.datastructures.forkchoice;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface MutableStore extends ReadOnlyStore {

  void putBlockAndState(SignedBeaconBlock block, BeaconState state);

  default void putBlockAndState(SignedBlockAndState blockAndState) {
    putBlockAndState(blockAndState.getBlock(), blockAndState.getState());
  }

  void putStateRoot(Bytes32 stateRoot, SlotAndBlockRoot slotAndBlockRoot);

  void setTime(UInt64 time);

  void setGenesis_time(UInt64 genesis_time);

  void setJustifiedCheckpoint(Checkpoint justified_checkpoint);

  void setFinalizedCheckpoint(Checkpoint finalized_checkpoint);

  void setBestJustifiedCheckpoint(Checkpoint best_justified_checkpoint);

  VoteTracker getVote(UInt64 validatorIndex);

  Bytes32 applyForkChoiceScoreChanges(
      Checkpoint finalizedCheckpoint,
      Checkpoint justifiedCheckpoint,
      BeaconState justifiedCheckpointState);
}
