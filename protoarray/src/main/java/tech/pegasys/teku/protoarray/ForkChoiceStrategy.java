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
import com.google.common.collect.ImmutableMap;
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
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProposerWeighting;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ForkChoiceStrategy implements BlockMetadataStore, ReadOnlyForkChoiceStrategy {
  private static final Logger LOG = LogManager.getLogger();
  private final ReadWriteLock protoArrayLock = new ReentrantReadWriteLock();
  private final ReadWriteLock votesLock = new ReentrantReadWriteLock();
  private final ReadWriteLock balancesLock = new ReentrantReadWriteLock();
  private final ProtoArray protoArray;

  private List<UInt64> balances;

  private ForkChoiceStrategy(ProtoArray protoArray, List<UInt64> balances) {
    this.protoArray = protoArray;
    this.balances = balances;
  }

  // Public
  public static SafeFuture<ForkChoiceStrategy> initializeAndMigrateStorage(
      ReadOnlyStore store, ProtoArrayStorageChannel storageChannel) {
    LOG.info("Migrating protoarray storing from snapshot to block based");
    // If no initialEpoch is explicitly set, default to zero (genesis epoch)
    final UInt64 initialEpoch =
        store.getInitialCheckpoint().map(Checkpoint::getEpoch).orElse(SpecConstants.GENESIS_EPOCH);
    return storageChannel
        .getProtoArraySnapshot()
        .thenApply(
            maybeSnapshot ->
                maybeSnapshot
                    .map(ProtoArraySnapshot::toProtoArray)
                    .orElse(
                        ProtoArray.builder()
                            .justifiedCheckpoint(store.getJustifiedCheckpoint())
                            .finalizedCheckpoint(store.getFinalizedCheckpoint())
                            .initialEpoch(initialEpoch)
                            .build()))
        .thenCompose(protoArray -> processBlocksInStoreAtStartup(store, protoArray))
        .thenPeek(
            protoArray -> storageChannel.onProtoArrayUpdate(ProtoArraySnapshot.create(protoArray)))
        .thenApply(ForkChoiceStrategy::initialize);
  }

  public static ForkChoiceStrategy initialize(final ProtoArray protoArray) {
    return new ForkChoiceStrategy(protoArray, new ArrayList<>());
  }

  public SlotAndBlockRoot findHead(final Checkpoint justifiedCheckpoint) {
    protoArrayLock.readLock().lock();
    try {
      final ProtoNode bestNode = protoArray.findHead(justifiedCheckpoint.getRoot());
      return new SlotAndBlockRoot(bestNode.getBlockSlot(), bestNode.getBlockRoot());
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  /**
   * Applies the weighting changes from any updated votes then finds and returns the best chain
   * head.
   *
   * @param voteUpdater the vote updater to access and update pending votes from
   * @param removedProposerWeightings expired proposer weightings to be removed
   * @param finalizedCheckpoint the current finalized checkpoint
   * @param justifiedCheckpoint the current justified checkpoint
   * @param justifiedStateEffectiveBalances the effective validator balances at the justified
   *     checkpoint
   * @return the best chain head block root
   */
  public Bytes32 applyPendingVotes(
      final VoteUpdater voteUpdater,
      final List<ProposerWeighting> removedProposerWeightings,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final List<UInt64> justifiedStateEffectiveBalances) {
    return applyPendingVotes(
        voteUpdater,
        justifiedCheckpoint.getEpoch(),
        justifiedCheckpoint.getRoot(),
        finalizedCheckpoint.getEpoch(),
        justifiedStateEffectiveBalances,
        removedProposerWeightings);
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
    protoArrayLock.readLock().lock();
    try {
      final Map<Bytes32, UInt64> chainHeads = new HashMap<>();
      protoArray.getNodes().stream()
          .filter(
              protoNode ->
                  protoNode.getBestChildIndex().isEmpty()
                      && protoArray.nodeIsViableForHead(protoNode))
          .forEach(protoNode -> chainHeads.put(protoNode.getBlockRoot(), protoNode.getBlockSlot()));
      return ImmutableMap.copyOf(chainHeads);
    } catch (Throwable t) {
      LOG.trace("Failed to get chain heads", t);
      return Collections.emptyMap();
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  // Internal
  private static SafeFuture<ProtoArray> processBlocksInStoreAtStartup(
      ReadOnlyStore store, ProtoArray protoArray) {
    List<Bytes32> alreadyIncludedBlockRoots =
        protoArray.getNodes().stream().map(ProtoNode::getBlockRoot).collect(Collectors.toList());

    SafeFuture<Void> future = SafeFuture.completedFuture(null);
    for (Bytes32 blockRoot : store.getOrderedBlockRoots()) {
      if (alreadyIncludedBlockRoots.contains(blockRoot)) {
        continue;
      }
      future =
          future.thenCompose(
              __ ->
                  store
                      .retrieveStateAndBlockSummary(blockRoot)
                      .thenAccept(
                          blockAndState ->
                              processBlockAtStartup(protoArray, blockAndState.orElseThrow())));
    }
    return future.thenApply(__ -> protoArray);
  }

  private static void processBlockAtStartup(
      final ProtoArray protoArray, final StateAndBlockSummary blockAndState) {
    final BeaconState state = blockAndState.getState();
    protoArray.onBlock(
        blockAndState.getSlot(),
        blockAndState.getRoot(),
        blockAndState.getParentRoot(),
        blockAndState.getStateRoot(),
        state.getCurrent_justified_checkpoint().getEpoch(),
        state.getFinalized_checkpoint().getEpoch());
  }

  void processAttestation(
      VoteUpdater voteUpdater, UInt64 validatorIndex, Bytes32 blockRoot, UInt64 targetEpoch) {
    VoteTracker vote = voteUpdater.getVote(validatorIndex);

    if (targetEpoch.isGreaterThan(vote.getNextEpoch()) || vote.equals(VoteTracker.DEFAULT)) {
      VoteTracker newVote = new VoteTracker(vote.getCurrentRoot(), blockRoot, targetEpoch);
      voteUpdater.putVote(validatorIndex, newVote);
    }
  }

  Bytes32 applyPendingVotes(
      VoteUpdater voteUpdater,
      UInt64 justifiedEpoch,
      Bytes32 justifiedRoot,
      UInt64 finalizedEpoch,
      List<UInt64> justifiedStateBalances,
      final List<ProposerWeighting> removedProposerWeightings) {
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
              justifiedStateBalances,
              removedProposerWeightings);

      protoArray.applyScoreChanges(deltas, justifiedEpoch, finalizedEpoch);
      balances = justifiedStateBalances;

      return protoArray.findHead(justifiedRoot).getBlockRoot();
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
  public BlockMetadataStore applyUpdate(
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
                      block.getCheckpointEpochs().getFinalizedEpoch()));
      removedBlockRoots.forEach(protoArray::removeBlockRoot);
      protoArray.maybePrune(finalizedCheckpoint.getRoot());
    } finally {
      protoArrayLock.writeLock().unlock();
    }
    return this;
  }

  public void applyProposerWeighting(final ProposerWeighting proposerWeighting) {
    protoArrayLock.writeLock().lock();
    try {
      protoArray.applyProposerWeighting(proposerWeighting);
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
      UInt64 finalizedEpoch) {
    protoArray.onBlock(blockSlot, blockRoot, parentRoot, stateRoot, justifiedEpoch, finalizedEpoch);
  }

  private Optional<ProtoNode> getProtoNode(Bytes32 blockRoot) {
    return protoArray.getProtoNode(blockRoot);
  }
}
