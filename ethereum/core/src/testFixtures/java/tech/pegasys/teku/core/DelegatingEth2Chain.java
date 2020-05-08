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

import com.google.common.primitives.UnsignedLong;
import java.util.stream.Stream;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;

public class DelegatingEth2Chain implements Eth2Chain {
  protected final Eth2Chain eth2Chain;

  public DelegatingEth2Chain(final Eth2Chain eth2Chain) {
    this.eth2Chain = eth2Chain;
  }

  @Override
  public UnsignedLong getLatestSlot() {
    return eth2Chain.getLatestSlot();
  }

  @Override
  public void putBlockAndState(final SignedBlockAndState blockAndState) {
    eth2Chain.putBlockAndState(blockAndState);
  }

  @Override
  public void putAll(final Eth2Chain otherChain) {
    eth2Chain.putAll(otherChain);
  }

  @Override
  public boolean isEmpty() {
    return eth2Chain.isEmpty();
  }

  @Override
  public UnsignedLong getLatestEpoch() {
    return eth2Chain.getLatestEpoch();
  }

  @Override
  public Stream<SignedBlockAndState> streamBlocksAndStates() {
    return eth2Chain.streamBlocksAndStates();
  }

  @Override
  public Stream<SignedBlockAndState> streamBlocksAndStates(final long fromSlot, final long toSlot) {
    return eth2Chain.streamBlocksAndStates(fromSlot, toSlot);
  }

  @Override
  public Stream<SignedBlockAndState> streamBlocksAndStates(
      final UnsignedLong fromSlot, final UnsignedLong toSlot) {
    return eth2Chain.streamBlocksAndStates(fromSlot, toSlot);
  }

  @Override
  public Stream<SignedBlockAndState> streamBlocksAndStatesUpTo(final long toSlot) {
    return eth2Chain.streamBlocksAndStatesUpTo(toSlot);
  }

  @Override
  public Stream<SignedBlockAndState> streamBlocksAndStatesUpTo(final UnsignedLong toSlot) {
    return eth2Chain.streamBlocksAndStatesUpTo(toSlot);
  }

  @Override
  public SignedBlockAndState getGenesis() {
    return eth2Chain.getGenesis();
  }

  @Override
  public SignedBlockAndState getLatestBlockAndState() {
    return eth2Chain.getLatestBlockAndState();
  }

  @Override
  public SignedBlockAndState getBlockAndStateAtSlot(final long slot) {
    return eth2Chain.getBlockAndStateAtSlot(slot);
  }

  @Override
  public SignedBlockAndState getBlockAndStateAtSlot(final UnsignedLong slot) {
    return eth2Chain.getBlockAndStateAtSlot(slot);
  }

  @Override
  public SignedBeaconBlock getBlockAtSlot(final long slot) {
    return eth2Chain.getBlockAtSlot(slot);
  }

  @Override
  public SignedBeaconBlock getBlockAtSlot(final UnsignedLong slot) {
    return eth2Chain.getBlockAtSlot(slot);
  }

  @Override
  public BeaconState getStateAtSlot(final long slot) {
    return eth2Chain.getStateAtSlot(slot);
  }

  @Override
  public BeaconState getStateAtSlot(final UnsignedLong slot) {
    return eth2Chain.getStateAtSlot(slot);
  }

  @Override
  public SignedBlockAndState getLatestBlockAndStateAtSlot(final long slot) {
    return eth2Chain.getLatestBlockAndStateAtSlot(slot);
  }

  @Override
  public SignedBlockAndState getLatestBlockAndStateAtSlot(final UnsignedLong slot) {
    return eth2Chain.getLatestBlockAndStateAtSlot(slot);
  }

  @Override
  public SignedBlockAndState getLatestBlockAndStateAtEpochBoundary(final long epoch) {
    return eth2Chain.getLatestBlockAndStateAtEpochBoundary(epoch);
  }

  @Override
  public SignedBlockAndState getLatestBlockAndStateAtEpochBoundary(final UnsignedLong epoch) {
    return eth2Chain.getLatestBlockAndStateAtEpochBoundary(epoch);
  }

  @Override
  public Checkpoint getCurrentCheckpointForEpoch(final long epoch) {
    return eth2Chain.getCurrentCheckpointForEpoch(epoch);
  }

  @Override
  public Checkpoint getCurrentCheckpointForEpoch(final UnsignedLong epoch) {
    return eth2Chain.getCurrentCheckpointForEpoch(epoch);
  }
}
