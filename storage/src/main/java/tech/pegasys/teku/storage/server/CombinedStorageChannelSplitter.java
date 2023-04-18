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

package tech.pegasys.teku.storage.server;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.api.CombinedStorageChannel;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.api.WeakSubjectivityState;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;

/**
 * Splits calls to {@link CombinedStorageChannelSplitter} into the separate {@link
 * StorageUpdateChannel} and {@link StorageQueryChannel} components with updates being handled
 * synchronously and queries being run asynchronously.
 *
 * <p>This guarantees that queries are only ever processed after the updates that were sent before
 * them but without allowing queries to delay updates.
 */
public class CombinedStorageChannelSplitter implements CombinedStorageChannel {
  private final AsyncRunner asyncRunner;
  private final StorageQueryChannel queryDelegate;
  private final StorageUpdateChannel updateDelegate;

  public CombinedStorageChannelSplitter(
      final AsyncRunner asyncRunner,
      final StorageUpdateChannel updateDelegate,
      final StorageQueryChannel queryDelegate) {
    this.asyncRunner = asyncRunner;
    this.queryDelegate = queryDelegate;
    this.updateDelegate = updateDelegate;
  }

  @Override
  public SafeFuture<UpdateResult> onStorageUpdate(final StorageUpdate event) {
    return updateDelegate.onStorageUpdate(event);
  }

  @Override
  public SafeFuture<Void> onFinalizedBlocks(
      final Collection<SignedBeaconBlock> finalizedBlocks,
      final Map<UInt64, BlobsSidecar> blobsSidecarBySlot) {
    return updateDelegate.onFinalizedBlocks(finalizedBlocks, blobsSidecarBySlot);
  }

  @Override
  public SafeFuture<Void> onFinalizedState(
      final BeaconState finalizedState, final Bytes32 blockRoot) {
    return updateDelegate.onFinalizedState(finalizedState, blockRoot);
  }

  @Override
  public SafeFuture<Void> onReconstructedFinalizedState(
      BeaconState finalizedState, Bytes32 blockRoot) {
    return updateDelegate.onReconstructedFinalizedState(finalizedState, blockRoot);
  }

  @Override
  public SafeFuture<Void> onWeakSubjectivityUpdate(
      final WeakSubjectivityUpdate weakSubjectivityUpdate) {
    return updateDelegate.onWeakSubjectivityUpdate(weakSubjectivityUpdate);
  }

  @Override
  public SafeFuture<Void> onFinalizedDepositSnapshot(
      final DepositTreeSnapshot depositTreeSnapshot) {
    return updateDelegate.onFinalizedDepositSnapshot(depositTreeSnapshot);
  }

  @Override
  public void onChainInitialized(final AnchorPoint initialAnchor) {
    updateDelegate.onChainInitialized(initialAnchor);
  }

  @Override
  public SafeFuture<Void> onBlobsSidecar(final BlobsSidecar blobsSidecar) {
    return updateDelegate.onBlobsSidecar(blobsSidecar);
  }

  @Override
  public SafeFuture<Void> onBlobsSidecarRemoval(final SlotAndBlockRoot blobsSidecarKey) {
    return updateDelegate.onBlobsSidecarRemoval(blobsSidecarKey);
  }

  @Override
  public SafeFuture<Optional<OnDiskStoreData>> onStoreRequest() {
    return asyncRunner.runAsync(queryDelegate::onStoreRequest);
  }

  @Override
  public SafeFuture<WeakSubjectivityState> getWeakSubjectivityState() {
    return asyncRunner.runAsync(queryDelegate::getWeakSubjectivityState);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlockSlot() {
    return asyncRunner.runAsync(queryDelegate::getEarliestAvailableBlockSlot);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getEarliestAvailableBlock() {
    return asyncRunner.runAsync(queryDelegate::getEarliestAvailableBlock);
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockAtSlot(final UInt64 slot) {
    return asyncRunner.runAsync(() -> queryDelegate.getFinalizedBlockAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return asyncRunner.runAsync(() -> queryDelegate.getLatestFinalizedBlockAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return asyncRunner.runAsync(() -> queryDelegate.getBlockByBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> getHotBlockAndStateByBlockRoot(
      final Bytes32 blockRoot) {
    return asyncRunner.runAsync(() -> queryDelegate.getHotBlockAndStateByBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Optional<StateAndBlockSummary>> getHotStateAndBlockSummaryByBlockRoot(
      final Bytes32 blockRoot) {
    return asyncRunner.runAsync(
        () -> queryDelegate.getHotStateAndBlockSummaryByBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Map<Bytes32, SignedBeaconBlock>> getHotBlocksByRoot(
      final Set<Bytes32> blockRoots) {
    return asyncRunner.runAsync(() -> queryDelegate.getHotBlocksByRoot(blockRoots));
  }

  @Override
  public SafeFuture<Optional<SlotAndBlockRoot>> getSlotAndBlockRootByStateRoot(
      final Bytes32 stateRoot) {
    return asyncRunner.runAsync(() -> queryDelegate.getSlotAndBlockRootByStateRoot(stateRoot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestFinalizedStateAtSlot(final UInt64 slot) {
    return asyncRunner.runAsync(() -> queryDelegate.getLatestFinalizedStateAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestAvailableFinalizedState(final UInt64 slot) {
    return asyncRunner.runAsync(() -> queryDelegate.getLatestAvailableFinalizedState(slot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(final Bytes32 blockRoot) {
    return asyncRunner.runAsync(() -> queryDelegate.getFinalizedStateByBlockRoot(blockRoot));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFinalizedSlotByStateRoot(final Bytes32 stateRoot) {
    return asyncRunner.runAsync(() -> queryDelegate.getFinalizedSlotByStateRoot(stateRoot));
  }

  @Override
  public SafeFuture<List<SignedBeaconBlock>> getNonCanonicalBlocksBySlot(final UInt64 slot) {
    return asyncRunner.runAsync(() -> queryDelegate.getNonCanonicalBlocksBySlot(slot));
  }

  @Override
  public SafeFuture<Optional<Checkpoint>> getAnchor() {
    return asyncRunner.runAsync(queryDelegate::getAnchor);
  }

  @Override
  public SafeFuture<Optional<DepositTreeSnapshot>> getFinalizedDepositSnapshot() {
    return asyncRunner.runAsync(queryDelegate::getFinalizedDepositSnapshot);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableBlobSidecarSlot() {
    return asyncRunner.runAsync(queryDelegate::getEarliestAvailableBlobSidecarSlot);
  }

  @Override
  public SafeFuture<Optional<BlobSidecar>> getBlobSidecar(final SlotAndBlockRootAndBlobIndex key) {
    return asyncRunner.runAsync(() -> queryDelegate.getBlobSidecar(key));
  }

  @Override
  public SafeFuture<List<SlotAndBlockRootAndBlobIndex>> getBlobSidecarKeys(
      UInt64 startSlot, UInt64 endSlot, UInt64 limit) {
    return asyncRunner.runAsync(() -> queryDelegate.getBlobSidecarKeys(startSlot, endSlot, limit));
  }

  @Override
  public SafeFuture<Optional<BlobsSidecar>> getBlobsSidecar(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return asyncRunner.runAsync(() -> queryDelegate.getBlobsSidecar(slotAndBlockRoot));
  }
}
