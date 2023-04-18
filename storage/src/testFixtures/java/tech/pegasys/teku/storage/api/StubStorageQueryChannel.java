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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;

public class StubStorageQueryChannel implements StorageQueryChannel {

  @Override
  public SafeFuture<Optional<OnDiskStoreData>> onStoreRequest() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<WeakSubjectivityState> getWeakSubjectivityState() {
    return SafeFuture.completedFuture(WeakSubjectivityState.empty());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlockSlot() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getEarliestAvailableBlock() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockAtSlot(UInt64 slot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(UInt64 slot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(Bytes32 blockRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> getHotBlockAndStateByBlockRoot(
      final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<StateAndBlockSummary>> getHotStateAndBlockSummaryByBlockRoot(
      final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Map<Bytes32, SignedBeaconBlock>> getHotBlocksByRoot(
      final Set<Bytes32> blockRoots) {
    return SafeFuture.completedFuture(Collections.emptyMap());
  }

  @Override
  public SafeFuture<Optional<SlotAndBlockRoot>> getSlotAndBlockRootByStateRoot(
      final Bytes32 stateRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestFinalizedStateAtSlot(UInt64 slot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestAvailableFinalizedState(UInt64 slot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(Bytes32 blockRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFinalizedSlotByStateRoot(final Bytes32 stateRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<List<SignedBeaconBlock>> getNonCanonicalBlocksBySlot(final UInt64 slot) {
    return SafeFuture.completedFuture(new ArrayList<>());
  }

  @Override
  public SafeFuture<Optional<Checkpoint>> getAnchor() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<DepositTreeSnapshot>> getFinalizedDepositSnapshot() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlobSidecarSlot() {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<Optional<BlobSidecar>> getBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
    return SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(
      final UInt64 startSlot, final UInt64 endSlot, final UInt64 limit) {
    return SafeFuture.completedFuture(List.of());
  }

  @Override
  public SafeFuture<Optional<BlobsSidecar>> getBlobsSidecar(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return SafeFuture.completedFuture(Optional.empty());
  }
}
