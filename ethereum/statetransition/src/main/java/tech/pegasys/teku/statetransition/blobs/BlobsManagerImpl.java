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

package tech.pegasys.teku.statetransition.blobs;

import static tech.pegasys.teku.spec.config.Constants.MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.logic.versions.eip4844.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceBlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class BlobsManagerImpl
    implements BlobsManager, FinalizedCheckpointChannel, SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private static final int UNCONFIRMED_PRUNING_LIMIT = 64;
  private static final int CONFIRMED_PRUNING_LIMIT = 96;

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final StorageQueryChannel storageQueryChannel;
  private final StorageUpdateChannel storageUpdateChannel;

  public BlobsManagerImpl(
      final Spec spec,
      final RecentChainData recentChainData,
      final StorageQueryChannel storageQueryChannel,
      final StorageUpdateChannel storageUpdateChannel) {

    this.spec = spec;
    this.recentChainData = recentChainData;
    this.storageUpdateChannel = storageUpdateChannel;
    this.storageQueryChannel = storageQueryChannel;
  }

  @Override
  public SafeFuture<Void> storeUnconfirmedBlobs(BlobsSidecar blobsSidecar) {
    // async IO
    storageUpdateChannel
        .onBlobsSidecar(blobsSidecar)
        .thenRun(() -> LOG.debug("Unconfirmed BlobsSidecar stored {}", blobsSidecar))
        .ifExceptionGetsHereRaiseABug();
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Optional<BlobsSidecar>> getBlobsByBlockRoot(final SignedBeaconBlock block) {
    return storageQueryChannel.getBlobsSidecar(
        new SlotAndBlockRoot(block.getSlot(), block.getRoot()));
  }

  @Override
  public SafeFuture<Void> discardBlobsByBlock(final SignedBeaconBlock block) {
    // async IO
    final SlotAndBlockRoot blobsAtSlotAndBlockRoot =
        new SlotAndBlockRoot(block.getSlot(), block.getRoot());
    storageUpdateChannel
        .onBlobsSidecarRemoval(blobsAtSlotAndBlockRoot)
        .thenRun(() -> LOG.debug("BlobsSidecar discarded for {}", blobsAtSlotAndBlockRoot))
        .ifExceptionGetsHereRaiseABug();
    return SafeFuture.COMPLETE;
  }

  @Override
  public void onBlockImport(StoreTransaction transaction) {
    // enable blobs confirmation when storing hot blocks on DB
    transaction.setConfirmHotBlocksBlobs(true);
  }

  @Override
  public BlobsSidecarAvailabilityChecker createAvailabilityChecker(final SignedBeaconBlock block) {
    return new ForkChoiceBlobsSidecarAvailabilityChecker(
        spec.atSlot(block.getSlot()), recentChainData, block, this);
  }

  @Override
  public void onNewFinalizedCheckpoint(Checkpoint checkpoint, boolean fromOptimisticBlock) {
    if (fromOptimisticBlock) {
      final UInt64 pruneUpToSlot = checkpoint.getEpochStartSlot(spec);
      storageUpdateChannel
          .onUnconfirmedBlobsSidecarPruning(pruneUpToSlot, UNCONFIRMED_PRUNING_LIMIT)
          .thenRun(
              () ->
                  LOG.debug(
                      "Unconfirmed BlobsSidecars discarded up to slot {} (limit {})",
                      pruneUpToSlot,
                      UNCONFIRMED_PRUNING_LIMIT))
          .ifExceptionGetsHereRaiseABug();
    }
  }

  @Override
  public void onSlot(UInt64 slot) {
    final int slotsPerEpoch = spec.getSlotsPerEpoch(slot);
    if (slot.mod(0).equals(UInt64.ZERO) || slot.mod(slotsPerEpoch - 1).equals(UInt64.ZERO)) {
      // avoid last and first slot of an epoch
      return;
    }

    UInt64 oldestPrunableSlot =
        slot.minusMinZero((long) MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS * slotsPerEpoch);
    if (oldestPrunableSlot.isGreaterThan(UInt64.ZERO)) {
      storageUpdateChannel
          .onBlobsSidecarPruning(oldestPrunableSlot, CONFIRMED_PRUNING_LIMIT)
          .thenRun(
              () ->
                  LOG.debug(
                      "BlobsSidecars discarded up to slot {} (limit {})",
                      oldestPrunableSlot,
                      CONFIRMED_PRUNING_LIMIT))
          .ifExceptionGetsHereRaiseABug();
    }
  }
}
