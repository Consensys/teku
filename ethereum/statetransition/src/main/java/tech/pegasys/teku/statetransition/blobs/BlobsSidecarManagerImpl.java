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
import static tech.pegasys.teku.spec.logic.versions.eip4844.blobs.BlobsSidecarAvailabilityChecker.ALREADY_CHECKED;

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
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.BeaconBlockBodyEip4844;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.logic.versions.eip4844.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceBlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobsSidecarManagerImpl implements BlobsSidecarManager, SlotEventsChannel {
  private static final int MAX_CACHED_VALIDATED_BLOBS_PER_SLOT = 10;
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final StorageQueryChannel storageQueryChannel;
  private final StorageUpdateChannel storageUpdateChannel;

  private final NavigableMap<UInt64, Map<Bytes32, BlobsSidecar>> validatedPendingBlobs =
      new ConcurrentSkipListMap<>();

  public BlobsSidecarManagerImpl(
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
  public void storeUnconfirmedValidatedBlobs(final BlobsSidecar blobsSidecar) {
    // cache already validated blobs
    validatedPendingBlobs
        .computeIfAbsent(blobsSidecar.getBeaconBlockSlot(), __ -> createNewMap())
        .put(blobsSidecar.getBeaconBlockRoot(), blobsSidecar);

    internalStoreUnconfirmedBlobs(blobsSidecar);
  }

  @Override
  public void storeUnconfirmedBlobs(final BlobsSidecar blobsSidecar) {
    // remove blobs from the already validated cache
    Optional.ofNullable(validatedPendingBlobs.get(blobsSidecar.getBeaconBlockSlot()))
        .ifPresent(map -> map.remove(blobsSidecar.getBeaconBlockRoot()));

    internalStoreUnconfirmedBlobs(blobsSidecar);
  }

  @Override
  public void discardBlobsByBlock(final SignedBeaconBlock block) {
    // note: we could discard blobs sending them to the BlobsPruner which will gradually delete them
    // from BD without
    // queue the deletion in the storageUpdateChannel which can cause slowdown if many blobs are
    // discarded in sequence
    // (various pending blocks on a fork that turns out to be invalid)

    // having unconfirmed affects how we handle BlobsSidecarByRange rpc method
    // for non finalized blobs, we should have a fast way to check if corresponding blocks are in
    // recentChainData (should be cached in ram)
    // for finalized we should always lookup on the unconfirmed column to filter them out (even in
    // the case we have only one blob per slot, it might be still unconfirmed (a proposed invalid
    // block))

    // for the unconfirmed column I found an interesting approach that could make the
    // confirmed-unconfirmed flag more
    // efficient to store and update: https://groups.google.com/g/leveldb/c/qOMWvp7gyxg but would
    // require some changes in our kvstore layer

    // async IO
    final SlotAndBlockRoot blobsAtSlotAndBlockRoot =
        new SlotAndBlockRoot(block.getSlot(), block.getRoot());

    storageUpdateChannel
        .onBlobsSidecarRemoval(blobsAtSlotAndBlockRoot)
        .thenRun(() -> LOG.debug("BlobsSidecar discarded for {}", blobsAtSlotAndBlockRoot))
        .ifExceptionGetsHereRaiseABug();
  }

  @Override
  public BlobsSidecarAvailabilityChecker createAvailabilityChecker(final SignedBeaconBlock block) {
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
    validatedPendingBlobs.headMap(slot.decrement()).clear();
  }

  private void internalStoreUnconfirmedBlobs(final BlobsSidecar blobsSidecar) {
    // async IO
    storageUpdateChannel
        .onBlobsSidecar(blobsSidecar)
        .thenRun(() -> LOG.debug("Unconfirmed BlobsSidecar stored {}", blobsSidecar))
        .ifExceptionGetsHereRaiseABug();
  }

  private Map<Bytes32, BlobsSidecar> createNewMap() {
    return LimitedMap.createSynchronized(MAX_CACHED_VALIDATED_BLOBS_PER_SLOT);
  }

  private Optional<BlobsSidecarAvailabilityChecker> handleEmptyBlockCommitmentsChecker(
      final SignedBeaconBlock block) {
    if (BeaconBlockBodyEip4844.required(block.getBeaconBlock().orElseThrow().getBody())
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
