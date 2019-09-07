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

import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.HashMap;
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
import tech.pegasys.artemis.datastructures.state.Checkpoint;

public class Store implements ReadOnlyStore {

  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private UnsignedLong time;
  private Checkpoint justified_checkpoint;
  private Checkpoint finalized_checkpoint;
  private Map<Bytes32, BeaconBlock> blocks;
  private Map<Bytes32, BeaconState> block_states;
  private Map<Checkpoint, BeaconState> checkpoint_states;
  private Map<UnsignedLong, LatestMessage> latest_messages;

  public Store(
      UnsignedLong time,
      Checkpoint justified_checkpoint,
      Checkpoint finalized_checkpoint,
      Map<Bytes32, BeaconBlock> blocks,
      Map<Bytes32, BeaconState> block_states,
      Map<Checkpoint, BeaconState> checkpoint_states) {
    this.time = time;
    this.justified_checkpoint = justified_checkpoint;
    this.finalized_checkpoint = finalized_checkpoint;
    this.blocks = blocks;
    this.block_states = block_states;
    this.checkpoint_states = checkpoint_states;
    this.latest_messages = new ConcurrentHashMap<>();
  }

  public Transaction startTransaction() {
    return new Transaction();
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
  public Checkpoint getJustified_checkpoint() {
    readLock.lock();
    try {
      return justified_checkpoint;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Checkpoint getFinalized_checkpoint() {
    readLock.lock();
    try {
      return finalized_checkpoint;
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
  public LatestMessage getLatestMessage(UnsignedLong validatorIndex) {
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
    private Optional<Checkpoint> justified_checkpoint = Optional.empty();
    private Optional<Checkpoint> finalized_checkpoint = Optional.empty();
    private Map<Bytes32, BeaconBlock> blocks = new HashMap<>();
    private Map<Bytes32, BeaconState> block_states = new HashMap<>();
    private Map<Checkpoint, BeaconState> checkpoint_states = new HashMap<>();
    private Map<UnsignedLong, LatestMessage> latest_messages = new HashMap<>();

    public void putLatestMessage(UnsignedLong validatorIndex, LatestMessage latestMessage) {
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

    public void setJustified_checkpoint(Checkpoint justified_checkpoint) {
      this.justified_checkpoint = Optional.of(justified_checkpoint);
    }

    public void setFinalized_checkpoint(Checkpoint finalized_checkpoint) {
      this.finalized_checkpoint = Optional.of(finalized_checkpoint);
    }

    public void commit() {
      final Lock writeLock = Store.this.lock.writeLock();
      writeLock.lock();
      try {
        time.ifPresent(value -> Store.this.time = value);
        justified_checkpoint.ifPresent(value -> Store.this.justified_checkpoint = value);
        finalized_checkpoint.ifPresent(value -> Store.this.finalized_checkpoint = value);
        Store.this.blocks.putAll(blocks);
        Store.this.block_states.putAll(block_states);
        Store.this.checkpoint_states.putAll(checkpoint_states);
        Store.this.latest_messages.putAll(latest_messages);
      } finally {
        writeLock.unlock();
      }
    }

    @Override
    public UnsignedLong getTime() {
      return time.orElseGet(Store.this::getTime);
    }

    @Override
    public Checkpoint getJustified_checkpoint() {
      return justified_checkpoint.orElseGet(Store.this::getJustified_checkpoint);
    }

    @Override
    public Checkpoint getFinalized_checkpoint() {
      return finalized_checkpoint.orElseGet(Store.this::getFinalized_checkpoint);
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
    public LatestMessage getLatestMessage(final UnsignedLong validatorIndex) {
      return either(validatorIndex, latest_messages::get, Store.this::getLatestMessage);
    }

    @Override
    public boolean containsLatestMessage(final UnsignedLong validatorIndex) {
      return latest_messages.containsKey(validatorIndex)
          || Store.this.containsLatestMessage(validatorIndex);
    }
  }
}
