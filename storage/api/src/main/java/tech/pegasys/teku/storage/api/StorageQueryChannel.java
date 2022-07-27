/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface StorageQueryChannel extends ChannelInterface {

  SafeFuture<Optional<OnDiskStoreData>> onStoreRequest();

  SafeFuture<WeakSubjectivityState> getWeakSubjectivityState();

  /** @return The earliest available finalized block's slot */
  SafeFuture<Optional<UInt64>> getEarliestAvailableBlockSlot();

  /** @return The earliest available finalized block */
  SafeFuture<Optional<SignedBeaconBlock>> getEarliestAvailableBlock();

  SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockAtSlot(final UInt64 slot);

  SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(final UInt64 slot);

  SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockRoot);

  SafeFuture<Optional<SignedBlockAndState>> getHotBlockAndStateByBlockRoot(final Bytes32 blockRoot);

  SafeFuture<Optional<StateAndBlockSummary>> getHotStateAndBlockSummaryByBlockRoot(
      final Bytes32 blockRoot);

  /**
   * Returns "hot" blocks - the latest finalized block or blocks that descend from the latest
   * finalized block
   *
   * @param blockRoots The roots of blocks to look up
   * @return A map from root too block of any found blocks
   */
  SafeFuture<Map<Bytes32, SignedBeaconBlock>> getHotBlocksByRoot(final Set<Bytes32> blockRoots);

  SafeFuture<Optional<SlotAndBlockRoot>> getSlotAndBlockRootByStateRoot(final Bytes32 stateRoot);

  SafeFuture<Optional<BeaconState>> getLatestFinalizedStateAtSlot(final UInt64 slot);

  SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(final Bytes32 blockRoot);

  SafeFuture<Optional<UInt64>> getFinalizedSlotByStateRoot(final Bytes32 stateRoot);

  SafeFuture<List<SignedBeaconBlock>> getNonCanonicalBlocksBySlot(final UInt64 slot);

  SafeFuture<Optional<Checkpoint>> getAnchor();
}
