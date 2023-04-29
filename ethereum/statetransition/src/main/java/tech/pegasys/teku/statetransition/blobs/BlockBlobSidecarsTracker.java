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

package tech.pegasys.teku.statetransition.blobs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public class BlockBlobSidecarsTracker {
  private static final Logger LOG = LogManager.getLogger();

  private final SlotAndBlockRoot slotAndBlockRoot;
  private final UInt64 maxBlobsPerBlock;

  private final AtomicReference<Optional<BeaconBlockBodyDeneb>> blockBody =
      new AtomicReference<>(Optional.empty());

  private final NavigableMap<UInt64, BlobSidecar> blobSidecars = new ConcurrentSkipListMap<>();
  private final SafeFuture<Void> blobSidecarsComplete = new SafeFuture<>();

  private boolean fetchTriggered = false;

  public BlockBlobSidecarsTracker(
      final SlotAndBlockRoot slotAndBlockRoot, final UInt64 maxBlobsPerBlock) {
    this.slotAndBlockRoot = slotAndBlockRoot;
    this.maxBlobsPerBlock = maxBlobsPerBlock;
  }

  public SortedMap<UInt64, BlobSidecar> getBlobSidecars() {
    return Collections.unmodifiableSortedMap(blobSidecars);
  }

  public SafeFuture<Void> getCompletionFuture() {
    return blobSidecarsComplete;
  }

  public Optional<BeaconBlockBodyDeneb> getBlockBody() {
    return blockBody.get();
  }

  public boolean containsBlobSidecar(final BlobIdentifier blobIdentifier) {
    return Optional.ofNullable(blobSidecars.get(blobIdentifier.getIndex()))
        .map(blobSidecar -> blobSidecar.getBlockRoot().equals(blobIdentifier.getBlockRoot()))
        .orElse(false);
  }

  public Stream<BlobIdentifier> getMissingBlobSidecars() {
    final Optional<BeaconBlockBodyDeneb> body = blockBody.get();
    if (body.isPresent()) {
      return UInt64.range(UInt64.ZERO, UInt64.valueOf(body.get().getBlobKzgCommitments().size()))
          .filter(blobIndex -> !blobSidecars.containsKey(blobIndex))
          .map(blobIndex -> new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), blobIndex));
    }

    if (blobSidecars.isEmpty()) {
      return Stream.of();
    }

    // We may return maxBlobsPerBlock because we don't know the block
    return UInt64.range(UInt64.ZERO, maxBlobsPerBlock)
        .filter(blobIndex -> !blobSidecars.containsKey(blobIndex))
        .map(blobIndex -> new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), blobIndex));
  }

  public Stream<BlobIdentifier> getUnusedBlobSidecarsForBlock() {
    final Optional<BeaconBlockBodyDeneb> body = blockBody.get();
    checkState(body.isPresent(), "Block must me known to call this method");

    final UInt64 firstUnusedIndex = maxBlobsPerBlock.min(body.get().getBlobKzgCommitments().size());
    return UInt64.range(firstUnusedIndex, maxBlobsPerBlock)
        .map(blobIndex -> new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), blobIndex));
  }

  public boolean add(final BlobSidecar blobSidecar) {
    checkArgument(
        blobSidecar.getBlockRoot().equals(slotAndBlockRoot.getBlockRoot()),
        "Wrong blobSidecar block root");

    if (blobSidecarsComplete.isDone()) {
      // already completed
      return false;
    }

    boolean addedNew = blobSidecars.put(blobSidecar.getIndex(), blobSidecar) == null;

    if (addedNew) {
      checkCompletion();
    } else {
      LOG.warn(
          "Multiple BlobSidecars with index {} for {} detected.",
          slotAndBlockRoot.toLogString(),
          blobSidecar.getIndex());
    }

    return addedNew;
  }

  public int blobSidecarsCount() {
    return blobSidecars.size();
  }

  public boolean setBlock(final SignedBeaconBlock block) {
    checkArgument(block.getSlotAndBlockRoot().equals(slotAndBlockRoot), "Wrong block");
    final Optional<BeaconBlockBodyDeneb> oldBlock =
        blockBody.getAndSet(
            Optional.of(BeaconBlockBodyDeneb.required(block.getMessage().getBody())));
    if (oldBlock.isPresent()) {
      return false;
    }

    checkCompletion();

    return true;
  }

  public SlotAndBlockRoot getSlotAndBlockRoot() {
    return slotAndBlockRoot;
  }

  private void checkCompletion() {
    if (blobSidecarsComplete.isDone()) {
      return;
    }
    final boolean complete = areBlobsComplete();
    if (complete) {
      blobSidecarsComplete.complete(null);
    }
  }

  public boolean isCompleted() {
    return blobSidecarsComplete.isDone();
  }

  public boolean isFetchTriggered() {
    return fetchTriggered;
  }

  public void setFetchTriggered() {
    this.fetchTriggered = true;
  }

  private boolean areBlobsComplete() {
    return blockBody
        .get()
        .map(b -> blobSidecars.size() >= b.getBlobKzgCommitments().size())
        .orElse(false);
  }
}
