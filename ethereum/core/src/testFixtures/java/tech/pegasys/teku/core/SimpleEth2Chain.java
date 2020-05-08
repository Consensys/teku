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

package tech.pegasys.teku.core;

import static org.assertj.core.util.Preconditions.checkArgument;
import static org.assertj.core.util.Preconditions.checkState;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.util.config.Constants;

public class SimpleEth2Chain implements Eth2Chain {
  private final NavigableMap<UnsignedLong, SignedBlockAndState> blocks = new TreeMap<>();

  @Override
  public UnsignedLong getLatestSlot() {
    assertChainIsNotEmpty();
    return getLatestBlockAndState().getBlock().getSlot();
  }

  @Override
  public void putBlockAndState(final SignedBlockAndState blockAndState) {
    assertBlockCanBeAppended(blockAndState);
    blocks.put(blockAndState.getSlot(), blockAndState);
  }

  @Override
  public void putAll(final Eth2Chain otherChain) {
    otherChain.streamBlocksAndStates().forEach(this::putBlockAndState);
  }

  @Override
  public boolean isEmpty() {
    return blocks.isEmpty();
  }

  @Override
  public UnsignedLong getLatestEpoch() {
    assertChainIsNotEmpty();
    final UnsignedLong slot = getLatestSlot();
    return compute_epoch_at_slot(slot);
  }

  @Override
  public Stream<SignedBlockAndState> streamBlocksAndStates() {
    return blocks.values().stream();
  }

  @Override
  public Stream<SignedBlockAndState> streamBlocksAndStates(final long fromSlot, final long toSlot) {
    return streamBlocksAndStates(UnsignedLong.valueOf(fromSlot), UnsignedLong.valueOf(toSlot));
  }

  @Override
  public Stream<SignedBlockAndState> streamBlocksAndStates(
      final UnsignedLong fromSlot, final UnsignedLong toSlot) {
    return blocks.values().stream()
        .filter(b -> b.getBlock().getSlot().compareTo(fromSlot) >= 0)
        .filter(b -> b.getBlock().getSlot().compareTo(toSlot) <= 0);
  }

  @Override
  public Stream<SignedBlockAndState> streamBlocksAndStatesUpTo(final long toSlot) {
    return streamBlocksAndStatesUpTo(UnsignedLong.valueOf(toSlot));
  }

  @Override
  public Stream<SignedBlockAndState> streamBlocksAndStatesUpTo(final UnsignedLong toSlot) {
    return blocks.values().stream().filter(b -> b.getBlock().getSlot().compareTo(toSlot) <= 0);
  }

  @Override
  public SignedBlockAndState getGenesis() {
    return Optional.ofNullable(blocks.firstEntry()).map(Entry::getValue).orElse(null);
  }

  @Override
  public SignedBlockAndState getLatestBlockAndState() {
    return Optional.ofNullable(blocks.lastEntry()).map(Entry::getValue).orElse(null);
  }

  @Override
  public SignedBlockAndState getBlockAndStateAtSlot(final long slot) {
    return getBlockAndStateAtSlot(UnsignedLong.valueOf(slot));
  }

  @Override
  public SignedBlockAndState getBlockAndStateAtSlot(final UnsignedLong slot) {
    return Optional.ofNullable(blocks.get(slot)).orElse(null);
  }

  @Override
  public SignedBeaconBlock getBlockAtSlot(final long slot) {
    return getBlockAtSlot(UnsignedLong.valueOf(slot));
  }

  @Override
  public SignedBeaconBlock getBlockAtSlot(final UnsignedLong slot) {
    return resultToBlock(getBlockAndStateAtSlot(slot));
  }

  @Override
  public BeaconState getStateAtSlot(final long slot) {
    return getStateAtSlot(UnsignedLong.valueOf(slot));
  }

  @Override
  public BeaconState getStateAtSlot(final UnsignedLong slot) {
    return resultToState(getBlockAndStateAtSlot(slot));
  }

  @Override
  public SignedBlockAndState getLatestBlockAndStateAtSlot(final long slot) {
    return getLatestBlockAndStateAtSlot(UnsignedLong.valueOf(slot));
  }

  @Override
  public SignedBlockAndState getLatestBlockAndStateAtSlot(final UnsignedLong slot) {
    return Optional.ofNullable(blocks.floorEntry(slot)).map(Entry::getValue).orElse(null);
  }

  @Override
  public SignedBlockAndState getLatestBlockAndStateAtEpochBoundary(final long epoch) {
    return getLatestBlockAndStateAtEpochBoundary(UnsignedLong.valueOf(epoch));
  }

  @Override
  public SignedBlockAndState getLatestBlockAndStateAtEpochBoundary(final UnsignedLong epoch) {
    assertChainIsNotEmpty();
    final UnsignedLong slot = compute_start_slot_at_epoch(epoch);
    return getLatestBlockAndStateAtSlot(slot);
  }

  @Override
  public Checkpoint getCurrentCheckpointForEpoch(final long epoch) {
    return getCurrentCheckpointForEpoch(UnsignedLong.valueOf(epoch));
  }

  @Override
  public Checkpoint getCurrentCheckpointForEpoch(final UnsignedLong epoch) {
    assertChainIsNotEmpty();
    final SignedBeaconBlock block = getLatestBlockAndStateAtEpochBoundary(epoch).getBlock();
    return new Checkpoint(epoch, block.getMessage().hash_tree_root());
  }

  private void assertChainIsNotEmpty() {
    checkState(!blocks.isEmpty(), "Unable to execute operation on empty chain");
  }

  private void assertBlockCanBeAppended(final SignedBlockAndState blockAndState) {
    final Optional<SignedBlockAndState> maybeLastBlock =
        Optional.ofNullable(blocks.lastEntry()).map(Entry::getValue);
    if (maybeLastBlock.isEmpty()) {
      checkArgument(
          blockAndState.getSlot().equals(UnsignedLong.valueOf(Constants.GENESIS_SLOT)),
          "Genesis is not set, cannot append block at slot %s.",
          blockAndState.getSlot());
    } else {
      final SignedBlockAndState lastBlock = maybeLastBlock.get();
      checkArgument(
          lastBlock.getSlot().compareTo(blockAndState.getSlot()) < 0,
          "Cannot append block at slot prior to latest block.");
      checkArgument(
          lastBlock.getRoot().compareTo(blockAndState.getParentRoot()) == 0,
          "Cannot append block that does not descend from the latest block.");
    }
  }

  private BeaconState resultToState(final SignedBlockAndState result) {
    return Optional.ofNullable(result).map(SignedBlockAndState::getState).orElse(null);
  }

  private SignedBeaconBlock resultToBlock(final SignedBlockAndState result) {
    return Optional.ofNullable(result).map(SignedBlockAndState::getBlock).orElse(null);
  }
}
