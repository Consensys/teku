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

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class TestStoreImpl implements MutableStore, VoteUpdater {
  private final Spec spec;
  protected UInt64 timeMillis;
  protected UInt64 genesisTime;
  protected final Optional<Checkpoint> initialCheckpoint;
  protected Checkpoint justifiedCheckpoint;
  protected Checkpoint finalizedCheckpoint;
  protected Checkpoint bestJustifiedCheckpoint;
  protected Map<Bytes32, SignedBeaconBlock> blocks;
  protected Map<Bytes32, BeaconState> blockStates;
  protected Map<Bytes32, BlockCheckpoints> blockCheckpoints;
  protected Map<Checkpoint, BeaconState> checkpointStates;
  protected Map<UInt64, VoteTracker> votes;
  protected Optional<Bytes32> proposerBoostRoot = Optional.empty();
  protected final TestReadOnlyForkChoiceStrategy forkChoiceStrategy =
      new TestReadOnlyForkChoiceStrategy();

  TestStoreImpl(
      final Spec spec,
      final UInt64 time,
      final UInt64 genesisTime,
      final Optional<Checkpoint> initialCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint bestJustifiedCheckpoint,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final Map<Bytes32, BeaconState> blockStates,
      final Map<Bytes32, BlockCheckpoints> blockCheckpoints,
      final Map<Checkpoint, BeaconState> checkpointStates,
      final Map<UInt64, VoteTracker> votes) {
    this.spec = spec;
    this.timeMillis = secondsToMillis(time);
    this.genesisTime = genesisTime;
    this.initialCheckpoint = initialCheckpoint;
    this.justifiedCheckpoint = justifiedCheckpoint;
    this.finalizedCheckpoint = finalizedCheckpoint;
    this.bestJustifiedCheckpoint = bestJustifiedCheckpoint;
    this.blocks = blocks;
    this.blockStates = blockStates;
    this.blockCheckpoints = blockCheckpoints;
    this.checkpointStates = checkpointStates;
    this.votes = votes;
  }

  // Readonly methods
  @Override
  public UInt64 getTimeMillis() {
    return timeMillis;
  }

  @Override
  public UInt64 getGenesisTime() {
    return genesisTime;
  }

  @Override
  public Optional<Checkpoint> getInitialCheckpoint() {
    return initialCheckpoint;
  }

  @Override
  public Checkpoint getJustifiedCheckpoint() {
    return justifiedCheckpoint;
  }

  @Override
  public Checkpoint getFinalizedCheckpoint() {
    return finalizedCheckpoint;
  }

  @Override
  public UInt64 getLatestFinalizedBlockSlot() {
    return blocks.get(finalizedCheckpoint.getRoot()).getSlot();
  }

  @Override
  public AnchorPoint getLatestFinalized() {
    final SignedBeaconBlock block = getSignedBlock(finalizedCheckpoint.getRoot());
    final BeaconState state = getBlockState(finalizedCheckpoint.getRoot());
    return AnchorPoint.create(spec, finalizedCheckpoint, block, state);
  }

  @Override
  public Optional<SlotAndExecutionPayloadSummary> getFinalizedOptimisticTransitionPayload() {
    return Optional.empty();
  }

  @Override
  public Checkpoint getBestJustifiedCheckpoint() {
    return bestJustifiedCheckpoint;
  }

  @Override
  public Optional<Bytes32> getProposerBoostRoot() {
    return proposerBoostRoot;
  }

  @Override
  public ReadOnlyForkChoiceStrategy getForkChoiceStrategy() {
    return forkChoiceStrategy;
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
        .collect(toList());
  }

  private BeaconState getBlockState(final Bytes32 blockRoot) {
    return blockStates.get(blockRoot);
  }

  private Optional<BeaconState> getCheckpointState(final Checkpoint checkpoint) {
    return Optional.ofNullable(checkpointStates.get(checkpoint));
  }

  @Override
  public UInt64 getHighestVotedValidatorIndex() {
    return votes.keySet().stream().max(Comparator.naturalOrder()).orElse(UInt64.ZERO);
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
    final BeaconState state = getCheckpointState(finalizedCheckpoint).orElseThrow();
    final SignedBeaconBlock block = getSignedBlock(finalizedCheckpoint.getRoot());
    return SafeFuture.completedFuture(
        CheckpointState.create(spec, finalizedCheckpoint, block, state));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(
      final Checkpoint checkpoint, final BeaconState latestStateAtEpoch) {
    if (!latestStateAtEpoch.getSlot().equals(checkpoint.getEpochStartSlot(spec))) {
      throw new UnsupportedOperationException("Checkpoint state calculation not supported");
    }
    return SafeFuture.completedFuture(Optional.of(latestStateAtEpoch));
  }

  // Mutable methods
  @Override
  public void putBlockAndState(
      final SignedBeaconBlock block, final BeaconState state, final BlockCheckpoints checkpoints) {
    blocks.put(block.getRoot(), block);
    blockStates.put(block.getRoot(), state);
    blockCheckpoints.put(block.getRoot(), checkpoints);
  }

  @Override
  public void putStateRoot(final Bytes32 stateRoot, final SlotAndBlockRoot slotAndBlockRoot) {
    // NO-OP
  }

  @Override
  public void pullUpBlockCheckpoints(final Bytes32 blockRoot) {
    final BlockCheckpoints blockCheckpoints = this.blockCheckpoints.get(blockRoot);
    if (blockCheckpoints == null) {
      return;
    }
    this.blockCheckpoints.put(blockRoot, blockCheckpoints.realizeNextEpoch());
  }

  @Override
  public void setTimeMillis(final UInt64 time) {
    this.timeMillis = time;
  }

  @Override
  public void setGenesisTime(final UInt64 genesisTime) {
    this.genesisTime = genesisTime;
  }

  @Override
  public void setJustifiedCheckpoint(final Checkpoint justifiedCheckpoint) {
    this.justifiedCheckpoint = justifiedCheckpoint;
  }

  @Override
  public void setFinalizedCheckpoint(
      final Checkpoint finalizedCheckpoint, final boolean fromOptimisticBlock) {
    this.finalizedCheckpoint = finalizedCheckpoint;
  }

  @Override
  public void setBestJustifiedCheckpoint(final Checkpoint bestJustifiedCheckpoint) {
    this.bestJustifiedCheckpoint = bestJustifiedCheckpoint;
  }

  @Override
  public void setProposerBoostRoot(final Bytes32 boostedBlockRoot) {
    proposerBoostRoot = Optional.of(boostedBlockRoot);
  }

  @Override
  public void removeFinalizedOptimisticTransitionPayload() {}

  @Override
  public void removeProposerBoostRoot() {
    proposerBoostRoot = Optional.empty();
  }

  @Override
  public VoteTracker getVote(final UInt64 validatorIndex) {
    VoteTracker vote = votes.get(validatorIndex);
    return vote != null ? vote : VoteTracker.DEFAULT;
  }

  @Override
  public void putVote(UInt64 validatorIndex, VoteTracker vote) {
    votes.put(validatorIndex, vote);
  }

  @Override
  public void commit() {}

  @Override
  public Bytes32 applyForkChoiceScoreChanges(
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final List<UInt64> justifiedCheckpointEffectiveBalances,
      final Optional<Bytes32> proposerBoostRoot,
      final UInt64 proposerScoreBoostAmount) {
    throw new UnsupportedOperationException("Not implemented");
  }

  private class TestReadOnlyForkChoiceStrategy implements ReadOnlyForkChoiceStrategy {

    @Override
    public Optional<UInt64> blockSlot(final Bytes32 blockRoot) {
      return Optional.ofNullable(blocks.get(blockRoot)).map(SignedBeaconBlock::getSlot);
    }

    @Override
    public Optional<Bytes32> blockParentRoot(final Bytes32 blockRoot) {
      return Optional.ofNullable(blocks.get(blockRoot)).map(SignedBeaconBlock::getParentRoot);
    }

    @Override
    public Optional<Bytes32> executionBlockHash(final Bytes32 blockRoot) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Optional<Bytes32> getAncestor(final Bytes32 blockRoot, final UInt64 slot) {
      return Optional.empty();
    }

    @Override
    public Optional<SlotAndBlockRoot> findCommonAncestor(
        final Bytes32 blockRoot1, final Bytes32 blockRoot2) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Bytes32> getBlockRootsAtSlot(final UInt64 slot) {
      return blocks.values().stream()
          .filter(block -> block.getSlot().equals(slot))
          .map(SignedBeaconBlock::getRoot)
          .collect(toList());
    }

    @Override
    public List<ProtoNodeData> getChainHeads() {
      final Map<Bytes32, ProtoNodeData> headsByRoot = new HashMap<>();
      getOrderedBlockRoots()
          .forEach(
              root -> {
                final SignedBeaconBlock block = blocks.get(root);
                headsByRoot.put(
                    root,
                    new ProtoNodeData(
                        block.getSlot(),
                        root,
                        block.getParentRoot(),
                        block.getStateRoot(),
                        block
                            .getMessage()
                            .getBody()
                            .getOptionalExecutionPayload()
                            .map(ExecutionPayload::getBlockHash)
                            .orElse(Bytes32.ZERO),
                        false,
                        blockCheckpoints.get(root)));
                headsByRoot.remove(block.getParentRoot());
              });
      return new ArrayList<>(headsByRoot.values());
    }

    @Override
    public Optional<Bytes32> getOptimisticallySyncedTransitionBlockRoot(final Bytes32 head) {
      return Optional.empty();
    }

    @Override
    public List<Map<String, String>> getNodeData() {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean contains(final Bytes32 blockRoot) {
      return blocks.containsKey(blockRoot);
    }

    @Override
    public Optional<Boolean> isOptimistic(final Bytes32 blockRoot) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean isFullyValidated(final Bytes32 blockRoot) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Optional<ProtoNodeData> getBlockData(final Bytes32 blockRoot) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Optional<UInt64> getWeight(final Bytes32 blockRoot) {
      throw new UnsupportedOperationException("Not implemented");
    }
  }
}
