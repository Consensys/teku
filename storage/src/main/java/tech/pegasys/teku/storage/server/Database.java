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

package tech.pegasys.teku.storage.server;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.store.UpdatableStore;

public interface Database extends AutoCloseable {

  void storeGenesis(UpdatableStore store);

  void update(StorageUpdate event);

  Optional<UpdatableStore> createMemoryStore();

  /**
   * Return the root of the finalized block at this slot if such a block exists.
   *
   * @param slot The slot to query
   * @return Returns the root of the finalized block proposed at this slot, if such a block exists
   */
  Optional<Bytes32> getFinalizedRootAtSlot(UnsignedLong slot);

  /**
   * Returns the latest finalized root at or prior to the given slot
   *
   * @param slot The slot to query
   * @return Returns the root of the latest finalized block proposed at or prior to the given slot
   */
  Optional<Bytes32> getLatestFinalizedRootAtSlot(final UnsignedLong slot);

  Optional<SignedBeaconBlock> getSignedBlock(Bytes32 root);

  /**
   * Given a block root, returns the corresponding finalized state, if this state is available.
   *
   * @param root A block root.
   * @return The finalized state corresponding to the given block root.
   */
  Optional<BeaconState> getFinalizedState(Bytes32 root);

  Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock();

  Stream<DepositsFromBlockEvent> streamDepositsFromBlocks();

  void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event);

  void addDepositsFromBlockEvent(final DepositsFromBlockEvent event);
}
