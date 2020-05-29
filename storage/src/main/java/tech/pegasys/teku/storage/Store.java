/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.storage;

import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.FailedPrecommitException;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.util.async.SafeFuture;

public class Store implements ReadOnlyStore {
  private static final Logger LOG = LogManager.getLogger();
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private UnsignedLong time;
  private UnsignedLong genesis_time;
  private Checkpoint justified_checkpoint;
  private Checkpoint finalized_checkpoint;
  private Checkpoint best_justified_checkpoint;
  private Map<Bytes32, SignedBeaconBlock> blocks;
  private Map<Bytes32, BeaconState> block_states;
  private Map<Checkpoint, BeaconState> checkpoint_states;
  private Map<UnsignedLong, VoteTracker> votes;

  private final NavigableMap<UnsignedLong, Set<Bytes32>> rootsBySlotLookup = new TreeMap<>();

  private Store(
      final UnsignedLong time,
      final UnsignedLong genesis_time,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final Map<Bytes32, BeaconState> block_states,
      final Map<Checkpoint, BeaconState> checkpoint_states,
      final Map<UnsignedLong, VoteTracker> votes) {
    this.time = time;
    this.genesis_time = genesis_time;
    this.justified_checkpoint = justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
    this.best_justified_checkpoint = best_justified_checkpoint;
    this.blocks = new ConcurrentHashMap<>(blocks);
    this.block_states = new ConcurrentHashMap<>(block_states);
    this.checkpoint_states = new ConcurrentHashMap<>(checkpoint_states);
    this.votes = new ConcurrentHashMap<>(votes);

    // Setup slot to root mappings
    indexBlockRootsBySlot(rootsBySlotLookup, this.blocks.values());
  }

  public static Store getForkChoiceStore(final BeaconState anchorState) {
    final BeaconBlock anchorBlock = new BeaconBlock(anchorState.hash_tree_root());
    final Bytes32 anchorRoot = anchorBlock.hash_tree_root();
    final UnsignedLong anchorEpoch = BeaconStateUtil.get_current_epoch(anchorState);
    final Checkpoint anchorCheckpoint = new Checkpoint(anchorEpoch, anchorRoot);
    Map<Bytes32, SignedBeaconBlock> blocks = new HashMap<>();
    Map<Bytes32, BeaconState> block_states = new HashMap<>();
    Map<Checkpoint, BeaconState> checkpoint_states = new HashMap<>();
    Map<UnsignedLong, VoteTracker> votes = new HashMap<>();

    blocks.put(anchorRoot, new SignedBeaconBlock(anchorBlock, BLSSignature.empty()));
    block_states.put(anchorRoot, anchorState);
    checkpoint_states.put(anchorCheckpoint, anchorState);

    return create(
        anchorState
            .getGenesis_time()
            .plus(UnsignedLong.valueOf(SECONDS_PER_SLOT).times(anchorState.getSlot())),
        anchorState.getGenesis_time(),
        anchorCheckpoint,
        anchorCheckpoint,
        anchorCheckpoint,
        blocks,
        block_states,
        checkpoint_states,
        votes);
  }

  public static Store createByRegeneratingHotStates(
      final UnsignedLong time,
      final UnsignedLong genesis_time,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final Map<Checkpoint, BeaconState> checkpoint_states,
      final Map<UnsignedLong, VoteTracker> votes) {

    final Map<Bytes32, BeaconState> blockStates =
        StateTransition.produceStatesForBlocks(
            finalized_checkpoint.getRoot(),
            checkpoint_states.get(finalized_checkpoint),
            blocks.values());

    // If we couldn't regenerate states, log a warning
    if (blockStates.size() < blocks.size()) {
      LOG.warn("Unable to regenerate some hot states from hot blocks");

      // Drop any blocks for which a state couldn't be generated
      new HashSet<>(Sets.difference(blocks.keySet(), blockStates.keySet())).forEach(blocks::remove);
    }

    return create(
        time,
        genesis_time,
        justified_checkpoint,
        finalized_checkpoint,
        best_justified_checkpoint,
        blocks,
        blockStates,
        checkpoint_states,
        votes);
  }

  public static Store create(
      final UnsignedLong time,
      final UnsignedLong genesis_time,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final Map<Bytes32, BeaconState> block_states,
      final Map<Checkpoint, BeaconState> checkpoint_states,
      final Map<UnsignedLong, VoteTracker> votes) {
    return new Store(
        time,
        genesis_time,
        justified_checkpoint,
        finalized_checkpoint,
        best_justified_checkpoint,
        blocks,
        block_states,
        checkpoint_states,
        votes);
  }

  private void indexBlockRootsBySlot(
      final Map<UnsignedLong, Set<Bytes32>> index, final Collection<SignedBeaconBlock> blocks) {
    blocks.forEach(b -> indexBlockRootBySlot(index, b));
  }

  private void indexBlockRootBySlot(
      final Map<UnsignedLong, Set<Bytes32>> index, SignedBeaconBlock block) {
    final Bytes32 root = block.getRoot();
    final UnsignedLong slot = block.getSlot();
    index.computeIfAbsent(slot, key -> new HashSet<>()).add(root);
  }

  private void removeBlockRootFromSlotIndex(
      final Map<UnsignedLong, Set<Bytes32>> index, final UnsignedLong slot, final Bytes32 root) {
    index.computeIfPresent(
        slot,
        (s, roots) -> {
          roots.remove(root);
          return roots.isEmpty() ? null : roots;
        });
  }

  private void removeBlockRootFromSlotIndex(
      final Map<UnsignedLong, Set<Bytes32>> index, SignedBeaconBlock block) {
    removeBlockRootFromSlotIndex(index, block.getSlot(), block.getRoot());
  }

  public Transaction startTransaction(final StorageUpdateChannel storageUpdateChannel) {
    return startTransaction(storageUpdateChannel, StoreUpdateHandler.NOOP);
  }

  public Transaction startTransaction(
      final StorageUpdateChannel storageUpdateChannel, final StoreUpdateHandler updateHandler) {
    return new Transaction(storageUpdateChannel, updateHandler);
  }

  @Override
  public UnsignedLong getTime() {
    readLock.lock();
    try {
      return time;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public UnsignedLong getGenesisTime() {
    readLock.lock();
    try {
      return genesis_time;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Checkpoint getJustifiedCheckpoint() {
    readLock.lock();
    try {
      return justified_checkpoint;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Checkpoint getFinalizedCheckpoint() {
    readLock.lock();
    try {
      return finalized_checkpoint;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public UnsignedLong getLatestFinalizedBlockSlot() {
    readLock.lock();
    try {
      return blocks.get(finalized_checkpoint.getRoot()).getSlot();
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Checkpoint getBestJustifiedCheckpoint() {
    readLock.lock();
    try {
      return best_justified_checkpoint;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public BeaconBlock getBlock(Bytes32 blockRoot) {
    final SignedBeaconBlock signedBlock = getSignedBlock(blockRoot);
    return signedBlock != null ? signedBlock.getMessage() : null;
  }

  @Override
  public SignedBeaconBlock getSignedBlock(Bytes32 blockRoot) {
    readLock.lock();
    try {
      return blocks.get(blockRoot);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Optional<SignedBlockAndState> getBlockAndState(final Bytes32 blockRoot) {
    readLock.lock();
    try {
      final SignedBeaconBlock block = blocks.get(blockRoot);
      final BeaconState state = block_states.get(blockRoot);
      if (block == null || state == null) {
        return Optional.empty();
      }
      return Optional.of(new SignedBlockAndState(block, state));
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public boolean containsBlock(Bytes32 blockRoot) {
    readLock.lock();
    try {
      return blocks.containsKey(blockRoot);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Set<Bytes32> getBlockRoots() {
    readLock.lock();
    try {
      return Collections.unmodifiableSet(blocks.keySet());
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public BeaconState getBlockState(Bytes32 blockRoot) {
    readLock.lock();
    try {
      return block_states.get(blockRoot);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public boolean containsBlockState(Bytes32 blockRoot) {
    readLock.lock();
    try {
      return block_states.containsKey(blockRoot);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public BeaconState getCheckpointState(Checkpoint checkpoint) {
    readLock.lock();
    try {
      return checkpoint_states.get(checkpoint);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public boolean containsCheckpointState(Checkpoint checkpoint) {
    readLock.lock();
    try {
      return checkpoint_states.containsKey(checkpoint);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Set<UnsignedLong> getVotedValidatorIndices() {
    readLock.lock();
    try {
      return votes.keySet();
    } finally {
      readLock.unlock();
    }
  }

  public class Transaction implements MutableStore {

    private final StorageUpdateChannel storageUpdateChannel;
    private Optional<UnsignedLong> time = Optional.empty();
    private Optional<UnsignedLong> genesis_time = Optional.empty();
    private Optional<Checkpoint> justified_checkpoint = Optional.empty();
    private Optional<Checkpoint> finalized_checkpoint = Optional.empty();
    private Optional<Checkpoint> best_justified_checkpoint = Optional.empty();
    private Map<Bytes32, SignedBeaconBlock> blocks = new HashMap<>();
    private Map<Bytes32, BeaconState> block_states = new HashMap<>();
    private Map<Checkpoint, BeaconState> checkpoint_states = new HashMap<>();
    private Map<UnsignedLong, VoteTracker> votes = new ConcurrentHashMap<>();
    private final StoreUpdateHandler updateHandler;

    Transaction(
        final StorageUpdateChannel storageUpdateChannel, final StoreUpdateHandler updateHandler) {
      this.storageUpdateChannel = storageUpdateChannel;
      this.updateHandler = updateHandler;
    }

    @Override
    public void putCheckpointState(Checkpoint checkpoint, BeaconState state) {
      checkpoint_states.put(checkpoint, state);
    }

    @Override
    public void putBlockAndState(SignedBeaconBlock block, BeaconState state) {
      blocks.put(block.getRoot(), block);
      block_states.put(block.getRoot(), state);
    }

    @Override
    public void setTime(UnsignedLong time) {
      this.time = Optional.of(time);
    }

    @Override
    public void setGenesis_time(UnsignedLong genesis_time) {
      this.genesis_time = Optional.of(genesis_time);
    }

    @Override
    public void setJustifiedCheckpoint(Checkpoint justified_checkpoint) {
      this.justified_checkpoint = Optional.of(justified_checkpoint);
    }

    @Override
    public void setFinalizedCheckpoint(Checkpoint finalized_checkpoint) {
      this.finalized_checkpoint = Optional.of(finalized_checkpoint);
    }

    @Override
    public void setBestJustifiedCheckpoint(Checkpoint best_justified_checkpoint) {
      this.best_justified_checkpoint = Optional.of(best_justified_checkpoint);
    }

    @Override
    public VoteTracker getVote(UnsignedLong validatorIndex) {
      VoteTracker vote = votes.get(validatorIndex);
      if (vote == null) {
        vote = Store.this.votes.get(validatorIndex);
        if (vote == null) {
          vote = VoteTracker.Default();
        } else {
          vote = vote.copy();
        }
        votes.put(validatorIndex, vote);
      }
      return vote;
    }

    @CheckReturnValue
    public SafeFuture<Void> commit() {
      final Map<Bytes32, SignedBeaconBlock> hotBlocks = new HashMap<>();
      // To start all blocks are assumed to be hot blocks
      hotBlocks.putAll(blocks);

      // If a new checkpoint has been finalized, calculated what to finalize and what to prune
      final UnsignedLong previouslyFinalizedEpoch = Store.this.finalized_checkpoint.getEpoch();
      final Optional<UnsignedLong> newlyFinalizedEpoch =
          finalized_checkpoint
              .map(Checkpoint::getEpoch)
              .filter(epoch -> epoch.compareTo(previouslyFinalizedEpoch) > 0);
      // Calculate finalized chain data
      final Map<Bytes32, SignedBlockAndState> finalizedChainData;
      final Map<UnsignedLong, Set<Bytes32>> prunedHotBlockRoots;
      final Set<Checkpoint> staleCheckpointStates;
      final Optional<SignedBeaconBlock> newlyFinalizedBlock;
      if (newlyFinalizedEpoch.isPresent()) {
        final SignedBlockAndState previouslyFinalizedBlock =
            getBlockAndState(Store.this.finalized_checkpoint.getRoot())
                .orElseThrow(() -> new IllegalStateException("Finalized block is missing"));
        final SignedBlockAndState newlyFinalizedBlockAndState =
            getBlockAndState(finalized_checkpoint.get().getRoot())
                .orElseThrow(() -> new IllegalStateException("Newly finalized block is missing"));
        newlyFinalizedBlock = Optional.of(newlyFinalizedBlockAndState.getBlock());

        finalizedChainData =
            getFinalizedChainData(previouslyFinalizedBlock, newlyFinalizedBlockAndState);
        prunedHotBlockRoots =
            getPrunedHotBlockRoots(
                finalized_checkpoint.get(), newlyFinalizedBlock.get(), hotBlocks);
        // Collect stale checkpoint states to be deleted
        staleCheckpointStates =
            Store.this.checkpoint_states.keySet().stream()
                .filter(c -> c.getEpoch().compareTo(newlyFinalizedEpoch.get()) < 0)
                .collect(Collectors.toSet());

        // Make sure we save the checkpoint state for the new finalized checkpoint
        checkpoint_states.put(finalized_checkpoint.get(), newlyFinalizedBlockAndState.getState());

        // Remove pruned blocks from hot blocks
        prunedHotBlockRoots.forEach((slot, roots) -> roots.forEach(hotBlocks::remove));
      } else {
        newlyFinalizedBlock = Optional.empty();
        finalizedChainData = Collections.emptyMap();
        prunedHotBlockRoots = Collections.emptyMap();
        staleCheckpointStates = Collections.emptySet();
      }

      final Set<Bytes32> prunedRootsSet =
          prunedHotBlockRoots.values().stream()
              .flatMap(roots -> roots.stream())
              .collect(Collectors.toSet());
      final StorageUpdate updateEvent =
          new StorageUpdate(
              genesis_time,
              justified_checkpoint,
              finalized_checkpoint,
              best_justified_checkpoint,
              hotBlocks,
              prunedRootsSet,
              finalizedChainData,
              checkpoint_states,
              staleCheckpointStates,
              votes);
      return storageUpdateChannel
          .onStorageUpdate(updateEvent)
          .thenAccept(
              updateResult -> {
                if (!updateResult.isSuccessful()) {
                  throw new FailedPrecommitException(updateResult);
                }
                final Lock writeLock = Store.this.lock.writeLock();
                writeLock.lock();
                try {
                  // Add new data
                  time.ifPresent(value -> Store.this.time = value);
                  genesis_time.ifPresent(value -> Store.this.genesis_time = value);
                  justified_checkpoint.ifPresent(value -> Store.this.justified_checkpoint = value);
                  finalized_checkpoint.ifPresent(value -> Store.this.finalized_checkpoint = value);
                  best_justified_checkpoint.ifPresent(
                      value -> Store.this.best_justified_checkpoint = value);
                  Store.this.blocks.putAll(blocks);
                  Store.this.block_states.putAll(block_states);
                  Store.this.checkpoint_states.putAll(checkpoint_states);
                  Store.this.votes.putAll(votes);
                  // Track roots by slot
                  indexBlockRootsBySlot(rootsBySlotLookup, hotBlocks.values());

                  // Prune old data if we updated our finalized epoch
                  if (newlyFinalizedBlock.isPresent()) {
                    // Prune stale checkpoint states
                    staleCheckpointStates.forEach(Store.this.checkpoint_states::remove);
                    // Prune finalized blocks and states
                    prunedHotBlockRoots.forEach(
                        (slot, roots) -> {
                          roots.forEach(
                              (root) -> {
                                Store.this.blocks.remove(root);
                                Store.this.block_states.remove(root);
                                removeBlockRootFromSlotIndex(rootsBySlotLookup, slot, root);
                              });
                        });
                  }
                } finally {
                  writeLock.unlock();
                }

                // Signal back changes to the handler
                finalized_checkpoint.ifPresent(updateHandler::onNewFinalizedCheckpoint);
              });
    }

    private Map<Bytes32, SignedBlockAndState> getFinalizedChainData(
        final SignedBlockAndState previouslyFinalizedBlock,
        final SignedBlockAndState newlyFinalizedBlock) {
      final Map<Bytes32, SignedBlockAndState> finalizedChainData = new HashMap<>();

      SignedBlockAndState oldestFinalizedBlock = newlyFinalizedBlock;
      SignedBlockAndState currentBlock = newlyFinalizedBlock;
      while (currentBlock != null
          && currentBlock.getSlot().compareTo(previouslyFinalizedBlock.getSlot()) > 0) {
        finalizedChainData.put(currentBlock.getRoot(), currentBlock);
        oldestFinalizedBlock = currentBlock;
        currentBlock = getBlockAndState(currentBlock.getParentRoot()).orElse(null);
      }

      // Make sure we capture all finalized blocks
      if (!oldestFinalizedBlock.getParentRoot().equals(previouslyFinalizedBlock.getRoot())) {
        throw new IllegalStateException("Unable to retrieve all finalized blocks");
      }

      return finalizedChainData;
    }

    private Map<UnsignedLong, Set<Bytes32>> getPrunedHotBlockRoots(
        final Checkpoint newFinalizedCheckpoint,
        final SignedBeaconBlock finalizedBlock,
        final Map<Bytes32, SignedBeaconBlock> newBlocks) {
      final UnsignedLong finalizedSlot = newFinalizedCheckpoint.getEpochStartSlot();
      Map<UnsignedLong, Set<Bytes32>> prunedBlockRoots = new HashMap<>();

      // Build combined index from slot to block root for new and existing blocks
      final NavigableMap<UnsignedLong, Set<Bytes32>> slotToHotBlockRootIndex = new TreeMap<>();
      indexBlockRootsBySlot(slotToHotBlockRootIndex, newBlocks.values());
      slotToHotBlockRootIndex.putAll(rootsBySlotLookup);

      // Prune historical blocks
      slotToHotBlockRootIndex
          .headMap(finalizedSlot, true)
          .forEach(
              (slot, roots) ->
                  prunedBlockRoots.computeIfAbsent(slot, __ -> new HashSet<>()).addAll(roots));

      // Prune any hot blocks that do not descend from the latest finalized block
      final Set<Bytes32> canonicalBlocks = new HashSet<>();
      canonicalBlocks.add(finalizedBlock.getRoot());
      slotToHotBlockRootIndex
          .tailMap(finalizedSlot, true)
          .forEach(
              (slot, roots) -> {
                for (Bytes32 root : roots) {
                  final BeaconBlock block = getBlock(root);
                  final Bytes32 blockRoot = block.hash_tree_root();
                  if (canonicalBlocks.contains(block.getParent_root())) {
                    canonicalBlocks.add(blockRoot);
                  } else {
                    // Prune block
                    prunedBlockRoots
                        .computeIfAbsent(block.getSlot(), __ -> new HashSet<>())
                        .add(blockRoot);
                  }
                }
              });

      // Don't prune the latest finalized block
      removeBlockRootFromSlotIndex(prunedBlockRoots, finalizedBlock);

      return prunedBlockRoots;
    }

    public void commit(final Runnable onSuccess, final String errorMessage) {
      commit(onSuccess, err -> LOG.error(errorMessage, err));
    }

    public void commit(final Runnable onSuccess, final Consumer<Throwable> onError) {
      commit().finish(onSuccess, onError);
    }

    @Override
    public UnsignedLong getTime() {
      return time.orElseGet(Store.this::getTime);
    }

    @Override
    public UnsignedLong getGenesisTime() {
      return genesis_time.orElseGet(Store.this::getGenesisTime);
    }

    @Override
    public Checkpoint getJustifiedCheckpoint() {
      return justified_checkpoint.orElseGet(Store.this::getJustifiedCheckpoint);
    }

    @Override
    public Checkpoint getFinalizedCheckpoint() {
      return finalized_checkpoint.orElseGet(Store.this::getFinalizedCheckpoint);
    }

    @Override
    public UnsignedLong getLatestFinalizedBlockSlot() {
      return getBlock(getFinalizedCheckpoint().getRoot()).getSlot();
    }

    @Override
    public Checkpoint getBestJustifiedCheckpoint() {
      return best_justified_checkpoint.orElseGet(Store.this::getBestJustifiedCheckpoint);
    }

    @Override
    public BeaconBlock getBlock(final Bytes32 blockRoot) {
      final SignedBeaconBlock signedBlock = getSignedBlock(blockRoot);
      return signedBlock != null ? signedBlock.getMessage() : null;
    }

    @Override
    public SignedBeaconBlock getSignedBlock(final Bytes32 blockRoot) {
      return either(blockRoot, blocks::get, Store.this::getSignedBlock);
    }

    @Override
    public Optional<SignedBlockAndState> getBlockAndState(final Bytes32 blockRoot) {
      final SignedBeaconBlock block = getSignedBlock(blockRoot);
      final BeaconState state = getBlockState(blockRoot);
      if (block == null || state == null) {
        return Optional.empty();
      }
      return Optional.of(new SignedBlockAndState(block, state));
    }

    @Override
    public boolean containsBlock(final Bytes32 blockRoot) {
      return blocks.containsKey(blockRoot) || Store.this.containsBlock(blockRoot);
    }

    @Override
    public Set<Bytes32> getBlockRoots() {
      return Sets.union(blocks.keySet(), Store.this.getBlockRoots());
    }

    @Override
    public BeaconState getBlockState(final Bytes32 blockRoot) {
      return either(blockRoot, block_states::get, Store.this::getBlockState);
    }

    @Override
    public Set<UnsignedLong> getVotedValidatorIndices() {
      return Sets.union(votes.keySet(), Store.this.getVotedValidatorIndices());
    }

    private <I, O> O either(I input, Function<I, O> primary, Function<I, O> secondary) {
      final O primaryValue = primary.apply(input);
      return primaryValue != null ? primaryValue : secondary.apply(input);
    }

    @Override
    public boolean containsBlockState(final Bytes32 blockRoot) {
      return block_states.containsKey(blockRoot) || Store.this.containsBlockState(blockRoot);
    }

    @Override
    public BeaconState getCheckpointState(final Checkpoint checkpoint) {
      return either(checkpoint, checkpoint_states::get, Store.this::getCheckpointState);
    }

    @Override
    public boolean containsCheckpointState(final Checkpoint checkpoint) {
      return checkpoint_states.containsKey(checkpoint)
          || Store.this.containsCheckpointState(checkpoint);
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Store)) {
      return false;
    }
    final Store store = (Store) o;
    return Objects.equals(time, store.time)
        && Objects.equals(genesis_time, store.genesis_time)
        && Objects.equals(justified_checkpoint, store.justified_checkpoint)
        && Objects.equals(finalized_checkpoint, store.finalized_checkpoint)
        && Objects.equals(best_justified_checkpoint, store.best_justified_checkpoint)
        && Objects.equals(blocks, store.blocks)
        && Objects.equals(block_states, store.block_states)
        && Objects.equals(checkpoint_states, store.checkpoint_states);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        time,
        genesis_time,
        justified_checkpoint,
        finalized_checkpoint,
        best_justified_checkpoint,
        blocks,
        block_states,
        checkpoint_states);
  }

  public interface StoreUpdateHandler {
    StoreUpdateHandler NOOP = finalizedCheckpoint -> {};

    void onNewFinalizedCheckpoint(Checkpoint finalizedCheckpoint);
  }
}
