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

package tech.pegasys.teku.protoarray;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.addExact;
import static java.lang.Math.subtractExact;
import static java.lang.Math.toIntExact;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.MutableForkChoiceState;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;

public class ProtoArrayForkChoiceStrategyUpdater implements MutableForkChoiceState {
  private static final Logger LOG = LogManager.getLogger();

  private final ProtoArrayForkChoiceStrategy forkChoice;

  final VoteUpdater votes;
  final Map<Bytes32, SignedBlockAndState> newBlocks = new HashMap<>();
  Optional<Bytes32> finalizedRoot = Optional.empty();
  Optional<HeadUpdates> headUpdates = Optional.empty();

  public ProtoArrayForkChoiceStrategyUpdater(
      final ProtoArrayForkChoiceStrategy protoArrayForkChoiceStrategy,
      Map<UnsignedLong, VoteTracker> votes) {
    this.forkChoice = protoArrayForkChoiceStrategy;
    this.votes = new VoteUpdater(votes);
  }

  public void commit() {
    // Process blocks
    newBlocks.values().stream()
        .sorted(Comparator.comparing(SignedBlockAndState::getSlot))
        .forEach(
            blockAndState -> {
              final SignedBeaconBlock block = blockAndState.getBlock();
              final BeaconState state = blockAndState.getState();
              processBlock(
                  block.getSlot(),
                  blockAndState.getRoot(),
                  block.getParent_root(),
                  block.getStateRoot(),
                  state.getCurrent_justified_checkpoint().getEpoch(),
                  state.getFinalized_checkpoint().getEpoch());
            });

    finalizedRoot.ifPresent(
        root -> {
          forkChoice.protoArrayLock.writeLock().lock();
          try {
            forkChoice.protoArray.maybePrune(root);
          } finally {
            forkChoice.protoArrayLock.writeLock().unlock();
          }
        });

    // Process head updates
    headUpdates.ifPresent(this::updateForkChoiceWeights);

    // Commit vote changes
    forkChoice.votesLock.writeLock().lock();
    try {
      votes.applyUpdates();
    } finally {
      forkChoice.votesLock.writeLock().unlock();
    }
  }

  public Map<UnsignedLong, VoteTracker> getVotes() {
    return votes.getVotes();
  }

  @Override
  public void updateHead(
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final BeaconState justifiedCheckpointState) {
    checkState(headUpdates.isEmpty(), "Head is already updated");

    forkChoice.protoArrayLock.readLock().lock();
    forkChoice.balancesLock.readLock().lock();
    try {
      List<UnsignedLong> oldBalances = forkChoice.balances;
      List<UnsignedLong> newBalances = justifiedCheckpointState.getBalances().asList();

      List<Long> deltas =
          computeDeltas(votes, forkChoice.protoArray.getIndices(), oldBalances, newBalances);
      this.headUpdates =
          Optional.of(
              new HeadUpdates(
                  finalizedCheckpoint, justifiedCheckpoint, justifiedCheckpointState, deltas));
    } finally {
      forkChoice.protoArrayLock.readLock().unlock();
      forkChoice.balancesLock.readLock().unlock();
    }
  }

  @Override
  public void onBlock(final SignedBlockAndState blockAndState) {
    this.newBlocks.put(blockAndState.getRoot(), blockAndState);
  }

  @Override
  public void onAttestation(final IndexedAttestation attestation) {
    attestation.getAttesting_indices().stream()
        .parallel()
        .forEach(
            validatorIndex -> {
              processAttestation(
                  validatorIndex,
                  attestation.getData().getBeacon_block_root(),
                  attestation.getData().getTarget().getEpoch());
            });
  }

  @VisibleForTesting
  void processAttestation(
      UnsignedLong validatorIndex, Bytes32 blockRoot, UnsignedLong targetEpoch) {
    VoteTracker vote = votes.getVote(validatorIndex);
    ;

    if (targetEpoch.compareTo(vote.getNextEpoch()) > 0 || vote.equals(VoteTracker.Default())) {
      vote.setNextRoot(blockRoot);
      vote.setNextEpoch(targetEpoch);
    }
  }

  @Override
  public void updateFinalizedBlock(Bytes32 finalizedRoot) {
    this.finalizedRoot = Optional.of(finalizedRoot);
  }

  @VisibleForTesting
  void updateForkChoiceWeights(final HeadUpdates headUpdates) {
    forkChoice.protoArrayLock.writeLock().lock();
    forkChoice.balancesLock.writeLock().lock();
    try {
      final Checkpoint justifiedCheckpoint = headUpdates.getJustifiedCheckpoint();
      final Checkpoint finalizedCheckpoint = headUpdates.getFinalizedCheckpoint();
      final BeaconState justifiedState = headUpdates.getJustifiedCheckpointState();
      final List<UnsignedLong> newBalances = justifiedState.getBalances().asList();
      final List<Long> deltas = headUpdates.getWeightChanges();

      forkChoice.justifiedCheckpoint = headUpdates.getJustifiedCheckpoint();
      forkChoice.protoArray.applyScoreChanges(
          deltas, justifiedCheckpoint.getEpoch(), finalizedCheckpoint.getEpoch());
      forkChoice.balances = new ArrayList<>(newBalances);
    } finally {
      forkChoice.protoArrayLock.writeLock().unlock();
      forkChoice.balancesLock.writeLock().unlock();
    }
  }

  @VisibleForTesting
  void processBlock(
      UnsignedLong blockSlot,
      Bytes32 blockRoot,
      Bytes32 parentRoot,
      Bytes32 stateRoot,
      UnsignedLong justifiedEpoch,
      UnsignedLong finalizedEpoch) {
    forkChoice.protoArrayLock.writeLock().lock();
    try {
      forkChoice.protoArray.onBlock(
          blockSlot, blockRoot, parentRoot, stateRoot, justifiedEpoch, finalizedEpoch);
    } finally {
      forkChoice.protoArrayLock.writeLock().unlock();
    }
  }

  @Override
  public Bytes32 getHead() {
    return forkChoice.getHead();
  }

  @Override
  public Optional<UnsignedLong> getBlockSlot(Bytes32 blockRoot) {
    return Optional.ofNullable(newBlocks.get(blockRoot))
        .map(SignedBlockAndState::getSlot)
        .or(() -> forkChoice.getBlockSlot(blockRoot));
  }

  @Override
  public Optional<Bytes32> getBlockParent(Bytes32 blockRoot) {
    return Optional.ofNullable(newBlocks.get(blockRoot))
        .map(SignedBlockAndState::getParentRoot)
        .or(() -> forkChoice.getBlockParent(blockRoot));
  }

  @Override
  public boolean containsBlock(Bytes32 blockRoot) {
    return newBlocks.containsKey(blockRoot) || forkChoice.containsBlock(blockRoot);
  }

  /**
   * Returns a list of `deltas`, where there is one delta for each of the indices in
   * `0..indices.size()`.
   *
   * <p>The deltas are formed by a change between `oldBalances` and `newBalances`, and/or a change
   * of vote in `votes`.
   *
   * <p>## Errors
   *
   * <ul>
   *   <li>If a value in `indices` is greater to or equal to `indices.size()`.
   *   <li>If some `Bytes32` in `votes` is not a key in `indices` (except for `Bytes32.ZERO`, this
   *       is always valid).
   * </ul>
   *
   * @param indices
   * @param votes
   * @param oldBalances
   * @param newBalances
   * @return
   */
  static List<Long> computeDeltas(
      VoteUpdater votes,
      Map<Bytes32, Integer> indices,
      List<UnsignedLong> oldBalances,
      List<UnsignedLong> newBalances) {
    List<Long> deltas = new ArrayList<>(Collections.nCopies(indices.size(), 0L));

    for (UnsignedLong validatorIndex : votes.getIndices()) {
      VoteTracker vote = votes.getVote(validatorIndex);

      // There is no need to create a score change if the validator has never voted
      // or both their votes are for the zero hash (alias to the genesis block).
      if (vote.getCurrentRoot().equals(Bytes32.ZERO) && vote.getNextRoot().equals(Bytes32.ZERO)) {
        LOG.warn("ProtoArrayForkChoiceStrategy: Unexpected zero hashes in voted validator votes");
        continue;
      }

      int validatorIndexInt = toIntExact(validatorIndex.longValue());
      // If the validator was not included in the oldBalances (i.e. it did not exist yet)
      // then say its balance was zero.
      UnsignedLong oldBalance =
          oldBalances.size() > validatorIndexInt
              ? oldBalances.get(validatorIndexInt)
              : UnsignedLong.ZERO;

      // If the validator vote is not known in the newBalances, then use a balance of zero.
      //
      // It is possible that there is a vote for an unknown validator if we change our
      // justified state to a new state with a higher epoch that is on a different fork
      // because that may have on-boarded less validators than the prior fork.
      UnsignedLong newBalance =
          newBalances.size() > validatorIndexInt
              ? newBalances.get(validatorIndexInt)
              : UnsignedLong.ZERO;

      if (!vote.getCurrentRoot().equals(vote.getNextRoot()) || !oldBalance.equals(newBalance)) {
        // We ignore the vote if it is not known in `indices`. We assume that it is outside
        // of our tree (i.e. pre-finalization) and therefore not interesting.
        Integer currentDeltaIndex = indices.get(vote.getCurrentRoot());
        if (currentDeltaIndex != null) {
          checkState(
              currentDeltaIndex < deltas.size(), "ProtoArrayForkChoice: Invalid node delta index");
          long delta = subtractExact(deltas.get(currentDeltaIndex), oldBalance.longValue());
          deltas.set(currentDeltaIndex, delta);
        }

        // We ignore the vote if it is not known in `indices`. We assume that it is outside
        // of our tree (i.e. pre-finalization) and therefore not interesting.
        Integer nextDeltaIndex = indices.get(vote.getNextRoot());
        if (nextDeltaIndex != null) {
          checkState(
              nextDeltaIndex < deltas.size(), "ProtoArrayForkChoice: Invalid node delta index");
          long delta = addExact(deltas.get(nextDeltaIndex), newBalance.longValue());
          deltas.set(nextDeltaIndex, delta);
        }

        vote.setCurrentRoot(vote.getNextRoot());
      }
    }
    return deltas;
  }

  static class VoteUpdater {
    private final Map<UnsignedLong, VoteTracker> originalVotes;
    private final Map<UnsignedLong, VoteTracker> votes;

    VoteUpdater(Map<UnsignedLong, VoteTracker> originalVotes) {
      this.originalVotes = originalVotes;
      this.votes = new HashMap<>(originalVotes);
    }

    public void applyUpdates() {
      originalVotes.putAll(votes);
    }

    public Set<UnsignedLong> getIndices() {
      return new HashSet<>(votes.keySet());
    }

    public Map<UnsignedLong, VoteTracker> getVotes() {
      return votes;
    }

    public VoteTracker getVote(UnsignedLong validatorIndex) {
      VoteTracker vote = votes.get(validatorIndex);
      if (vote == null) {
        vote = votes.get(validatorIndex);
        if (vote == null) {
          vote = VoteTracker.Default();
        } else {
          vote = vote.copy();
        }
        votes.put(validatorIndex, vote);
      }
      return vote;
    }
  }

  static class HeadUpdates {
    final Checkpoint finalizedCheckpoint;
    final Checkpoint justifiedCheckpoint;
    final BeaconState justifiedCheckpointState;
    final List<Long> weightChanges;

    private HeadUpdates(
        Checkpoint finalizedCheckpoint,
        Checkpoint justifiedCheckpoint,
        BeaconState justifiedCheckpointState,
        List<Long> weightChanges) {
      this.finalizedCheckpoint = finalizedCheckpoint;
      this.justifiedCheckpoint = justifiedCheckpoint;
      this.justifiedCheckpointState = justifiedCheckpointState;
      this.weightChanges = weightChanges;
    }

    public Checkpoint getFinalizedCheckpoint() {
      return finalizedCheckpoint;
    }

    public Checkpoint getJustifiedCheckpoint() {
      return justifiedCheckpoint;
    }

    public BeaconState getJustifiedCheckpointState() {
      return justifiedCheckpointState;
    }

    public List<Long> getWeightChanges() {
      return weightChanges;
    }
  }
}
