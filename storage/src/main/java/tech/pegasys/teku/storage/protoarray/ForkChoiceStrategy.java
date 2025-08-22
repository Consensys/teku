/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.storage.protoarray;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;

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

  private ForkChoiceStrategy(
      final Spec spec, final ProtoArray protoArray, final List<UInt64> balances) {
    this.spec = spec;
    this.protoArray = protoArray;
    this.balances = balances;
  }

  public static ForkChoiceStrategy initialize(final Spec spec, final ProtoArray protoArray) {
    return new ForkChoiceStrategy(spec, protoArray, new ArrayList<>());
  }

  public SlotAndBlockRoot findHead(
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint) {
    protoArrayLock.readLock().lock();
    try {
      return findHeadImpl(currentEpoch, justifiedCheckpoint, finalizedCheckpoint);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  private SlotAndBlockRoot findHeadImpl(
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint) {
    final ProtoNode bestNode =
        protoArray.findOptimisticHead(currentEpoch, justifiedCheckpoint, finalizedCheckpoint);
    return new SlotAndBlockRoot(bestNode.getBlockSlot(), bestNode.getBlockRoot());
  }

  /**
   * Applies the weighting changes from any updated votes then finds and returns the best chain
   * head.
   *
   * @param voteUpdater the vote updater to access and update pending votes from
   * @param proposerBoostRoot the block root to apply proposer boost to
   * @param currentEpoch the current epoch based on Store time.
   * @param finalizedCheckpoint the current finalized checkpoint
   * @param justifiedCheckpoint the current justified checkpoint
   * @param justifiedStateEffectiveBalances the effective validator balances at the justified
   *     checkpoint
   * @return the best chain head block root
   */
  public Bytes32 applyPendingVotes(
      final VoteUpdater voteUpdater,
      final Optional<Bytes32> proposerBoostRoot,
      final UInt64 currentEpoch,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final List<UInt64> justifiedStateEffectiveBalances,
      final UInt64 proposerBoostAmount) {
    protoArrayLock.writeLock().lock();
    votesLock.writeLock().lock();
    balancesLock.writeLock().lock();
    try {
      LongList deltas =
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

      protoArray.applyScoreChanges(deltas, currentEpoch, justifiedCheckpoint, finalizedCheckpoint);
      balances = justifiedStateEffectiveBalances;
      this.proposerBoostRoot = proposerBoostRoot;
      this.proposerBoostAmount = proposerBoostAmount;

      return findHeadImpl(currentEpoch, justifiedCheckpoint, finalizedCheckpoint).getBlockRoot();
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
          .getAttestingIndices()
          .streamUnboxed()
          .forEach(
              validatorIndex ->
                  processAttestation(
                      voteUpdater,
                      validatorIndex,
                      attestation.getData().getBeaconBlockRoot(),
                      attestation.getData().getTarget().getEpoch()));
    } finally {
      votesLock.writeLock().unlock();
    }
  }

  public void applyDeferredAttestations(final VoteUpdater voteUpdater, final DeferredVotes votes) {
    final UInt64 targetEpoch = spec.computeEpochAtSlot(votes.getSlot());
    votesLock.writeLock().lock();
    try {
      votes.forEachDeferredVote(
          (blockRoot, validatorIndex) ->
              processAttestation(voteUpdater, validatorIndex, blockRoot, targetEpoch));
    } finally {
      votesLock.writeLock().unlock();
    }
  }

  @Override
  public List<ProtoNodeData> getChainHeads(final boolean includeNonViableHeads) {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.getNodes().stream()
          .filter(
              protoNode ->
                  protoNode.getBestChildIndex().isEmpty()
                      && (includeNonViableHeads || protoArray.nodeIsViableForHead(protoNode)))
          .map(ProtoNode::getBlockData)
          .toList();
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public List<ProtoNodeData> getViableChainHeads() {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.getNodes().stream()
          .filter(
              protoNode ->
                  protoNode.getBestChildIndex().isEmpty()
                      && protoArray.nodeIsViableForHead(protoNode)
                      && protoArray.isJustifiedRootOrDescendant(protoNode))
          .map(ProtoNode::getBlockData)
          .toList();
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  public ForkChoiceState getForkChoiceState(
      final UInt64 currentEpoch,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint) {
    protoArrayLock.readLock().lock();
    try {
      final ProtoNode headNode =
          protoArray.findOptimisticHead(currentEpoch, justifiedCheckpoint, finalizedCheckpoint);
      final UInt64 headExecutionBlockNumber = headNode.getExecutionBlockNumber();
      final Bytes32 headExecutionBlockHash = headNode.getExecutionBlockHash();
      final Bytes32 justifiedExecutionHash =
          protoArray
              .getProtoNode(justifiedCheckpoint.getRoot())
              .map(ProtoNode::getExecutionBlockHash)
              .orElse(Bytes32.ZERO);
      final Bytes32 finalizedExecutionHash =
          protoArray
              .getProtoNode(finalizedCheckpoint.getRoot())
              .map(ProtoNode::getExecutionBlockHash)
              .orElse(Bytes32.ZERO);
      return new ForkChoiceState(
          headNode.getBlockRoot(),
          headNode.getBlockSlot(),
          headExecutionBlockNumber,
          headExecutionBlockHash,
          justifiedExecutionHash,
          finalizedExecutionHash,
          headNode.isOptimistic() || !protoArray.nodeIsViableForHead(headNode));
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<Bytes32> getOptimisticallySyncedTransitionBlockRoot(final Bytes32 head) {
    protoArrayLock.readLock().lock();
    try {
      return protoArray
          .findOptimisticallySyncedMergeTransitionBlock(head)
          .map(ProtoNode::getBlockRoot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  void processAttestation(
      final VoteUpdater voteUpdater,
      final UInt64 validatorIndex,
      final Bytes32 blockRoot,
      final UInt64 targetEpoch) {
    VoteTracker vote = voteUpdater.getVote(validatorIndex);
    // Not updating anything for equivocated validators
    if (vote.isEquivocating()) {
      return;
    }

    if (targetEpoch.isGreaterThan(vote.getNextEpoch()) || vote.equals(VoteTracker.DEFAULT)) {
      VoteTracker newVote = new VoteTracker(vote.getCurrentRoot(), blockRoot, targetEpoch);
      voteUpdater.putVote(validatorIndex, newVote);
    }
  }

  public void setPruneThreshold(final int pruneThreshold) {
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
  public boolean contains(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.contains(blockRoot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<UInt64> blockSlot(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getProtoNode(blockRoot).map(ProtoNode::getBlockSlot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<UInt64> executionBlockNumber(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getProtoNode(blockRoot).map(ProtoNode::getExecutionBlockNumber);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<Bytes32> executionBlockHash(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getProtoNode(blockRoot).map(ProtoNode::getExecutionBlockHash);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<Bytes32> blockParentRoot(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getProtoNode(blockRoot).map(ProtoNode::getParentRoot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public boolean isFullyValidated(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getProtoNode(blockRoot).map(ProtoNode::isFullyValidated).orElse(false);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<ProtoNodeData> getBlockData(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getProtoNode(blockRoot).map(ProtoNode::getBlockData);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<UInt64> getWeight(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getProtoNode(blockRoot).map(ProtoNode::getWeight);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<Boolean> isOptimistic(final Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getProtoNode(blockRoot).map(ProtoNode::isOptimistic);
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
  public List<Bytes32> getBlockRootsAtSlot(final UInt64 slot) {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.getNodes().stream()
          .filter(protoNode -> protoNode.getBlockSlot().equals(slot))
          .map(ProtoNode::getBlockRoot)
          .toList();
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
  public void processHashesInChain(final Bytes32 head, final NodeProcessor processor) {
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
  public void processHashesInChainWhile(
      final Bytes32 head, final HaltableNodeProcessor nodeProcessor) {
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
                currentNode.getParentRoot(),
                currentNode.getExecutionBlockHash());
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
      final Object2IntMap<Bytes32> indices = protoArray.getRootIndices();
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
  public List<ProtoNodeData> getBlockData() {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.getNodes().stream().map(ProtoNode::getBlockData).toList();
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public void applyUpdate(
      final Collection<BlockAndCheckpoints> newBlocks,
      final Collection<Bytes32> pulledUpBlocks,
      final Map<Bytes32, UInt64> removedBlockRoots,
      final Checkpoint finalizedCheckpoint) {
    protoArrayLock.writeLock().lock();
    try {
      newBlocks.stream()
          .sorted(Comparator.comparing(BlockAndCheckpoints::getSlot))
          .forEach(
              block ->
                  processBlock(
                      block.getBlock().getSlot(),
                      block.getBlock().getRoot(),
                      block.getBlock().getParentRoot(),
                      block.getBlock().getStateRoot(),
                      block.getBlockCheckpoints(),
                      block.getExecutionBlockNumber().orElse(ProtoNode.NO_EXECUTION_BLOCK_NUMBER),
                      block.getExecutionBlockHash().orElse(ProtoNode.NO_EXECUTION_BLOCK_HASH)));
      removedBlockRoots.forEach((root, uInt64) -> protoArray.removeBlockRoot(root));
      pulledUpBlocks.forEach(protoArray::pullUpBlockCheckpoints);
      protoArray.maybePrune(finalizedCheckpoint.getRoot());
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  @Override
  public Optional<SlotAndBlockRoot> findCommonAncestor(final Bytes32 root1, final Bytes32 root2) {
    protoArrayLock.readLock().lock();
    try {
      Optional<ProtoNode> chainHead1 = protoArray.getProtoNode(root1);
      Optional<ProtoNode> chainHead2 = protoArray.getProtoNode(root2);
      while (chainHead1.isPresent() && chainHead2.isPresent()) {
        final ProtoNode node1 = chainHead1.get();
        final ProtoNode node2 = chainHead2.get();
        if (node1.getBlockSlot().isGreaterThan(node2.getBlockSlot())) {
          // Chain 1 is longer than chain 2 so need to move further up chain 2
          chainHead1 = node1.getParentIndex().map(protoArray::getNodeByIndex);
        } else if (node2.getBlockSlot().isGreaterThan(node1.getBlockSlot())) {
          // Chain 2 is longer than chain 1 so need to move further up chain 1
          chainHead2 = node2.getParentIndex().map(protoArray::getNodeByIndex);
        } else {
          // At the same slot, check if this is the common ancestor
          if (node1.getBlockRoot().equals(node2.getBlockRoot())) {
            return Optional.of(new SlotAndBlockRoot(node1.getBlockSlot(), node1.getBlockRoot()));
          }
          // Nope, need to move further up both chains
          chainHead1 = node1.getParentIndex().map(protoArray::getNodeByIndex);
          chainHead2 = node2.getParentIndex().map(protoArray::getNodeByIndex);
        }
      }
      // Reached the start of protoarray without finding a common ancestor
      return Optional.empty();
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @VisibleForTesting
  void processBlock(
      final UInt64 blockSlot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final BlockCheckpoints checkpoints,
      final UInt64 executionBlockNumber,
      final Bytes32 executionBlockHash) {
    protoArray.onBlock(
        blockSlot,
        blockRoot,
        parentRoot,
        stateRoot,
        checkpoints,
        executionBlockNumber,
        executionBlockHash,
        spec.isBlockProcessorOptimistic(blockSlot));
  }

  private Optional<ProtoNode> getProtoNode(final Bytes32 blockRoot) {
    return protoArray.getProtoNode(blockRoot);
  }

  public void onExecutionPayloadResult(
      final Bytes32 blockRoot,
      final PayloadStatus result,
      final boolean verifiedInvalidTransition) {
    if (result.hasFailedExecution()) {
      LOG.warn(
          "Unable to execute Payload for block root {}, Execution Engine is offline",
          blockRoot,
          result.getFailureCause().orElseThrow());
      return;
    }
    ExecutionPayloadStatus status = result.getStatus().orElseThrow();
    if (status.isNotValidated()) {
      return;
    }
    protoArrayLock.writeLock().lock();
    try {
      if (status.isValid()) {
        protoArray.markNodeValid(blockRoot);
      } else if (status.isInvalid()) {
        if (verifiedInvalidTransition) {
          LOG.warn("Payload for block root {} marked as invalid by Execution Client", blockRoot);
          protoArray.markNodeInvalid(blockRoot, result.getLatestValidHash());
        } else {
          LOG.warn(
              "Payload for child of block root {} marked as invalid by Execution Client",
              blockRoot);
          protoArray.markParentChainInvalid(blockRoot, result.getLatestValidHash());
        }
      } else {
        throw new IllegalArgumentException("Unknown payload validity status: " + status);
      }
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }
}
