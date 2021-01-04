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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class TestStoreImpl implements MutableStore {
  protected UInt64 time;
  protected UInt64 genesis_time;
  protected final Optional<Checkpoint> initialCheckpoint;
  protected Checkpoint justified_checkpoint;
  protected Checkpoint finalized_checkpoint;
  protected Checkpoint best_justified_checkpoint;
  protected Map<Bytes32, SignedBeaconBlock> blocks;
  protected Map<Bytes32, BeaconState> block_states;
  protected Map<Checkpoint, BeaconState> checkpoint_states;
  protected Map<UInt64, VoteTracker> votes;

  TestStoreImpl(
      final UInt64 time,
      final UInt64 genesis_time,
      final Optional<Checkpoint> initialCheckpoint,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final Map<Bytes32, BeaconState> block_states,
      final Map<Checkpoint, BeaconState> checkpoint_states,
      final Map<UInt64, VoteTracker> votes) {
    this.time = time;
    this.genesis_time = genesis_time;
    this.initialCheckpoint = initialCheckpoint;
    this.justified_checkpoint = justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
    this.best_justified_checkpoint = best_justified_checkpoint;
    this.blocks = blocks;
    this.block_states = block_states;
    this.checkpoint_states = checkpoint_states;
    this.votes = votes;
  }

  // Readonly methods
  @Override
  public UInt64 getTime() {
    return time;
  }

  @Override
  public UInt64 getGenesisTime() {
    return genesis_time;
  }

  @Override
  public Optional<Checkpoint> getInitialCheckpoint() {
    return initialCheckpoint;
  }

  @Override
  public Checkpoint getJustifiedCheckpoint() {
    return justified_checkpoint;
  }

  @Override
  public Checkpoint getFinalizedCheckpoint() {
    return finalized_checkpoint;
  }

  @Override
  public UInt64 getLatestFinalizedBlockSlot() {
    return blocks.get(finalized_checkpoint.getRoot()).getSlot();
  }

  @Override
  public AnchorPoint getLatestFinalized() {
    final SignedBeaconBlock block = getSignedBlock(finalized_checkpoint.getRoot());
    final BeaconState state = getBlockState(finalized_checkpoint.getRoot());
    return AnchorPoint.create(finalized_checkpoint, block, state);
  }

  @Override
  public Checkpoint getBestJustifiedCheckpoint() {
    return best_justified_checkpoint;
  }

  @Override
  public ReadOnlyForkChoiceStrategy getForkChoiceStrategy() {
    throw new UnsupportedOperationException("Not implemented");
  }

  private SignedBeaconBlock getSignedBlock(final Bytes32 blockRoot) {
    return blocks.get(blockRoot);
  }

  private Optional<SignedBlockAndState> getBlockAndState(final Bytes32 blockRoot) {
    final SignedBeaconBlock block = getSignedBlock(blockRoot);
    final BeaconState state = getBlockState(blockRoot);
    if (block == null || state == null) {
      return Optional.empty();
    }
    return Optional.of(new SignedBlockAndState(block, state));
  }

  @Override
  public boolean containsBlock(final Bytes32 blockRoot) {
    return blocks.containsKey(blockRoot);
  }

  @Override
  public List<Bytes32> getOrderedBlockRoots() {
    return blocks.values().stream()
        .sorted(Comparator.comparing(SignedBeaconBlock::getSlot))
        .map(SignedBeaconBlock::getRoot)
        .collect(Collectors.toList());
  }

  private BeaconState getBlockState(final Bytes32 blockRoot) {
    return block_states.get(blockRoot);
  }

  private Optional<BeaconState> getCheckpointState(final Checkpoint checkpoint) {
    return Optional.ofNullable(checkpoint_states.get(checkpoint));
  }

  @Override
  public Set<UInt64> getVotedValidatorIndices() {
    return votes.keySet();
  }

  // Prunable methods
  @Override
  public Optional<BeaconState> getBlockStateIfAvailable(Bytes32 blockRoot) {
    return Optional.ofNullable(getBlockState(blockRoot));
  }

  @Override
  public Optional<SignedBeaconBlock> getBlockIfAvailable(Bytes32 blockRoot) {
    return Optional.ofNullable(getSignedBlock(blockRoot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> retrieveSignedBlock(Bytes32 blockRoot) {
    return SafeFuture.completedFuture(getBlockIfAvailable(blockRoot));
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> retrieveBlockAndState(Bytes32 blockRoot) {
    return SafeFuture.completedFuture(getBlockAndState(blockRoot));
  }

  @Override
  public SafeFuture<Optional<StateAndBlockSummary>> retrieveStateAndBlockSummary(
      final Bytes32 blockRoot) {
    return retrieveBlockAndState(blockRoot).thenApply(res -> res.map(a -> a));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveBlockState(Bytes32 blockRoot) {
    return SafeFuture.completedFuture(getBlockStateIfAvailable(blockRoot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(Checkpoint checkpoint) {
    return SafeFuture.completedFuture(getCheckpointState(checkpoint));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveStateAtSlot(
      final SlotAndBlockRoot slotAndBlockRoot) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public SafeFuture<CheckpointState> retrieveFinalizedCheckpointAndState() {
    final BeaconState state = getCheckpointState(finalized_checkpoint).orElseThrow();
    final SignedBeaconBlock block = getSignedBlock(finalized_checkpoint.getRoot());
    return SafeFuture.completedFuture(CheckpointState.create(finalized_checkpoint, block, state));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(
      final Checkpoint checkpoint, final BeaconState latestStateAtEpoch) {
    if (!latestStateAtEpoch.getSlot().equals(checkpoint.getEpochStartSlot())) {
      throw new UnsupportedOperationException("Checkpoint state calculation not supported");
    }
    return SafeFuture.completedFuture(Optional.of(latestStateAtEpoch));
  }

  // Mutable methods
  @Override
  public void putBlockAndState(final SignedBeaconBlock block, final BeaconState state) {
    blocks.put(block.getRoot(), block);
    block_states.put(block.getRoot(), state);
  }

  @Override
  public void putStateRoot(final Bytes32 stateRoot, final SlotAndBlockRoot slotAndBlockRoot) {
    // NO-OP
  }

  @Override
  public void putBlockAndState(final SignedBlockAndState blockAndState) {
    blocks.put(blockAndState.getRoot(), blockAndState.getBlock());
    block_states.put(blockAndState.getRoot(), blockAndState.getState());
  }

  @Override
  public void setTime(final UInt64 time) {
    this.time = time;
  }

  @Override
  public void setGenesis_time(final UInt64 genesis_time) {
    this.genesis_time = genesis_time;
  }

  @Override
  public void setJustifiedCheckpoint(final Checkpoint justified_checkpoint) {
    this.justified_checkpoint = justified_checkpoint;
  }

  @Override
  public void setFinalizedCheckpoint(final Checkpoint finalized_checkpoint) {
    this.finalized_checkpoint = finalized_checkpoint;
  }

  @Override
  public void setBestJustifiedCheckpoint(final Checkpoint best_justified_checkpoint) {
    this.best_justified_checkpoint = best_justified_checkpoint;
  }

  @Override
  public VoteTracker getVote(final UInt64 validatorIndex) {
    VoteTracker vote = votes.get(validatorIndex);
    if (vote == null) {
      vote = VoteTracker.Default();
    }
    this.votes.put(validatorIndex, vote);
    return vote;
  }

  @Override
  public Bytes32 applyForkChoiceScoreChanges(
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final BeaconState justifiedCheckpointState) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
