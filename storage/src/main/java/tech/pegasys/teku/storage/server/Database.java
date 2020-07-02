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
import com.google.errorprone.annotations.MustBeClosed;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.UpdatableStore;

public interface Database extends AutoCloseable {

  void storeGenesis(UpdatableStore store);

  void update(StorageUpdate event);

  Optional<StoreBuilder> createMemoryStore();

  Optional<UnsignedLong> getSlotForFinalizedBlockRoot(Bytes32 blockRoot);

  /**
   * Return the finalized block at this slot if such a block exists.
   *
   * @param slot The slot to query
   * @return Returns the finalized block proposed at this slot, if such a block exists
   */
  Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(UnsignedLong slot);

  /**
   * Returns the latest finalized block at or prior to the given slot
   *
   * @param slot The slot to query
   * @return Returns the latest finalized block proposed at or prior to the given slot
   */
  Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(UnsignedLong slot);

  Optional<SignedBeaconBlock> getSignedBlock(Bytes32 root);

  /**
   * Returns latest finalized block or any known blocks that descend from the latest finalized block
   *
   * @param blockRoots The roots of blocks to look up
   * @return A map from root too block of any found blocks
   */
  Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots);

  /**
   * Return a {@link Stream} of blocks beginning at startSlot and ending at endSlot, both inclusive.
   *
   * @param startSlot the slot of the first block to return
   * @param endSlot the slot of the last block to return
   * @return a Stream of blocks in the range startSlot to endSlot (both inclusive).
   */
  @MustBeClosed
  Stream<SignedBeaconBlock> streamFinalizedBlocks(UnsignedLong startSlot, UnsignedLong endSlot);

  Optional<BeaconState> getLatestAvailableFinalizedState(UnsignedLong maxSlot);

  Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock();

  @MustBeClosed
  Stream<DepositsFromBlockEvent> streamDepositsFromBlocks();

  Optional<ProtoArraySnapshot> getProtoArraySnapshot();

  void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event);

  void addDepositsFromBlockEvent(final DepositsFromBlockEvent event);

  void putProtoArraySnapshot(final ProtoArraySnapshot protoArray);
}
