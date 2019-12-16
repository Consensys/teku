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

package tech.pegasys.artemis.storage;

import static tech.pegasys.artemis.util.config.Constants.GENESIS_EPOCH;

import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Checkpoint;

public class Store implements ReadOnlyStore {

  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private UnsignedLong time;
  private UnsignedLong genesis_time;
  private Checkpoint justified_checkpoint;
  private Checkpoint finalized_checkpoint;
  private Checkpoint best_justified_checkpoint;
  private Map<Bytes32, BeaconBlock> blocks;
  private Map<Bytes32, BeaconState> block_states;
  private Map<Checkpoint, BeaconState> checkpoint_states;
  private Map<UnsignedLong, Checkpoint> latest_messages;

  public Store(
      final UnsignedLong time,
      final UnsignedLong genesis_time,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final Map<Bytes32, BeaconBlock> blocks,
      final Map<Bytes32, BeaconState> block_states,
      final Map<Checkpoint, BeaconState> checkpoint_states,
      final Map<UnsignedLong, Checkpoint> latest_messages) {
    this.time = time;
    this.genesis_time = genesis_time;
    this.justified_checkpoint = justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
    this.best_justified_checkpoint = best_justified_checkpoint;
    this.blocks = new ConcurrentHashMap<>(blocks);
    this.block_states = new ConcurrentHashMap<>(block_states);
    this.checkpoint_states = new ConcurrentHashMap<>(checkpoint_states);
    this.latest_messages = new ConcurrentHashMap<>(latest_messages);
  }

  public static Store get_genesis_store(final BeaconState genesisState) {
    BeaconBlock genesisBlock = new BeaconBlock(genesisState.hash_tree_root());
    Bytes32 root = genesisBlock.signing_root("signature");

    Checkpoint justified_checkpoint = new Checkpoint(UnsignedLong.valueOf(GENESIS_EPOCH), root);
    Checkpoint finalized_checkpoint = new Checkpoint(UnsignedLong.valueOf(GENESIS_EPOCH), root);
    Map<Bytes32, BeaconBlock> blocks = new HashMap<>();
    Map<Bytes32, BeaconState> block_states = new HashMap<>();
    Map<Checkpoint, BeaconState> checkpoint_states = new HashMap<>();
    Map<UnsignedLong, Checkpoint> latest_messages = new HashMap<>();

    blocks.put(root, genesisBlock);
    block_states.put(root, BeaconStateWithCache.deepCopy(genesisState));
    checkpoint_states.put(justified_checkpoint, BeaconStateWithCache.deepCopy(genesisState));

    return new Store(
        genesisState.getGenesis_time(),
        genesisState.getGenesis_time(),
        justified_checkpoint,
        finalized_checkpoint,
        justified_checkpoint,
        blocks,
        block_states,
        checkpoint_states,
        latest_messages);
  }

  public Transaction startTransaction() {
    return new Transaction();
  }

  public void cleanStoreUntilSlot(UnsignedLong slot) {
    // Find keys of objects to clean from store
    Set<Bytes32> blocks = new HashSet<>();
    Set<Bytes32> block_states = new HashSet<>();
    Set<Checkpoint> checkpoint_states = new HashSet<>();

    this.blocks.forEach(
        (key, block) -> {
          if (block.getSlot().compareTo(slot) < 0) {
            blocks.add(key);
          }
        });

    this.block_states.forEach(
        (key, state) -> {
          if (state.getSlot().compareTo(slot) < 0) {
            block_states.add(key);
          }
        });

    this.checkpoint_states.forEach(
        (key, state) -> {
          if (state.getSlot().compareTo(slot) < 0) {
            checkpoint_states.add(key);
          }
        });

    Transaction cleanTransaction = new Transaction();
    cleanTransaction.setKeysToBeCleaned(blocks, block_states, checkpoint_states);
    cleanTransaction.commit();
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
    readLock.lock();
    try {
      return blocks.get(blockRoot);
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
  public Checkpoint getLatestMessage(UnsignedLong validatorIndex) {
    readLock.lock();
    try {
      return latest_messages.get(validatorIndex);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public boolean containsLatestMessage(UnsignedLong validatorIndex) {
    readLock.lock();
    try {
      return latest_messages.containsKey(validatorIndex);
    } finally {
      readLock.unlock();
    }
  }

  public class Transaction implements ReadOnlyStore {
    private Optional<UnsignedLong> time = Optional.empty();
    private Optional<UnsignedLong> genesis_time = Optional.empty();
    private Optional<Checkpoint> justified_checkpoint = Optional.empty();
    private Optional<Checkpoint> finalized_checkpoint = Optional.empty();
    private Optional<Checkpoint> best_justified_checkpoint = Optional.empty();
    private Map<Bytes32, BeaconBlock> blocks = new HashMap<>();
    private Map<Bytes32, BeaconState> block_states = new HashMap<>();
    private Map<Checkpoint, BeaconState> checkpoint_states = new HashMap<>();
    private Map<UnsignedLong, Checkpoint> latest_messages = new HashMap<>();

    // Keys to be removed from Store for memory cleaning purposes
    private Set<Bytes32> block_keys = new HashSet<>();
    private Set<Bytes32> block_state_keys = new HashSet<>();
    private Set<Checkpoint> checkpoint_state_keys = new HashSet<>();

    public void putLatestMessage(UnsignedLong validatorIndex, Checkpoint latestMessage) {
      latest_messages.put(validatorIndex, latestMessage);
    }

    public void putCheckpointState(Checkpoint checkpoint, BeaconState state) {
      checkpoint_states.put(checkpoint, state);
    }

    public void putBlockState(Bytes32 blockRoot, BeaconState state) {
      block_states.put(blockRoot, state);
    }

    public void putBlock(Bytes32 blockRoot, BeaconBlock block) {
      blocks.put(blockRoot, block);
    }

    public void setTime(UnsignedLong time) {
      this.time = Optional.of(time);
    }

    public void setGenesis_time(UnsignedLong genesis_time) {
      this.genesis_time = Optional.of(genesis_time);
    }

    public void setJustifiedCheckpoint(Checkpoint justified_checkpoint) {
      this.justified_checkpoint = Optional.of(justified_checkpoint);
    }

    public void setFinalizedCheckpoint(Checkpoint finalized_checkpoint) {
      this.finalized_checkpoint = Optional.of(finalized_checkpoint);
    }

    public void setBestJustifiedCheckpoint(Checkpoint best_justified_checkpoint) {
      this.best_justified_checkpoint = Optional.of(best_justified_checkpoint);
    }

    public void setKeysToBeCleaned(
        Set<Bytes32> block_keys,
        Set<Bytes32> block_state_keys,
        Set<Checkpoint> checkpoint_state_keys) {
      this.block_keys = block_keys;
      this.block_state_keys = block_state_keys;
      this.checkpoint_state_keys = checkpoint_state_keys;
    }

    public void commit() {
      final Lock writeLock = Store.this.lock.writeLock();
      writeLock.lock();
      try {
        time.ifPresent(value -> Store.this.time = value);
        genesis_time.ifPresent(value -> Store.this.genesis_time = value);
        justified_checkpoint.ifPresent(value -> Store.this.justified_checkpoint = value);
        finalized_checkpoint.ifPresent(value -> Store.this.finalized_checkpoint = value);
        best_justified_checkpoint.ifPresent(value -> Store.this.best_justified_checkpoint = value);
        Store.this.blocks.putAll(blocks);
        Store.this.block_states.putAll(block_states);
        Store.this.checkpoint_states.putAll(checkpoint_states);
        Store.this.latest_messages.putAll(latest_messages);

        block_keys.forEach(key -> Store.this.blocks.remove(key));
        block_state_keys.forEach(key -> Store.this.block_states.remove(key));
        checkpoint_state_keys.forEach(key -> Store.this.checkpoint_states.remove(key));
      } finally {
        writeLock.unlock();
      }
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
    public Checkpoint getBestJustifiedCheckpoint() {
      return best_justified_checkpoint.orElseGet(Store.this::getBestJustifiedCheckpoint);
    }

    @Override
    public BeaconBlock getBlock(final Bytes32 blockRoot) {
      return either(blockRoot, blocks::get, Store.this::getBlock);
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

    @Override
    public Checkpoint getLatestMessage(final UnsignedLong validatorIndex) {
      return either(validatorIndex, latest_messages::get, Store.this::getLatestMessage);
    }

    @Override
    public boolean containsLatestMessage(final UnsignedLong validatorIndex) {
      return latest_messages.containsKey(validatorIndex)
          || Store.this.containsLatestMessage(validatorIndex);
    }

    // Disk Storage Related Functions

    public Map<Bytes32, BeaconBlock> getBlocks() {
      return blocks;
    }

    public Map<Bytes32, BeaconState> getBlockStates() {
      return block_states;
    }

    public Map<Checkpoint, BeaconState> getCheckpointStates() {
      return checkpoint_states;
    }

    public Map<UnsignedLong, Checkpoint> getLatestMessages() {
      return latest_messages;
    }
  }
}
