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

package tech.pegasys.artemis.protoarray;

import static java.lang.Math.addExact;
import static java.lang.Math.subtractExact;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.util.config.Constants;

public class ProtoArrayForkChoice {
  private final ReadWriteLock protoArrayLock = new ReentrantReadWriteLock();
  private final ReadWriteLock votesLock = new ReentrantReadWriteLock();
  private final ReadWriteLock balancesLock = new ReentrantReadWriteLock();
  private final ProtoArray protoArray;
  private final ElasticList<VoteTracker> votes;
  private List<UnsignedLong> balances;

  private ProtoArrayForkChoice(
      ProtoArray protoArray, ElasticList<VoteTracker> votes, List<UnsignedLong> balances) {
    this.protoArray = protoArray;
    this.votes = votes;
    this.balances = balances;
  }

  public static ProtoArrayForkChoice create(
      UnsignedLong finalizedBlockSlot,
      Bytes32 finalizedBlockStateRoot,
      UnsignedLong justifiedEpoch,
      UnsignedLong finalizedEpoch,
      Bytes32 finalizedRoot) {
    ProtoArray protoArray =
        new ProtoArray(
            Constants.PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD,
            justifiedEpoch,
            finalizedEpoch,
            new ArrayList<>(),
            new HashMap<>());

    protoArray.onBlock(
        finalizedBlockSlot,
        finalizedRoot,
        Optional.empty(),
        finalizedBlockStateRoot,
        justifiedEpoch,
        finalizedEpoch);

    return new ProtoArrayForkChoice(
        protoArray, new ElasticList<>(VoteTracker.DEFAULT), new ArrayList<>());
  }

  public void processAttestation(int validatorIndex, Bytes32 blockRoot, UnsignedLong targetEpoch) {
    votesLock.writeLock().lock();
    try {
      VoteTracker vote = votes.get(validatorIndex);

      if (targetEpoch.compareTo(vote.getNextEpoch()) > 0 || vote.equals(VoteTracker.DEFAULT)) {
        vote.setNextRoot(blockRoot);
        vote.setNextEpoch(targetEpoch);
      }
    } finally {
      votesLock.writeLock().unlock();
    }
  }

  public void processBlock(
      UnsignedLong slot,
      Bytes32 blockRoot,
      Bytes32 parentRoot,
      Bytes32 stateRoot,
      UnsignedLong justifiedEpoch,
      UnsignedLong finalizedEpoch) {
    protoArrayLock.writeLock().lock();
    try {
      protoArray.onBlock(
          slot, blockRoot, Optional.of(parentRoot), stateRoot, justifiedEpoch, finalizedEpoch);
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  public Bytes32 findHead(
      UnsignedLong justifiedEpoch,
      Bytes32 justifiedRoot,
      UnsignedLong finalizedEpoch,
      List<UnsignedLong> justifiedStateBalances) {
    protoArrayLock.writeLock().lock();
    votesLock.writeLock().lock();
    balancesLock.writeLock().lock();
    try {
      List<UnsignedLong> oldBalances = balances;
      List<UnsignedLong> newBalances = justifiedStateBalances;

      List<Long> deltas = computeDeltas(protoArray.getIndices(), votes, oldBalances, newBalances);

      protoArray.applyScoreChanges(deltas, justifiedEpoch, finalizedEpoch);
      balances = newBalances;

      return protoArray.findHead(justifiedRoot);
    } finally {
      protoArrayLock.writeLock().unlock();
      votesLock.writeLock().unlock();
      balancesLock.writeLock().unlock();
    }
  }

  public void maybePrune(Bytes32 finalizedRoot) {
    protoArrayLock.writeLock().lock();
    try {
      protoArray.maybePrune(finalizedRoot);
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  public void setPruneThreshold(int pruneThreshold) {
    protoArrayLock.writeLock().lock();
    try {
      protoArray.setPruneThreshold(pruneThreshold);
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  public int size() {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.getNodes().size();
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  public boolean containsBlock(Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.getIndices().containsKey(blockRoot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  public Optional<BlockSlotAndStateRoot> blockSlotAndStateRoot(Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      int blockIndex = protoArray.getIndices().get(blockRoot);

      if (protoArray.getNodes().size() > blockIndex) {
        ProtoNode node = protoArray.getNodes().get(blockIndex);
        return Optional.of(new BlockSlotAndStateRoot(node.getSlot(), node.getStateRoot()));
      } else {
        return Optional.empty();
      }
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  public Optional<Checkpoint> latestMessage(int validatorIndex) {
    votesLock.readLock().lock();
    try {
      if (validatorIndex < votes.size()) {
        VoteTracker vote = votes.get(validatorIndex);
        if (vote.equals(VoteTracker.DEFAULT)) {
          return Optional.empty();
        } else {
          return Optional.of(new Checkpoint(vote.getNextEpoch(), vote.getNextRoot()));
        }
      } else {
        return Optional.empty();
      }
    } finally {
      votesLock.readLock().unlock();
    }
  }

  /**
   * Returns a list of `deltas`, where there is one delta for each of the indices in
   * `0..indices.size()`.
   *
   * The deltas are formed by a change between `oldBalances` and `newBalances`,
   * and/or a change of vote in `votes`.
   *
   * ## Errors
   *
   * - If a value in `indices` is greater to or equal to `indices.size()`.
   * - If some `Bytes32` in `votes` is not a key in `indices` (except for `Bytes32.ZERO`, this is
   * always valid).
   *
   * @param indices
   * @param votes
   * @param oldBalances
   * @param newBalances
   * @return
   */
  static List<Long> computeDeltas(
      Map<Bytes32, Integer> indices,
      ElasticList<VoteTracker> votes,
      List<UnsignedLong> oldBalances,
      List<UnsignedLong> newBalances) {
    List<Long> deltas = new ArrayList<>(Collections.nCopies(indices.size(), 0L));

    for (int validatorIndex = 0; validatorIndex < votes.size(); validatorIndex++) {
      VoteTracker vote = votes.get(validatorIndex);

      // There is no need to create a score change if the validator has never voted
      // or both their votes are for the zero hash (alias to the genesis block).
      if (vote.getCurrentRoot().equals(Bytes32.ZERO) && vote.getNextRoot().equals(Bytes32.ZERO)) {
        continue;
      }

      // If the validator was not included in the oldBalances (i.e. it did not exist yet)
      // then say its balance was zero.
      UnsignedLong oldBalance =
          oldBalances.size() > validatorIndex ? oldBalances.get(validatorIndex) : UnsignedLong.ZERO;

      // If the validator vote is not known in the newBalances, then use a balance of zero.
      //
      // It is possible that there is a vote for an unknown validator if we change our
      // justified state to a new state with a higher epoch that is on a different fork
      // because that may have on-boarded less validators than the prior fork.
      UnsignedLong newBalance =
          newBalances.size() > validatorIndex ? newBalances.get(validatorIndex) : UnsignedLong.ZERO;

      if (!vote.getCurrentRoot().equals(vote.getNextRoot()) || !oldBalance.equals(newBalance)) {
        // We ignore the vote if it is not known in `indices`. We assume that it is outside
        // of our tree (i.e. pre-finalization) and therefore not interesting.
        Integer currentDeltaIndex = indices.get(vote.getCurrentRoot());
        if (currentDeltaIndex != null) {
          if (currentDeltaIndex >= deltas.size()) {
            throw new RuntimeException("ProtoArrayForkChoice: Invalid node delta index");
          }
          long delta = subtractExact(deltas.get(currentDeltaIndex), oldBalance.longValue());
          deltas.set(currentDeltaIndex, delta);
        }

        // We ignore the vote if it is not known in `indices`. We assume that it is outside
        // of our tree (i.e. pre-finalization) and therefore not interesting.
        Integer nextDeltaIndex = indices.get(vote.getNextRoot());
        if (nextDeltaIndex != null) {
          if (nextDeltaIndex >= deltas.size()) {
            throw new RuntimeException("ProtoArrayForkChoice: Invalid node delta index");
          }
          long delta = addExact(deltas.get(nextDeltaIndex), newBalance.longValue());
          deltas.set(nextDeltaIndex, delta);
        }

        vote.setCurrentRoot(vote.getNextRoot());
      }
    }
    return deltas;
  }

  public static class BlockSlotAndStateRoot {
    private final UnsignedLong blockSlot;
    private final Bytes32 stateRoot;

    public BlockSlotAndStateRoot(UnsignedLong blockSlot, Bytes32 stateRoot) {
      this.blockSlot = blockSlot;
      this.stateRoot = stateRoot;
    }

    public UnsignedLong getBlockSlot() {
      return blockSlot;
    }

    public Bytes32 getStateRoot() {
      return stateRoot;
    }
  }
}
