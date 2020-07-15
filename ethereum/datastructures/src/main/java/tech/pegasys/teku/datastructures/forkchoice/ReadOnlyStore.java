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

package tech.pegasys.teku.datastructures.forkchoice;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointAndBlock;

public interface ReadOnlyStore {

  UnsignedLong getTime();

  UnsignedLong getGenesisTime();

  Checkpoint getJustifiedCheckpoint();

  Checkpoint getFinalizedCheckpoint();

  CheckpointAndBlock getFinalizedCheckpointAndBlock();

  /**
   * Return the slot of the latest finalized block. This slot may be at or prior to the epoch
   * boundary slot which this block finalizes.
   *
   * @return the slot of the latest finalized block.
   */
  UnsignedLong getLatestFinalizedBlockSlot();

  SignedBlockAndState getLatestFinalizedBlockAndState();

  Checkpoint getBestJustifiedCheckpoint();

  BeaconBlock getBlock(Bytes32 blockRoot);

  SignedBeaconBlock getSignedBlock(Bytes32 blockRoot);

  Optional<SignedBlockAndState> getBlockAndState(Bytes32 blockRoot);

  BeaconState getBlockState(Bytes32 blockRoot);

  boolean containsBlock(Bytes32 blockRoot);

  Set<Bytes32> getBlockRoots();

  Optional<BeaconState> getCheckpointState(Checkpoint checkpoint);

  Set<UnsignedLong> getVotedValidatorIndices();
}
