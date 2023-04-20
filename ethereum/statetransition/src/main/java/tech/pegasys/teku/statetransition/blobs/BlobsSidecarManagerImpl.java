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
import static tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker.ALREADY_CHECKED;

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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceBlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.statetransition.validation.BlobSidecarValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobsSidecarManagerImpl implements BlobsSidecarManager, SlotEventsChannel {
  private static final int MAX_CACHED_VALIDATED_BLOBS_SIDECARS_PER_SLOT = 10;
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final BlobSidecarValidator validator;

  private final StorageQueryChannel storageQueryChannel;
  private final StorageUpdateChannel storageUpdateChannel;

  private final NavigableMap<UInt64, Map<Bytes32, BlobsSidecar>> validatedPendingBlobs =
      new ConcurrentSkipListMap<>();

  private final Subscribers<ImportedBlobSidecarListener> importedBlobSidecarSubscribers =
      Subscribers.create(true);

  public BlobsSidecarManagerImpl(
      final Spec spec,
      final RecentChainData recentChainData,
      final BlobSidecarValidator validator,
      final StorageQueryChannel storageQueryChannel,
      final StorageUpdateChannel storageUpdateChannel) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.validator = validator;
    this.storageUpdateChannel = storageUpdateChannel;
    this.storageQueryChannel = storageQueryChannel;
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<InternalValidationResult> validateAndImportBlobSidecar(
      final SignedBlobSidecar signedBlobSidecar) {

    final SafeFuture<InternalValidationResult> validationResult =
        validator.validate(signedBlobSidecar);

    validationResult.thenAccept(
        result -> {
          if (result.code().equals(ValidationResultCode.ACCEPT)
              || result.code().equals(ValidationResultCode.SAVE_FOR_FUTURE)) {
            final BlobSidecar blobSidecar = signedBlobSidecar.getBlobSidecar();
            importBlobSidecar(blobSidecar)
                .finish(err -> LOG.error("Failed to process received BlobSidecar.", err));
          }
        });

    return validationResult;
  }

  @Override
  @SuppressWarnings("unused")
  public SafeFuture<Void> importBlobSidecar(final BlobSidecar blobSidecar) {
    // TODO implement import
    return SafeFuture.COMPLETE.thenRun(
        () -> importedBlobSidecarSubscribers.forEach(s -> s.onBlobSidecarImported(blobSidecar)));
  }

  @Override
  public void subscribeToImportedBlobSidecars(
      final ImportedBlobSidecarListener importedBlobSidecarListener) {
    importedBlobSidecarSubscribers.subscribe(importedBlobSidecarListener);
  }

  @Override
  public boolean isAvailabilityRequiredAtSlot(final UInt64 slot) {
    return spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(recentChainData.getStore(), slot);
  }

  @Override
  public void storeUnconfirmedValidatedBlobsSidecar(final BlobsSidecar blobsSidecar) {
    // cache already validated blobs
    validatedPendingBlobs
        .computeIfAbsent(blobsSidecar.getBeaconBlockSlot(), __ -> createNewMap())
        .put(blobsSidecar.getBeaconBlockRoot(), blobsSidecar);

    internalStoreUnconfirmedBlobsSidecar(blobsSidecar);
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
  public void storeUnconfirmedBlobsSidecar(final BlobsSidecar blobsSidecar) {
    internalStoreUnconfirmedBlobsSidecar(blobsSidecar);
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
  public BlobsSidecarAvailabilityChecker createAvailabilityChecker(final SignedBeaconBlock block) {
    // Block is pre-Deneb, BlobsSidecar is not supported yet
    if (block.getMessage().getBody().toVersionDeneb().isEmpty()) {
      return BlobsSidecarAvailabilityChecker.NOT_REQUIRED;
    }

    final Optional<BlobsSidecar> maybeValidatedBlobs =
        Optional.ofNullable(
            validatedPendingBlobs.getOrDefault(block.getSlot(), emptyMap()).get(block.getRoot()));

    return maybeValidatedBlobs
        .filter(
            checkedBlobsSidecar -> checkedBlobsSidecar.getBeaconBlockRoot().equals(block.getRoot()))
        .map(ALREADY_CHECKED)
        .or(() -> handleEmptyBlockCommitmentsChecker(block))
        .orElse(
            new ForkChoiceBlobsSidecarAvailabilityChecker(
                spec.atSlot(block.getSlot()),
                recentChainData,
                block,
                storageQueryChannel::getBlobsSidecar));
  }

  @Override
  public void onSlot(final UInt64 slot) {
    validatedPendingBlobs.headMap(slot.minusMinZero(1)).clear();
  }

  private void internalStoreUnconfirmedBlobsSidecar(final BlobsSidecar blobsSidecar) {
    storageUpdateChannel
        .onBlobsSidecar(blobsSidecar)
        .thenRun(
            () ->
                LOG.debug(
                    "Unconfirmed BlobsSidecar stored for {}",
                    () ->
                        new SlotAndBlockRoot(
                            blobsSidecar.getBeaconBlockSlot(), blobsSidecar.getBeaconBlockRoot())))
        .ifExceptionGetsHereRaiseABug();
  }

  private Map<Bytes32, BlobsSidecar> createNewMap() {
    return LimitedMap.createSynchronized(MAX_CACHED_VALIDATED_BLOBS_SIDECARS_PER_SLOT);
  }

  private Optional<BlobsSidecarAvailabilityChecker> handleEmptyBlockCommitmentsChecker(
      final SignedBeaconBlock block) {
    if (BeaconBlockBodyDeneb.required(block.getBeaconBlock().orElseThrow().getBody())
        .getBlobKzgCommitments()
        .isEmpty()) {
      return Optional.of(BlobsSidecarAvailabilityChecker.NOT_REQUIRED);
    }
    return Optional.empty();
  }

  @VisibleForTesting
  Map<Bytes32, BlobsSidecar> getValidatedPendingBlobsForSlot(final UInt64 slot) {

    return Optional.ofNullable(validatedPendingBlobs.get(slot))
        .map(Collections::unmodifiableMap)
        .orElse(emptyMap());
  }
}
