/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.millisToSeconds;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface ReadOnlyStore {

  default UInt64 getTimeSeconds() {
    return millisToSeconds(getTimeMillis());
  }

  /**
   * Returns time in milliseconds to allow for more fine-grained time calculations
   *
   * @return the time in milliseconds
   */
  UInt64 getTimeMillis();

  UInt64 getGenesisTime();

  /** @return the genesis time in milliseconds */
  default UInt64 getGenesisTimeMillis() {
    return secondsToMillis(getGenesisTime());
  }

  /**
   * Returns the initial checkpoint from which the chain was started. If the checkpoint is missing,
   * the node was started up from genesis.
   *
   * @return The initial checkpoint if it exists.
   */
  Optional<Checkpoint> getInitialCheckpoint();

  Checkpoint getJustifiedCheckpoint();

  Checkpoint getFinalizedCheckpoint();

  /**
   * Return the slot of the latest finalized block. This slot may be at or prior to the epoch
   * boundary slot which this block finalizes.
   *
   * @return the slot of the latest finalized block.
   */
  UInt64 getLatestFinalizedBlockSlot();

  AnchorPoint getLatestFinalized();

  Optional<SlotAndExecutionPayloadSummary> getFinalizedOptimisticTransitionPayload();

  Checkpoint getBestJustifiedCheckpoint();

  Optional<Bytes32> getProposerBoostRoot();

  ReadOnlyForkChoiceStrategy getForkChoiceStrategy();

  boolean containsBlock(Bytes32 blockRoot);

  /**
   * @return A collection of block roots ordered to guarantee that parent roots will be sorted
   *     earlier than child roots
   */
  Collection<Bytes32> getOrderedBlockRoots();

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

  SafeFuture<Optional<StateAndBlockSummary>> retrieveStateAndBlockSummary(Bytes32 blockRoot);

  SafeFuture<Optional<BeaconState>> retrieveBlockState(Bytes32 blockRoot);

  SafeFuture<Optional<BeaconState>> retrieveCheckpointState(Checkpoint checkpoint);

  SafeFuture<Optional<BeaconState>> retrieveStateAtSlot(SlotAndBlockRoot checkpoint);

  SafeFuture<List<BlobSidecar>> retrieveBlobSidecars(SlotAndBlockRoot slotAndBlockRoot);

  SafeFuture<CheckpointState> retrieveFinalizedCheckpointAndState();

  SafeFuture<Optional<BeaconState>> retrieveCheckpointState(
      Checkpoint checkpoint, BeaconState latestStateAtEpoch);
}
