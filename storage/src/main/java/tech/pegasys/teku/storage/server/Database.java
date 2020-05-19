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
import java.util.NavigableMap;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.Eth1BlockData;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.MerkleTree;
import tech.pegasys.teku.storage.Store;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.events.StorageUpdateResult;

public interface Database extends AutoCloseable {

  void storeGenesis(Store store);

  StorageUpdateResult update(StorageUpdate event);

  /**
   * Add an eth1 deposit to the merkle tree and the pending deposits list
   *
   * @param depositWithIndex the deposit to add
   * @return
   */
  StorageUpdateResult addEth1Deposit(final DepositWithIndex depositWithIndex);

  /**
   * Add block information about eth1 deposit, indexed by timestamp.
   *
   * @param timestamp
   * @param eth1BlockData
   * @return
   */
  StorageUpdateResult addEth1BlockData(
      final UnsignedLong timestamp, final Eth1BlockData eth1BlockData);

  /**
   * purge the pending deposits list up to the specified index
   *
   * @param eth1DepositIndex the highest index to purge
   * @return
   */
  StorageUpdateResult pruneEth1Deposits(UnsignedLong eth1DepositIndex);

  MerkleTree getMerkleTree();

  NavigableMap<UnsignedLong, DepositWithIndex> getDepositNavigableMap();

  Optional<Store> createMemoryStore();

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

  Optional<BeaconState> getState(Bytes32 root);
}
