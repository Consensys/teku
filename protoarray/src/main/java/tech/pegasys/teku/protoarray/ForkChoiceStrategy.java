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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.executionengine.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionengine.ForkChoiceState;

public class ForkChoiceStrategy implements BlockMetadataStore, ReadOnlyForkChoiceStrategy {
  private static final Logger LOG = LogManager.getLogger();
  private final ReadWriteLock protoArrayLock = new ReentrantReadWriteLock();
  private final ReadWriteLock votesLock = new ReentrantReadWriteLock();
  private final ReadWriteLock balancesLock = new ReentrantReadWriteLock();
  private final Spec spec;
  private final ProtoArray protoArray;

  private List<UInt64> balances;
  private Optional<Bytes32> proposerBoostRoot = Optional.empty();
  private UInt64 proposerBoostAmount = UInt64.ZERO;

  private ForkChoiceStrategy(Spec spec, ProtoArray protoArray, List<UInt64> balances) {
    this.spec = spec;
    this.protoArray = protoArray;
    this.balances = balances;
  }

  public static ForkChoiceStrategy initialize(final Spec spec, final ProtoArray protoArray) {
    return new ForkChoiceStrategy(spec, protoArray, new ArrayList<>());
  }

  public SlotAndBlockRoot findHead(
      final Checkpoint justifiedCheckpoint, final Checkpoint finalizedCheckpoint) {
    protoArrayLock.readLock().lock();
    try {
      return findHeadImpl(justifiedCheckpoint, finalizedCheckpoint);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  private SlotAndBlockRoot findHeadImpl(
      final Checkpoint justifiedCheckpoint, final Checkpoint finalizedCheckpoint) {
    return protoArray
        .findHead(
            justifiedCheckpoint.getRoot(),
            justifiedCheckpoint.getEpoch(),
            finalizedCheckpoint.getEpoch())
        .map(bestNode -> new SlotAndBlockRoot(bestNode.getBlockSlot(), bestNode.getBlockRoot()))
        .orElse(justifiedCheckpoint.toSlotAndBlockRoot(spec));
  }

  /**
   * Applies the weighting changes from any updated votes then finds and returns the best chain
   * head.
   *
   * @param voteUpdater the vote updater to access and update pending votes from
   * @param proposerBoostRoot the block root to apply proposer boost to
   * @param finalizedCheckpoint the current finalized checkpoint
   * @param justifiedCheckpoint the current justified checkpoint
   * @param justifiedStateEffectiveBalances the effective validator balances at the justified
   *     checkpoint
   * @return the best chain head block root
   */
  public Bytes32 applyPendingVotes(
      final VoteUpdater voteUpdater,
      final Optional<Bytes32> proposerBoostRoot,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final List<UInt64> justifiedStateEffectiveBalances,
      final UInt64 proposerBoostAmount) {
    protoArrayLock.writeLock().lock();
    votesLock.writeLock().lock();
    balancesLock.writeLock().lock();
    try {
      List<Long> deltas =
          ProtoArrayScoreCalculator.computeDeltas(
              voteUpdater,
              getTotalTrackedNodeCount(),
              protoArray::getIndexByRoot,
              balances,
              justifiedStateEffectiveBalances,
              this.proposerBoostRoot,
              proposerBoostRoot,
              this.proposerBoostAmount,
              proposerBoostAmount);

      protoArray.applyScoreChanges(
          deltas, justifiedCheckpoint.getEpoch(), finalizedCheckpoint.getEpoch());
      balances = justifiedStateEffectiveBalances;
      this.proposerBoostRoot = proposerBoostRoot;
      this.proposerBoostAmount = proposerBoostAmount;

      return findHeadImpl(justifiedCheckpoint, finalizedCheckpoint).getBlockRoot();
    } finally {
      protoArrayLock.writeLock().unlock();
      votesLock.writeLock().unlock();
      balancesLock.writeLock().unlock();
    }
  }

  public void onAttestation(final VoteUpdater voteUpdater, final IndexedAttestation attestation) {
    votesLock.writeLock().lock();
    try {
      attestation
          .getAttesting_indices()
          .streamUnboxed()
          .forEach(
              validatorIndex ->
                  processAttestation(
                      voteUpdater,
                      validatorIndex,
                      attestation.getData().getBeacon_block_root(),
                      attestation.getData().getTarget().getEpoch()));
    } finally {
      votesLock.writeLock().unlock();
    }
  }

  @Override
  public Map<Bytes32, UInt64> getChainHeads() {
    return getChainHeads(node -> true);
  }

  @Override
  public Map<Bytes32, UInt64> getOptimisticChainHeads() {
    return getChainHeads(ProtoNode::isOptimistic);
  }

  private Map<Bytes32, UInt64> getChainHeads(final Predicate<ProtoNode> filter) {
    protoArrayLock.readLock().lock();
    try {
      final Map<Bytes32, UInt64> chainHeads = new HashMap<>();
      protoArray.getNodes().stream()
          .filter(
              protoNode ->
                  protoNode.getBestChildIndex().isEmpty()
                      && filter.test(protoNode)
                      && protoArray.nodeIsViableForHead(protoNode))
          .forEach(protoNode -> chainHeads.put(protoNode.getBlockRoot(), protoNode.getBlockSlot()));
      return Collections.unmodifiableMap(chainHeads);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  public ForkChoiceState getForkChoiceState(
      final Checkpoint justifiedCheckpoint, final Checkpoint finalizedCheckpoint) {
    protoArrayLock.readLock().lock();
    try {
      final ProtoNode headNode =
          protoArray.findOptimisticHead(
              justifiedCheckpoint.getRoot(),
              justifiedCheckpoint.getEpoch(),
              finalizedCheckpoint.getEpoch());
      final Bytes32 headExecutionBlockHash = headNode.getExecutionBlockHash();
      final Bytes32 finalizedExecutionHash =
          protoArray
              .getProtoNode(finalizedCheckpoint.getRoot())
              .map(ProtoNode::getExecutionBlockHash)
              .orElse(Bytes32.ZERO);
      return new ForkChoiceState(
          headExecutionBlockHash, headExecutionBlockHash, finalizedExecutionHash);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  void processAttestation(
      VoteUpdater voteUpdater, UInt64 validatorIndex, Bytes32 blockRoot, UInt64 targetEpoch) {
    VoteTracker vote = voteUpdater.getVote(validatorIndex);

    if (targetEpoch.isGreaterThan(vote.getNextEpoch()) || vote.equals(VoteTracker.DEFAULT)) {
      VoteTracker newVote = new VoteTracker(vote.getCurrentRoot(), blockRoot, targetEpoch);
      voteUpdater.putVote(validatorIndex, newVote);
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

  public int getTotalTrackedNodeCount() {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.getTotalTrackedNodeCount();
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public boolean contains(Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.contains(blockRoot);
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
  public Optional<Bytes32> executionBlockHash(final Bytes32 beaconBlockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getProtoNode(beaconBlockRoot).map(ProtoNode::getExecutionBlockHash);
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

  public boolean isFullyValidated(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getProtoNode(blockRoot).map(ProtoNode::isFullyValidated).orElse(false);
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

  @Override
  public Set<Bytes32> getBlockRootsAtSlot(final UInt64 slot) {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.getNodes().stream()
          .filter(protoNode -> protoNode.getBlockSlot().equals(slot))
          .map(ProtoNode::getBlockRoot)
          .collect(Collectors.toSet());
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  /**
   * Process each node in the chain defined by {@code head}
   *
   * @param head The root defining the head of the chain to process
   * @param processor The callback to invoke for each child-parent pair
   */
  @Override
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
  @Override
  public void processHashesInChainWhile(final Bytes32 head, HaltableNodeProcessor nodeProcessor) {
    protoArrayLock.readLock().lock();
    try {
      final Optional<ProtoNode> startingNode = getProtoNode(head);
      if (startingNode.isEmpty()) {
        throw new IllegalArgumentException("Unknown root supplied: " + head);
      }
      ProtoNode currentNode = startingNode.orElseThrow();

      while (protoArray.contains(currentNode.getBlockRoot())) {
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
  public void processAllInOrder(final NodeProcessor nodeProcessor) {
    protoArrayLock.readLock().lock();
    try {
      final Map<Bytes32, Integer> indices = protoArray.getRootIndices();
      protoArray.getNodes().stream()
          // Filter out nodes that could be pruned but are still in the protoarray
          .filter(node -> indices.containsKey(node.getBlockRoot()))
          .forEach(
              node ->
                  nodeProcessor.process(
                      node.getBlockRoot(), node.getBlockSlot(), node.getParentRoot()));
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public void applyUpdate(
      final Collection<BlockAndCheckpointEpochs> newBlocks,
      final Set<Bytes32> removedBlockRoots,
      final Checkpoint finalizedCheckpoint) {
    protoArrayLock.writeLock().lock();
    try {
      newBlocks.stream()
          .sorted(Comparator.comparing(BlockAndCheckpointEpochs::getSlot))
          .forEach(
              block ->
                  processBlock(
                      block.getBlock().getSlot(),
                      block.getBlock().getRoot(),
                      block.getBlock().getParentRoot(),
                      block.getBlock().getStateRoot(),
                      block.getCheckpointEpochs().getJustifiedEpoch(),
                      block.getCheckpointEpochs().getFinalizedEpoch(),
                      block.getExecutionBlockHash().orElse(Bytes32.ZERO)));
      removedBlockRoots.forEach(protoArray::removeBlockRoot);
      protoArray.maybePrune(finalizedCheckpoint.getRoot());
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  @VisibleForTesting
  void processBlock(
      UInt64 blockSlot,
      Bytes32 blockRoot,
      Bytes32 parentRoot,
      Bytes32 stateRoot,
      UInt64 justifiedEpoch,
      UInt64 finalizedEpoch,
      Bytes32 executionBlockHash) {
    protoArray.onBlock(
        blockSlot,
        blockRoot,
        parentRoot,
        stateRoot,
        justifiedEpoch,
        finalizedEpoch,
        executionBlockHash,
        spec.isBlockProcessorOptimistic(blockSlot));
  }

  private Optional<ProtoNode> getProtoNode(Bytes32 blockRoot) {
    return protoArray.getProtoNode(blockRoot);
  }

  public void onExecutionPayloadResult(
      final Bytes32 blockRoot, final ExecutionPayloadStatus status) {
    if (status == ExecutionPayloadStatus.SYNCING) {
      return;
    }
    protoArrayLock.writeLock().lock();
    try {
      switch (status) {
        case VALID:
          protoArray.markNodeValid(blockRoot);
          break;
        case INVALID:
          LOG.warn("Payload for block root {} was invalid", blockRoot);
          protoArray.markNodeInvalid(blockRoot);
          break;
        default:
          throw new IllegalArgumentException("Unknown payload status: " + status);
      }
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }
}
