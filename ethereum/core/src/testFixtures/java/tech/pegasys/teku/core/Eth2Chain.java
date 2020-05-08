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

/** Represents a valid chain (no forks - a single, linear chain) of blocks and states */
public interface Eth2Chain {

  void putBlockAndState(SignedBlockAndState blockAndState);

  void putAll(Eth2Chain otherChain);

  boolean isEmpty();

  UnsignedLong getLatestSlot();

  UnsignedLong getLatestEpoch();

  Stream<SignedBlockAndState> streamBlocksAndStates();

  Stream<SignedBlockAndState> streamBlocksAndStates(long fromSlot, long toSlot);

  Stream<SignedBlockAndState> streamBlocksAndStates(UnsignedLong fromSlot, UnsignedLong toSlot);

  Stream<SignedBlockAndState> streamBlocksAndStatesUpTo(long toSlot);

  Stream<SignedBlockAndState> streamBlocksAndStatesUpTo(UnsignedLong toSlot);

  SignedBlockAndState getGenesis();

  SignedBlockAndState getLatestBlockAndState();

  SignedBlockAndState getBlockAndStateAtSlot(long slot);

  SignedBlockAndState getBlockAndStateAtSlot(UnsignedLong slot);

  SignedBeaconBlock getBlockAtSlot(long slot);

  SignedBeaconBlock getBlockAtSlot(UnsignedLong slot);

  BeaconState getStateAtSlot(long slot);

  BeaconState getStateAtSlot(UnsignedLong slot);

  SignedBlockAndState getLatestBlockAndStateAtSlot(long slot);

  SignedBlockAndState getLatestBlockAndStateAtSlot(UnsignedLong slot);

  SignedBlockAndState getLatestBlockAndStateAtEpochBoundary(long epoch);

  SignedBlockAndState getLatestBlockAndStateAtEpochBoundary(UnsignedLong epoch);

  Checkpoint getCurrentCheckpointForEpoch(long epoch);

  Checkpoint getCurrentCheckpointForEpoch(UnsignedLong epoch);
}
