/*
 * Copyright ConsenSys Software Inc., 2023
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
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ThrottlingStorageQueryChannel implements StorageQueryChannel {
  private final StorageQueryChannel delegate;
  private final ThrottlingTaskQueue taskQueue;

  public ThrottlingStorageQueryChannel(
      final StorageQueryChannel delegate,
      final int maximumConcurrentRequests,
      final MetricsSystem metricsSystem) {
    this.delegate = delegate;
    taskQueue =
        ThrottlingTaskQueue.create(
            maximumConcurrentRequests,
            metricsSystem,
            TekuMetricCategory.STORAGE,
            "throttled_query_queue_size");
  }

  @Override
  public SafeFuture<Optional<OnDiskStoreData>> onStoreRequest() {
    return taskQueue.queueTask(delegate::onStoreRequest);
  }

  @Override
  public SafeFuture<WeakSubjectivityState> getWeakSubjectivityState() {
    return taskQueue.queueTask(delegate::getWeakSubjectivityState);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlockSlot() {
    return taskQueue.queueTask(delegate::getEarliestAvailableBlockSlot);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getEarliestAvailableBlock() {
    return taskQueue.queueTask(delegate::getEarliestAvailableBlock);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockAtSlot(UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getFinalizedBlockAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getLatestFinalizedBlockAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(Bytes32 blockRoot) {
    return taskQueue.queueTask(() -> delegate.getBlockByBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> getHotBlockAndStateByBlockRoot(
      Bytes32 blockRoot) {
    return taskQueue.queueTask(() -> delegate.getHotBlockAndStateByBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Optional<StateAndBlockSummary>> getHotStateAndBlockSummaryByBlockRoot(
      Bytes32 blockRoot) {
    return taskQueue.queueTask(() -> delegate.getHotStateAndBlockSummaryByBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Map<Bytes32, SignedBeaconBlock>> getHotBlocksByRoot(Set<Bytes32> blockRoots) {
    return taskQueue.queueTask(() -> delegate.getHotBlocksByRoot(blockRoots));
  }

  @Override
  public SafeFuture<Optional<SlotAndBlockRoot>> getSlotAndBlockRootByStateRoot(Bytes32 stateRoot) {
    return taskQueue.queueTask(() -> delegate.getSlotAndBlockRootByStateRoot(stateRoot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestFinalizedStateAtSlot(UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getLatestFinalizedStateAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestAvailableFinalizedState(UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getLatestAvailableFinalizedState(slot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(Bytes32 blockRoot) {
    return taskQueue.queueTask(() -> delegate.getFinalizedStateByBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFinalizedSlotByStateRoot(Bytes32 stateRoot) {
    return taskQueue.queueTask(() -> delegate.getFinalizedSlotByStateRoot(stateRoot));
  }

  @Override
  public SafeFuture<List<SignedBeaconBlock>> getNonCanonicalBlocksBySlot(UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getNonCanonicalBlocksBySlot(slot));
  }

  @Override
  public SafeFuture<Optional<Checkpoint>> getAnchor() {
    return taskQueue.queueTask(delegate::getAnchor);
  }

  @Override
  public SafeFuture<Optional<DepositTreeSnapshot>> getFinalizedDepositSnapshot() {
    return taskQueue.queueTask(delegate::getFinalizedDepositSnapshot);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlobsSidecarSlot() {
    return taskQueue.queueTask(delegate::getEarliestAvailableBlobsSidecarSlot);
  }

  @Override
  public SafeFuture<Optional<BlobsSidecar>> getBlobsSidecar(SlotAndBlockRoot slotAndBlockRoot) {
    return taskQueue.queueTask(() -> delegate.getBlobsSidecar(slotAndBlockRoot));
  }
}
