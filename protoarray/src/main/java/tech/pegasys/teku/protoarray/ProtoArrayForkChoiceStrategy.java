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

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

public class ProtoArrayForkChoiceStrategy implements ForkChoiceStrategy {
  private static final Logger LOG = LogManager.getLogger();
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

  // Public
  public static SafeFuture<ProtoArrayForkChoiceStrategy> initialize(
      ReadOnlyStore store, ProtoArrayStorageChannel storageChannel) {
    // If no initialEpoch is explicitly set, default to zero (genesis epoch)
    final UInt64 initialEpoch =
        store
            .getInitialCheckpoint()
            .map(Checkpoint::getEpoch)
            .orElse(UInt64.valueOf(Constants.GENESIS_EPOCH));
    ProtoArray protoArray =
        storageChannel
            .getProtoArraySnapshot()
            .join()
            .map(ProtoArraySnapshot::toProtoArray)
            .orElse(
                new ProtoArray(
                    Constants.PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD,
                    store.getJustifiedCheckpoint().getEpoch(),
                    store.getFinalizedCheckpoint().getEpoch(),
                    initialEpoch,
                    new ArrayList<>(),
                    new HashMap<>()));

    return processBlocksInStoreAtStartup(store, protoArray)
        .thenApply(
            __ -> new ProtoArrayForkChoiceStrategy(protoArray, new ArrayList<>(), storageChannel));
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

  @Override
  public void save() {
    protoArrayLock.readLock().lock();
    try {
      storageChannel.onProtoArrayUpdate(ProtoArraySnapshot.create(protoArray));
    } finally {
      protoArrayLock.readLock().unlock();
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
  private static SafeFuture<Void> processBlocksInStoreAtStartup(
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
                      .retrieveBlockAndState(blockRoot)
                      .thenAccept(
                          blockAndState ->
                              processBlockAtStartup(protoArray, blockAndState.orElseThrow())));
    }
    return future;
  }

  private static void processBlockAtStartup(
      final ProtoArray protoArray, final SignedBlockAndState blockAndState) {
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
      MutableStore store, UInt64 validatorIndex, Bytes32 blockRoot, UInt64 targetEpoch) {
    VoteTracker vote = store.getVote(validatorIndex);

    if (targetEpoch.isGreaterThan(vote.getNextEpoch()) || vote.equals(VoteTracker.Default())) {
      vote.setNextRoot(blockRoot);
      vote.setNextEpoch(targetEpoch);
    }
  }

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
}
