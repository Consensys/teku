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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.addExact;
import static java.lang.Math.subtractExact;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.forkchoice.MutableStore;
import tech.pegasys.artemis.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.artemis.datastructures.forkchoice.VoteTracker;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.util.config.Constants;

public class ProtoArrayForkChoiceStrategy implements ForkChoiceStrategy {
  private static final Logger LOG = LogManager.getLogger();

  private final ReadWriteLock protoArrayLock = new ReentrantReadWriteLock();
  private final ReadWriteLock votesLock = new ReentrantReadWriteLock();
  private final ReadWriteLock balancesLock = new ReentrantReadWriteLock();
  private final ProtoArray protoArray;

  private List<UnsignedLong> balances;

  private ProtoArrayForkChoiceStrategy(ProtoArray protoArray, List<UnsignedLong> balances) {
    this.protoArray = protoArray;
    this.balances = balances;
  }

  // Public
  public static ProtoArrayForkChoiceStrategy create(ReadOnlyStore store) {
    ProtoArray protoArray =
        new ProtoArray(
            Constants.PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD,
            store.getJustifiedCheckpoint().getEpoch(),
            store.getFinalizedCheckpoint().getEpoch(),
            new ArrayList<>(),
            new HashMap<>());

    processBlocksInStoreAtStartup(store, protoArray);

    return new ProtoArrayForkChoiceStrategy(protoArray, new ArrayList<>());
  }

  @Override
  public Bytes32 findHead(final MutableStore store) {
    Checkpoint justifiedCheckpoint = store.getJustifiedCheckpoint();
    return findHead(
        store,
        justifiedCheckpoint.getEpoch(),
        justifiedCheckpoint.getRoot(),
        store.getFinalizedCheckpoint().getEpoch(),
        store.getCheckpointState(justifiedCheckpoint).getBalances().asList());
  }

  @Override
  public void onAttestation(final MutableStore store, final IndexedAttestation attestation) {
    votesLock.writeLock().lock();
    try {
      attestation.getAttesting_indices().stream()
          .parallel()
          .forEach(
              validatorIndex -> {
                processAttestation(
                    store,
                    validatorIndex,
                    attestation.getData().getBeacon_block_root(),
                    attestation.getData().getTarget().getEpoch());
              });
    } finally {
      votesLock.writeLock().unlock();
    }
  }

  @Override
  public void onBlock(final ReadOnlyStore store, final BeaconBlock block) {
    Bytes32 blockRoot = block.hash_tree_root();
    processBlock(
        block.getSlot(),
        blockRoot,
        block.getParent_root(),
        block.getState_root(),
        store.getBlockState(blockRoot).getCurrent_justified_checkpoint().getEpoch(),
        store.getBlockState(blockRoot).getFinalized_checkpoint().getEpoch());
  }

  public void maybePrune(Bytes32 finalizedRoot) {
    protoArrayLock.writeLock().lock();
    try {
      protoArray.maybePrune(finalizedRoot);
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  // Internal
  private static void processBlocksInStoreAtStartup(ReadOnlyStore store, ProtoArray protoArray) {
    store.getBlockRoots().stream()
        .map(store::getBlock)
        .sorted(Comparator.comparing(BeaconBlock::getSlot))
        .forEach(block -> processBlockAtStartup(store, protoArray, block));
  }

  private static void processBlockAtStartup(
      final ReadOnlyStore store, final ProtoArray protoArray, final BeaconBlock block) {
    Bytes32 blockRoot = block.hash_tree_root();
    protoArray.onBlock(
        block.getSlot(),
        blockRoot,
        store.getBlockRoots().contains(block.getParent_root())
            ? Optional.of(block.getParent_root())
            : Optional.empty(),
        block.getState_root(),
        store.getBlockState(block.hash_tree_root()).getCurrent_justified_checkpoint().getEpoch(),
        store.getBlockState(block.hash_tree_root()).getFinalized_checkpoint().getEpoch());
  }

  void processAttestation(
      MutableStore store,
      UnsignedLong validatorIndex,
      Bytes32 blockRoot,
      UnsignedLong targetEpoch) {
    VoteTracker vote = store.getVote(validatorIndex);

    if (targetEpoch.compareTo(vote.getNextEpoch()) > 0 || vote.equals(VoteTracker.Default())) {
      vote.setNextRoot(blockRoot);
      vote.setNextEpoch(targetEpoch);
    }
  }

  void processBlock(
      UnsignedLong blockSlot,
      Bytes32 blockRoot,
      Bytes32 parentRoot,
      Bytes32 stateRoot,
      UnsignedLong justifiedEpoch,
      UnsignedLong finalizedEpoch) {
    protoArrayLock.writeLock().lock();
    try {
      protoArray.onBlock(
          blockSlot, blockRoot, Optional.of(parentRoot), stateRoot, justifiedEpoch, finalizedEpoch);
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  Bytes32 findHead(
      MutableStore store,
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

      List<Long> deltas = computeDeltas(store, protoArray.getIndices(), oldBalances, newBalances);

      protoArray.applyScoreChanges(deltas, justifiedEpoch, finalizedEpoch);
      balances = new ArrayList<>(newBalances);

      return protoArray.findHead(justifiedRoot);
    } finally {
      protoArrayLock.writeLock().unlock();
      votesLock.writeLock().unlock();
      balancesLock.writeLock().unlock();
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

  public Optional<UnsignedLong> blockSlot(Bytes32 blockRoot) {
    return blockSlotAndStateRoot(blockRoot).map(BlockSlotAndStateRoot::getBlockSlot);
  }

  public Optional<BlockSlotAndStateRoot> blockSlotAndStateRoot(Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      int blockIndex =
          checkNotNull(
              protoArray.getIndices().get(blockRoot), "ProtoArrayForkChoice: Unknown block root");
      if (blockIndex >= protoArray.getNodes().size()) {
        return Optional.empty();
      } else {
        ProtoNode node = protoArray.getNodes().get(blockIndex);
        return Optional.of(new BlockSlotAndStateRoot(node.getBlockSlot(), node.getStateRoot()));
      }
    } finally {
      protoArrayLock.readLock().unlock();
    }
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
   * @param store
   * @param oldBalances
   * @param newBalances
   * @return
   */
  static List<Long> computeDeltas(
      MutableStore store,
      Map<Bytes32, Integer> indices,
      List<UnsignedLong> oldBalances,
      List<UnsignedLong> newBalances) {
    List<Long> deltas = new ArrayList<>(Collections.nCopies(indices.size(), 0L));

    for (UnsignedLong validatorIndex : store.getVotedValidatorIndices()) {
      VoteTracker vote = store.getVote(validatorIndex);

      // There is no need to create a score change if the validator has never voted
      // or both their votes are for the zero hash (alias to the genesis block).
      if (vote.getCurrentRoot().equals(Bytes32.ZERO) && vote.getNextRoot().equals(Bytes32.ZERO)) {
        LOG.warn("ProtoArrayForkChoiceStrategy: Unexpected zero hashes in voted validator votes");
        continue;
      }

      // If the validator was not included in the oldBalances (i.e. it did not exist yet)
      // then say its balance was zero.
      UnsignedLong oldBalance =
          oldBalances.size() > validatorIndex.intValue()
              ? oldBalances.get(validatorIndex.intValue())
              : UnsignedLong.ZERO;

      // If the validator vote is not known in the newBalances, then use a balance of zero.
      //
      // It is possible that there is a vote for an unknown validator if we change our
      // justified state to a new state with a higher epoch that is on a different fork
      // because that may have on-boarded less validators than the prior fork.
      UnsignedLong newBalance =
          newBalances.size() > validatorIndex.intValue()
              ? newBalances.get(validatorIndex.intValue())
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
