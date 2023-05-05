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

import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAvailabilityChecker;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceBlobSidecarsAvailabilityChecker;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.validation.BlobSidecarValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobSidecarManagerImpl implements BlobSidecarManager, SlotEventsChannel {

  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final BlobSidecarValidator validator;
  private final FutureItems<SignedBlobSidecar> futureBlobSidecars;
  private final Map<Bytes32, InternalValidationResult> invalidBlobSidecarRoots;
  private final BlobSidecarPool blobSidecarPool;

  @SuppressWarnings("unused")
  private final StorageQueryChannel storageQueryChannel;

  private final Subscribers<ReceivedBlobSidecarListener> receivedBlobSidecarSubscribers =
      Subscribers.create(true);

  public BlobSidecarManagerImpl(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final BlobSidecarPool blobSidecarPool,
      final BlobSidecarValidator validator,
      final FutureItems<SignedBlobSidecar> futureBlobSidecars,
      final Map<Bytes32, InternalValidationResult> invalidBlobSidecarRoots,
      final StorageQueryChannel storageQueryChannel) {
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.validator = validator;
    this.blobSidecarPool = blobSidecarPool;
    this.futureBlobSidecars = futureBlobSidecars;
    this.invalidBlobSidecarRoots = invalidBlobSidecarRoots;
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
              prepareForBlockImport(blobSidecar);
              break;
          }
        });

    return validationResult;
  }

  @Override
  public void prepareForBlockImport(final BlobSidecar blobSidecar) {
    blobSidecarPool.onNewBlobSidecar(blobSidecar);
    receivedBlobSidecarSubscribers.forEach(s -> s.onBlobSidecarReceived(blobSidecar));
  }

  @Override
  public void subscribeToReceivedBlobSidecar(
      final ReceivedBlobSidecarListener receivedBlobSidecarListener) {
    receivedBlobSidecarSubscribers.subscribe(receivedBlobSidecarListener);
  }

  @Override
  public boolean isAvailabilityRequiredAtSlot(final UInt64 slot) {
    return spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(recentChainData.getStore(), slot);
  }

  @Override
  public BlobSidecarsAvailabilityChecker createAvailabilityChecker(final SignedBeaconBlock block) {
    // Block is pre-Deneb, BlobsSidecar is not supported yet
    if (block.getMessage().getBody().toVersionDeneb().isEmpty()) {
      return BlobSidecarsAvailabilityChecker.NOT_REQUIRED;
    }

    final BlockBlobSidecarsTracker blockBlobsSidecarsTracker =
        blobSidecarPool.getOrCreateBlockBlobSidecarsTracker(block);

    return new ForkChoiceBlobSidecarsAvailabilityChecker(
        spec, asyncRunner, recentChainData, blockBlobsSidecarsTracker);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    blobSidecarPool.onSlot(slot);

    futureBlobSidecars.onSlot(slot);
    futureBlobSidecars
        .prune(slot)
        .forEach(
            blobSidecar ->
                validateAndPrepareForBlockImport(blobSidecar).ifExceptionGetsHereRaiseABug());
  }
}
