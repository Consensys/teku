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

import static java.util.Collections.emptyMap;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAvailabilityChecker;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceBlobSidecarsAvailabilityChecker;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.validation.BlobSidecarValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobSidecarManagerImpl implements BlobSidecarManager, SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final BlobSidecarValidator validator;
  private final FutureItems<SignedBlobSidecar> futureBlobSidecars;
  private final Map<Bytes32, InternalValidationResult> invalidBlobSidecarRoots;
  private final BlobSidecarPool blobSidecarPool;

  @SuppressWarnings("unused")
  private final StorageQueryChannel storageQueryChannel;

  private final StorageUpdateChannel storageUpdateChannel;

  private final NavigableMap<UInt64, Map<Bytes32, BlobsSidecar>> validatedPendingBlobs =
      new ConcurrentSkipListMap<>();

  private final Subscribers<ImportedBlobSidecarListener> preparedBlobSidecarSubscribers =
      Subscribers.create(true);

  public BlobSidecarManagerImpl(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final BlobSidecarPool blobSidecarPool,
      final BlobSidecarValidator validator,
      final FutureItems<SignedBlobSidecar> futureBlobSidecars,
      final Map<Bytes32, InternalValidationResult> invalidBlobSidecarRoots,
      final StorageQueryChannel storageQueryChannel,
      final StorageUpdateChannel storageUpdateChannel) {
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.validator = validator;
    this.blobSidecarPool = blobSidecarPool;
    this.futureBlobSidecars = futureBlobSidecars;
    this.invalidBlobSidecarRoots = invalidBlobSidecarRoots;
    this.storageUpdateChannel = storageUpdateChannel;
    this.storageQueryChannel = storageQueryChannel;
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<InternalValidationResult> validateAndPrepareForBlockImport(
      final SignedBlobSidecar signedBlobSidecar) {

    final Optional<InternalValidationResult> maybeInvalid =
        Optional.ofNullable(
            invalidBlobSidecarRoots.get(signedBlobSidecar.getBlobSidecar().hashTreeRoot()));
    if (maybeInvalid.isPresent()) {
      return SafeFuture.completedFuture(maybeInvalid.get());
    }

    final SafeFuture<InternalValidationResult> validationResult =
        validator.validate(signedBlobSidecar);

    validationResult.thenAccept(
        result -> {
          switch (result.code()) {
            case IGNORE:
              // do nothing
              break;
            case REJECT:
              invalidBlobSidecarRoots.put(
                  signedBlobSidecar.getBlobSidecar().hashTreeRoot(), result);
              break;
            case SAVE_FOR_FUTURE:
              futureBlobSidecars.add(signedBlobSidecar);
              break;
            case ACCEPT:
              final BlobSidecar blobSidecar = signedBlobSidecar.getBlobSidecar();
              prepareForBlockImport(blobSidecar)
                  .finish(err -> LOG.error("Failed to process received BlobSidecar.", err));
              break;
          }
        });

    return validationResult;
  }

  @Override
  @SuppressWarnings("unused")
  public SafeFuture<Void> prepareForBlockImport(final BlobSidecar blobSidecar) {
    blobSidecarPool.onNewBlobSidecar(blobSidecar);
    preparedBlobSidecarSubscribers.forEach(s -> s.onBlobSidecarImported(blobSidecar));
    return SafeFuture.COMPLETE;
  }

  @Override
  public void subscribeToPreparedBlobSidecars(
      final ImportedBlobSidecarListener importedBlobSidecarListener) {
    preparedBlobSidecarSubscribers.subscribe(importedBlobSidecarListener);
  }

  @Override
  public boolean isAvailabilityRequiredAtSlot(final UInt64 slot) {
    return spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(recentChainData.getStore(), slot);
  }

  @Override
  public void storeNoBlobsSlot(final SlotAndBlockRoot slotAndBlockRoot) {
    storageUpdateChannel
        .onNoBlobsSlot(slotAndBlockRoot)
        .thenRun(() -> LOG.debug("Slot {} with no BlobSidecars stored", slotAndBlockRoot))
        .ifExceptionGetsHereRaiseABug();
  }

  @Override
  public void storeBlobSidecar(final BlobSidecar blobSidecar) {
    storageUpdateChannel
        .onBlobSidecar(blobSidecar)
        .thenRun(
            () ->
                LOG.debug(
                    "BlobSidecar stored for {}",
                    () ->
                        new SlotAndBlockRootAndBlobIndex(
                            blobSidecar.getSlot(),
                            blobSidecar.getBlockRoot(),
                            blobSidecar.getIndex())))
        .ifExceptionGetsHereRaiseABug();
  }

  @Override
  public void discardBlobSidecarsByBlock(final SignedBeaconBlock block) {
    storageUpdateChannel
        .onBlobSidecarsRemoval(block.getSlot())
        .thenRun(
            () ->
                LOG.debug(
                    () ->
                        String.format(
                            "BlobsSidecar discarded for %s",
                            new SlotAndBlockRoot(block.getSlot(), block.getRoot()))))
        .ifExceptionGetsHereRaiseABug();
  }

  @Override
  public BlobSidecarsAvailabilityChecker createAvailabilityChecker(final SignedBeaconBlock block) {
    // Block is pre-Deneb, BlobsSidecar is not supported yet
    if (block.getMessage().getBody().toVersionDeneb().isEmpty()) {
      return BlobSidecarsAvailabilityChecker.NOT_REQUIRED;
    }

    final BlockBlobSidecarsTracker blockBlobsSidecarsTracker =
        blobSidecarPool.getOrCreateBlockBlobsSidecarsTracker(block);

    return new ForkChoiceBlobSidecarsAvailabilityChecker(
        spec, asyncRunner, recentChainData, blockBlobsSidecarsTracker);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    validatedPendingBlobs.headMap(slot.minusMinZero(1)).clear();

    blobSidecarPool.onSlot(slot);

    futureBlobSidecars.onSlot(slot);
    futureBlobSidecars
        .prune(slot)
        .forEach(
            blobSidecar ->
                validateAndPrepareForBlockImport(blobSidecar).ifExceptionGetsHereRaiseABug());
  }

  @VisibleForTesting
  Map<Bytes32, BlobsSidecar> getValidatedPendingBlobsForSlot(final UInt64 slot) {

    return Optional.ofNullable(validatedPendingBlobs.get(slot))
        .map(Collections::unmodifiableMap)
        .orElse(emptyMap());
  }
}
