/*
 * Copyright Consensys Software Inc., 2022
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
import tech.pegasys.teku.infrastructure.time.TimeProvider;
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

public interface ReadOnlyStore extends TimeProvider {

  default UInt64 getTimeSeconds() {
    return millisToSeconds(getTimeInMillis());
  }

  UInt64 getGenesisTime();

  /**
   * @return the genesis time in milliseconds
   */
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
  Optional<SignedBeaconBlock> getBlockIfAvailable(Bytes32 blockRoot);

  Optional<List<BlobSidecar>> getBlobSidecarsIfAvailable(SlotAndBlockRoot slotAndBlockRoot);

  default SafeFuture<Optional<BeaconBlock>> retrieveBlock(final Bytes32 blockRoot) {
    return retrieveSignedBlock(blockRoot).thenApply(res -> res.map(SignedBeaconBlock::getMessage));
  }

  SafeFuture<Optional<SignedBeaconBlock>> retrieveSignedBlock(Bytes32 blockRoot);

  SafeFuture<Optional<SignedBlockAndState>> retrieveBlockAndState(Bytes32 blockRoot);

  SafeFuture<Optional<StateAndBlockSummary>> retrieveStateAndBlockSummary(Bytes32 blockRoot);

  SafeFuture<Optional<BeaconState>> retrieveBlockState(Bytes32 blockRoot);

  SafeFuture<Optional<BeaconState>> retrieveCheckpointState(Checkpoint checkpoint);

  SafeFuture<Optional<BeaconState>> retrieveStateAtSlot(SlotAndBlockRoot checkpoint);

  SafeFuture<Optional<UInt64>> retrieveEarliestBlobSidecarSlot();

  SafeFuture<CheckpointState> retrieveFinalizedCheckpointAndState();

  SafeFuture<Optional<BeaconState>> retrieveCheckpointState(
      Checkpoint checkpoint, BeaconState latestStateAtEpoch);

  // implements is_head_weak from fork-choice Consensus Spec
  boolean isHeadWeak(Bytes32 root);

  // implements is_parent_strong from fork-choice Consensus Spec
  boolean isParentStrong(Bytes32 parentRoot);

  void computeBalanceThresholds(BeaconState justifiedState);

  // implements is_ffg_competitive from Consensus Spec
  Optional<Boolean> isFfgCompetitive(Bytes32 headRoot, Bytes32 parentRoot);
}
