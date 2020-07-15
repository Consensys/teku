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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.util.async.SafeFuture;

public interface PrunableStore extends ReadOnlyStore {
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
