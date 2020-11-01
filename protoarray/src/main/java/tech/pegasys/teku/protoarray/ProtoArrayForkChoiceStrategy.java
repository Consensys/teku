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

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.ForkChoiceStrategy;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ProtoArrayForkChoiceStrategy implements ForkChoiceStrategy {
  private final ReadWriteLock protoArrayLock = new ReentrantReadWriteLock();
  private final ReadWriteLock votesLock = new ReentrantReadWriteLock();
  private final ReadWriteLock balancesLock = new ReentrantReadWriteLock();
  private final ProtoArray protoArray;
  private final ProtoArrayStorageChannel storageChannel;

  private List<UInt64> balances;

  private ProtoArrayForkChoiceStrategy(
      ProtoArray protoArray,
      List<UInt64> balances,
      ProtoArrayStorageChannel protoArrayStorageChannel) {
    this.protoArray = protoArray;
    this.balances = balances;
    this.storageChannel = protoArrayStorageChannel;
  }

  public static ProtoArrayForkChoiceStrategy create(
      final ProtoArray protoArray, final ProtoArrayStorageChannel storageChannel) {
    return new ProtoArrayForkChoiceStrategy(protoArray, new ArrayList<>(), storageChannel);
  }

  @Override
  public Bytes32 findHead(
      final MutableStore store,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final BeaconState justifiedCheckpointState) {
    return findHead(
        store,
        justifiedCheckpoint.getEpoch(),
        justifiedCheckpoint.getRoot(),
        finalizedCheckpoint.getEpoch(),
        justifiedCheckpointState.getBalances().asList());
  }

  @Override
  public void onAttestation(final MutableStore store, final IndexedAttestation attestation) {
    votesLock.writeLock().lock();
    try {
      attestation
          .getAttesting_indices()
          .forEach(
              validatorIndex ->
                  processAttestation(
                      store,
                      validatorIndex,
                      attestation.getData().getBeacon_block_root(),
                      attestation.getData().getTarget().getEpoch()));
    } finally {
      votesLock.writeLock().unlock();
    }
  }

  @Override
  public void onBlock(final BeaconBlock block, final BeaconState state) {
    Bytes32 blockRoot = block.hash_tree_root();
    processBlock(
        block.getSlot(),
        blockRoot,
        block.getParent_root(),
        block.getState_root(),
        state.getCurrent_justified_checkpoint().getEpoch(),
        state.getFinalized_checkpoint().getEpoch());
  }

  /**
   * Process each node in the chain defined by {@code head}
   *
   * @param head The root defining the head of the chain to process
   * @param processor The callback to invoke for each child-parent pair
   */
  public void processHashesInChain(final Bytes32 head, NodeProcessor processor) {
    processHashesInChainWhile(head, HaltableNodeProcessor.fromNodeProcessor(processor));
  }

  /**
   * Process roots in the chain defined by {@code head}. Stops processing when {@code
   * shouldContinue} returns false.
   *
   * @param head The root defining the head of the chain to construct
   * @param nodeProcessor The callback receiving hashes and determining whether to continue
   *     processing
   */
  public void processHashesInChainWhile(final Bytes32 head, HaltableNodeProcessor nodeProcessor) {
    protoArrayLock.readLock().lock();
    try {
      final Optional<ProtoNode> startingNode = getProtoNode(head);
      if (startingNode.isEmpty()) {
        throw new IllegalArgumentException("Unknown root supplied: " + head);
      }
      ProtoNode currentNode = startingNode.orElseThrow();
      while (true) {
        final boolean shouldContinue =
            nodeProcessor.process(
                currentNode.getBlockRoot(),
                currentNode.getBlockSlot(),
                currentNode.getParentRoot());
        if (!shouldContinue || currentNode.getParentIndex().isEmpty()) {
          break;
        }
        currentNode = protoArray.getNodes().get(currentNode.getParentIndex().get());
      }
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public void save() {
    protoArrayLock.readLock().lock();
    try {
      storageChannel.onProtoArrayUpdate(ProtoArraySnapshot.create(protoArray));
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public void maybePrune(Bytes32 finalizedRoot) {
    protoArrayLock.writeLock().lock();
    try {
      protoArray.maybePrune(finalizedRoot);
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  void processAttestation(
      MutableStore store, UInt64 validatorIndex, Bytes32 blockRoot, UInt64 targetEpoch) {
    VoteTracker vote = store.getVote(validatorIndex);

    if (targetEpoch.isGreaterThan(vote.getNextEpoch()) || vote.equals(VoteTracker.Default())) {
      vote.setNextRoot(blockRoot);
      vote.setNextEpoch(targetEpoch);
    }
  }

  public void applyTransaction(
      final Collection<SignedBeaconBlock> newBlocks,
      final Collection<Bytes32> removedBlockRoots,
      final UInt64 justifiedEpoch,
      final UInt64 finalizedEpoch) {
    protoArrayLock.writeLock().lock();
    try {
      newBlocks.stream()
          .sorted(Comparator.comparing(SignedBeaconBlock::getSlot))
          .forEach(
              block ->
                  protoArray.onBlock(
                      block.getSlot(),
                      block.getRoot(),
                      block.getParent_root(),
                      block.getStateRoot(),
                      justifiedEpoch,
                      finalizedEpoch));
      removedBlockRoots.forEach(protoArray::removeBlockRoot);
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  // TODO: Probably better if this isn't public?
  void processBlock(
      UInt64 blockSlot,
      Bytes32 blockRoot,
      Bytes32 parentRoot,
      Bytes32 stateRoot,
      UInt64 justifiedEpoch,
      UInt64 finalizedEpoch) {
    protoArrayLock.writeLock().lock();
    try {
      protoArray.onBlock(
          blockSlot, blockRoot, parentRoot, stateRoot, justifiedEpoch, finalizedEpoch);
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  Bytes32 findHead(
      MutableStore store,
      UInt64 justifiedEpoch,
      Bytes32 justifiedRoot,
      UInt64 finalizedEpoch,
      List<UInt64> justifiedStateBalances) {
    protoArrayLock.writeLock().lock();
    votesLock.writeLock().lock();
    balancesLock.writeLock().lock();
    try {
      List<UInt64> oldBalances = balances;
      List<UInt64> newBalances = justifiedStateBalances;

      List<Long> deltas =
          ProtoArrayScoreCalculator.computeDeltas(
              store, protoArray.getIndices(), oldBalances, newBalances);

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

  @Override
  public boolean contains(Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.getIndices().containsKey(blockRoot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<UInt64> blockSlot(Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getProtoNode(blockRoot).map(ProtoNode::getBlockSlot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<Bytes32> blockParentRoot(Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getProtoNode(blockRoot).map(ProtoNode::getParentRoot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<Bytes32> getAncestor(final Bytes32 blockRoot, final UInt64 slot) {
    protoArrayLock.readLock().lock();
    try {
      // Note: This code could be more succinct if currentNode were an Optional and we used flatMap
      // and map but during long periods of finality this becomes a massive hot spot in the code and
      // our performance is dominated by the time taken to create Optional instances within the map
      // calls.
      final Optional<ProtoNode> startingNode = getProtoNode(blockRoot);
      if (startingNode.isEmpty()) {
        return Optional.empty();
      }
      ProtoNode currentNode = startingNode.get();
      while (currentNode.getBlockSlot().isGreaterThan(slot)) {
        final Optional<Integer> parentIndex = currentNode.getParentIndex();
        if (parentIndex.isEmpty()) {
          return Optional.empty();
        }
        currentNode = protoArray.getNodes().get(parentIndex.get());
      }
      return Optional.of(currentNode.getBlockRoot());
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  public void forEach(final NodeProcessor nodeProcessor) {
    protoArrayLock.readLock().lock();
    try {
      protoArray
          .getNodes()
          .forEach(
              node ->
                  nodeProcessor.process(
                      node.getBlockRoot(), node.getBlockSlot(), node.getParentRoot()));
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  private Optional<ProtoNode> getProtoNode(Bytes32 blockRoot) {
    return Optional.ofNullable(protoArray.getIndices().get(blockRoot))
        .flatMap(
            blockIndex -> {
              if (blockIndex < protoArray.getNodes().size()) {
                return Optional.of(protoArray.getNodes().get(blockIndex));
              }
              return Optional.empty();
            });
  }

  @VisibleForTesting
  public Set<Bytes32> getBlockRoots() {
    protoArrayLock.readLock().lock();
    try {
      return new HashSet<>(protoArray.getIndices().keySet());
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  public interface NodeProcessor {
    /**
     * Process parent and child roots
     *
     * @param childRoot The child root
     * @param parentRoot The parent root
     */
    void process(Bytes32 childRoot, UInt64 childSlot, Bytes32 parentRoot);
  }

  public interface HaltableNodeProcessor {

    static HaltableNodeProcessor fromNodeProcessor(final NodeProcessor processor) {
      return (child, childSlot, parent) -> {
        processor.process(child, childSlot, parent);
        return true;
      };
    }

    /**
     * Process parent and child and return a status indicating whether to continue
     *
     * @param childRoot The child root
     * @param parentRoot The parent root
     * @return True if processing should continue, false if processing should halt
     */
    boolean process(Bytes32 childRoot, UInt64 childSlot, Bytes32 parentRoot);
  }
}
