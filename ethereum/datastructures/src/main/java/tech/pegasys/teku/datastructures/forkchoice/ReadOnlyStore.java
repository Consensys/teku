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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public interface ReadOnlyStore {

  UnsignedLong getTime();

  UnsignedLong getGenesisTime();

  Checkpoint getJustifiedCheckpoint();

  Checkpoint getFinalizedCheckpoint();

  /**
   * Return the slot of the latest finalized block. This slot may be at or prior to the epoch
   * boundary slot which this block finalizes.
   *
   * @return the slot of the latest finalized block.
   */
  UnsignedLong getLatestFinalizedBlockSlot();

  SignedBlockAndState getLatestFinalizedBlockAndState();

  Checkpoint getBestJustifiedCheckpoint();

  boolean containsBlock(Bytes32 blockRoot);

  Set<Bytes32> getBlockRoots();

  /**
   * @return A list of block roots ordered to guarantee that parent roots will be sorted earlier
   *     than child roots
   */
  List<Bytes32> getOrderedBlockRoots();

  Set<UnsignedLong> getVotedValidatorIndices();

  /**
   * Returns a block state only if it is immediately available (not pruned).
   *
   * @param blockRoot The block root corresponding to the state to retrieve
   * @return The block state if available.
   */
  Optional<BeaconState> getBlockStateIfAvailable(Bytes32 blockRoot);

  /**
   * Returns a block only if it is immediately available (not pruned).
   *
   * @param blockRoot The block root of the block to retrieve
   * @return The block if available.
   */
  Optional<SignedBeaconBlock> getBlockIfAvailable(final Bytes32 blockRoot);

  default SafeFuture<Optional<BeaconBlock>> retrieveBlock(Bytes32 blockRoot) {
    return retrieveSignedBlock(blockRoot).thenApply(res -> res.map(SignedBeaconBlock::getMessage));
  }

  SafeFuture<Optional<SignedBeaconBlock>> retrieveSignedBlock(Bytes32 blockRoot);

  SafeFuture<Optional<SignedBlockAndState>> retrieveBlockAndState(Bytes32 blockRoot);

  SafeFuture<Optional<BeaconState>> retrieveBlockState(Bytes32 blockRoot);

  SafeFuture<Optional<BeaconState>> retrieveCheckpointState(Checkpoint checkpoint);
}
