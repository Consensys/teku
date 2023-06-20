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

import com.google.common.base.MoreObjects;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
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
  private static final UInt64 CREATION_TIMING_IDX = UInt64.MAX_VALUE;
  private static final UInt64 BLOCK_ARRIVAL_TIMING_IDX = CREATION_TIMING_IDX.decrement();
  private static final UInt64 FETCH_TIMING_IDX = BLOCK_ARRIVAL_TIMING_IDX.decrement();

  private final SlotAndBlockRoot slotAndBlockRoot;
  private final UInt64 maxBlobsPerBlock;

  private final AtomicReference<Optional<BeaconBlockBodyDeneb>> blockBody =
      new AtomicReference<>(Optional.empty());

  private final NavigableMap<UInt64, BlobSidecar> blobSidecars = new ConcurrentSkipListMap<>();
  private final SafeFuture<Void> blobSidecarsComplete = new SafeFuture<>();

  private volatile boolean fetchTriggered = false;

  private final Optional<Map<UInt64, Long>> maybeDebugTimings;

  public BlockBlobSidecarsTracker(
      final SlotAndBlockRoot slotAndBlockRoot, final UInt64 maxBlobsPerBlock) {
    this.slotAndBlockRoot = slotAndBlockRoot;
    this.maxBlobsPerBlock = maxBlobsPerBlock;
    if (LOG.isDebugEnabled()) {
      // don't need a concurrent hashmap since we'll interact with it from synchronized BlobSidecar
      // pool methods
      final Map<UInt64, Long> debugTimings = new HashMap<>();
      debugTimings.put(CREATION_TIMING_IDX, System.currentTimeMillis());
      this.maybeDebugTimings = Optional.of(debugTimings);
    } else {
      this.maybeDebugTimings = Optional.empty();
    }
  }

  public SortedMap<UInt64, BlobSidecar> getBlobSidecars() {
    return Collections.unmodifiableSortedMap(blobSidecars);
  }

  public SafeFuture<Void> getCompletionFuture() {
    final SafeFuture<Void> newCompletionFuture = new SafeFuture<>();
    blobSidecarsComplete.propagateTo(newCompletionFuture);
    return newCompletionFuture;
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

    final UInt64 firstUnusedIndex = UInt64.valueOf(body.get().getBlobKzgCommitments().size());
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
      LOG.debug("New BlobSidecar {}", blobSidecar::toLogString);
      maybeDebugTimings.ifPresent(
          debugTimings -> debugTimings.put(blobSidecar.getIndex(), System.currentTimeMillis()));
      checkCompletion();
    } else {
      LOG.warn(
          "Multiple BlobSidecars with index {} for {} detected.",
          blobSidecar.getIndex(),
          slotAndBlockRoot.toLogString());
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

    LOG.debug("Block received for {}", slotAndBlockRoot::toLogString);
    maybeDebugTimings.ifPresent(
        debugTimings -> debugTimings.put(BLOCK_ARRIVAL_TIMING_IDX, System.currentTimeMillis()));

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
    if (areBlobsComplete()) {
      LOG.debug("BlobSidecars for {} are completed", slotAndBlockRoot::toLogString);
      blobSidecarsComplete.complete(null);
      maybeDebugTimings.ifPresent(this::printDebugTimings);
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
    maybeDebugTimings.ifPresent(
        debugTimings -> debugTimings.put(FETCH_TIMING_IDX, System.currentTimeMillis()));
  }

  private boolean areBlobsComplete() {
    return blockBody
        .get()
        .map(b -> blobSidecars.size() >= b.getBlobKzgCommitments().size())
        .orElse(false);
  }

  /**
   * prints a debug line when tracker completes
   *
   * @param debugTimings
   */
  private void printDebugTimings(final Map<UInt64, Long> debugTimings) {
    final long completionTime = System.currentTimeMillis();
    final long creationTime = debugTimings.getOrDefault(CREATION_TIMING_IDX, 0L);
    final StringBuilder timingsReport = new StringBuilder(128);

    timingsReport
        .append("Tracker for ")
        .append(slotAndBlockRoot.toLogString())
        .append(" created at ")
        .append(creationTime)
        .append(" - ");

    timingsReport.append("Completion time ").append(completionTime - creationTime).append("ms - ");

    blobSidecars
        .keySet()
        .forEach(
            blobIndex ->
                timingsReport
                    .append("Sidecar [")
                    .append(blobIndex)
                    .append("] delay ")
                    .append(debugTimings.getOrDefault(blobIndex, 0L) - creationTime)
                    .append("ms - "));
    timingsReport
        .append("Block delay ")
        .append(debugTimings.getOrDefault(BLOCK_ARRIVAL_TIMING_IDX, 0L) - creationTime)
        .append("ms - ");

    if (debugTimings.containsKey(FETCH_TIMING_IDX)) {
      timingsReport
          .append("Fetch delay ")
          .append(debugTimings.get(FETCH_TIMING_IDX) - creationTime)
          .append("ms");
    } else {
      timingsReport.append("Fetch wasn't required");
    }

    LOG.debug(timingsReport.toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slotAndBlockRoot", slotAndBlockRoot)
        .add("isBlockBodyPresent", blockBody.get().isPresent())
        .add("isCompleted", isCompleted())
        .add("fetchTriggered", fetchTriggered)
        .add(
            "blobSidecars",
            blobSidecars.entrySet().stream()
                .map(
                    entry ->
                        MoreObjects.toStringHelper("")
                            .add("index", entry.getKey())
                            .add("blobSidecar", entry.getValue().toLogString()))
                .collect(Collectors.toUnmodifiableList()))
        .toString();
  }
}
