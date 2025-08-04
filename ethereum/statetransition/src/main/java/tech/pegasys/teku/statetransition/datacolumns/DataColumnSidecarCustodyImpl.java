/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

public class DataColumnSidecarCustodyImpl
    implements DataColumnSidecarCustody, SlotEventsChannel, FinalizedCheckpointChannel {

  private static final Logger LOG = LogManager.getLogger();

  private record SlotCustody(
      UInt64 slot,
      Optional<Bytes32> canonicalBlockRoot,
      Collection<UInt64> requiredColumnIndices,
      Collection<DataColumnSlotAndIdentifier> custodiedColumnIndices) {
    public Collection<DataColumnSlotAndIdentifier> getIncompleteColumns() {
      return canonicalBlockRoot
          .map(
              blockRoot -> {
                Set<UInt64> collectedIndices =
                    custodiedColumnIndices.stream()
                        .filter(identifier -> identifier.blockRoot().equals(blockRoot))
                        .map(DataColumnSlotAndIdentifier::columnIndex)
                        .collect(Collectors.toSet());
                return requiredColumnIndices.stream()
                    .filter(requiredColIdx -> !collectedIndices.contains(requiredColIdx))
                    .map(
                        missedColIdx ->
                            new DataColumnSlotAndIdentifier(slot(), blockRoot, missedColIdx));
              })
          .orElse(Stream.empty())
          .toList();
    }

    public AsyncStream<DataColumnSlotAndIdentifier> streamIncompleteColumns() {
      return AsyncStream.createUnsafe(getIncompleteColumns().iterator());
    }

    @SuppressWarnings("UnusedMethod")
    public boolean isComplete() {
      return canonicalBlockRoot().isPresent() && !isIncomplete();
    }

    public boolean isIncomplete() {
      return !getIncompleteColumns().isEmpty();
    }
  }

  // How long the custody will wait for a missing column to be gossiped
  private static final int GOSSIP_WAIT_SLOTS = 2;

  private final Spec spec;
  private final DataColumnSidecarDbAccessor db;
  private final CanonicalBlockResolver blockResolver;
  private final AtomicInteger totalCustodyGroupCount;
  private final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator;
  private final CustodyGroupCountManager custodyGroupCountManager;

  private volatile UInt64 currentSlot = null;
  private UInt64 lastEpoch = UInt64.MAX_VALUE;

  public DataColumnSidecarCustodyImpl(
      final Spec spec,
      final CanonicalBlockResolver blockResolver,
      final DataColumnSidecarDbAccessor db,
      final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator,
      final CustodyGroupCountManager custodyGroupCountManager,
      final int totalCustodyGroupCount) {
    checkNotNull(spec);
    checkNotNull(blockResolver);
    checkNotNull(minCustodyPeriodSlotCalculator);
    checkNotNull(db);

    this.spec = spec;
    this.db = db;
    this.blockResolver = blockResolver;
    this.minCustodyPeriodSlotCalculator = minCustodyPeriodSlotCalculator;
    this.custodyGroupCountManager = custodyGroupCountManager;
    this.totalCustodyGroupCount = new AtomicInteger(totalCustodyGroupCount);
    LOG.debug(
        "Initialized DataColumnSidecar Custody with custody group count {}",
        totalCustodyGroupCount);
  }

  @Override
  public SafeFuture<Void> onNewValidatedDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar) {
    if (isMyCustody(dataColumnSidecar.getIndex())) {
      return db.addSidecar(dataColumnSidecar);
    } else {
      return SafeFuture.COMPLETE;
    }
  }

  private boolean isMyCustody(final UInt64 columnIndex) {
    return custodyGroupCountManager.getCustodyColumnIndices().contains(columnIndex);
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId) {
    return db.getSidecar(columnId);
  }

  @Override
  public SafeFuture<Boolean> hasCustodyDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId) {
    return db.getColumnIdentifiers(new SlotAndBlockRoot(columnId.slot(), columnId.blockRoot()))
        .thenApply(ids -> ids.contains(columnId));
  }

  @Override
  public void onSlot(final UInt64 slot) {
    currentSlot = slot;
    if (!updateEpoch(spec.computeEpochAtSlot(slot))) {
      return;
    }
    final int newCustodyGroupCount = custodyGroupCountManager.getCustodyGroupCount();
    final int oldCustodyGroupCount = totalCustodyGroupCount.getAndSet(newCustodyGroupCount);
    if (newCustodyGroupCount == oldCustodyGroupCount) {
      return;
    }
    LOG.debug(
        "Custody group count changed from {} to {}", oldCustodyGroupCount, newCustodyGroupCount);
    if (newCustodyGroupCount > oldCustodyGroupCount) {
      final UInt64 minCustodyPeriodSlot =
          minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(currentSlot);
      db.setFirstCustodyIncompleteSlot(minCustodyPeriodSlot)
          .finish(
              error ->
                  LOG.error(
                      "Unexpected error while updating first custody incomplete slot with a new value: {}.",
                      minCustodyPeriodSlot,
                      error));
    }
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    advanceFirstIncompleteSlot(checkpoint.getEpoch())
        .finish(
            error ->
                LOG.error(
                    "Unexpected error while advancing first custody incomplete slot for checkpoint {}{}",
                    checkpoint,
                    fromOptimisticBlock ? " (from optimistic block)" : "",
                    error));
  }

  @VisibleForTesting
  public int getTotalCustodyGroupCount() {
    return totalCustodyGroupCount.get();
  }

  private synchronized boolean updateEpoch(final UInt64 epoch) {
    if (!lastEpoch.equals(epoch)) {
      lastEpoch = epoch;
      return true;
    }
    return false;
  }

  private SafeFuture<Void> advanceFirstIncompleteSlot(final UInt64 finalizedEpoch) {
    final UInt64 firstNonFinalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch.increment());
    return retrievePotentiallyIncompleteSlotCustodies(firstNonFinalizedSlot)
        .takeUntil(SlotCustody::isIncomplete, true)
        .findLast()
        .thenCompose(
            maybeFirstIncompleteOrLastComplete ->
                maybeFirstIncompleteOrLastComplete
                    .map(
                        firstIncompleteOrLastComplete -> {
                          if (firstIncompleteOrLastComplete.slot().equals(firstNonFinalizedSlot)) {
                            LOG.debug(
                                "Custody group count synced to {}", totalCustodyGroupCount.get());
                            custodyGroupCountManager.setCustodyGroupSyncedCount(
                                totalCustodyGroupCount.get());
                          }
                          return db.setFirstCustodyIncompleteSlot(
                              firstIncompleteOrLastComplete.slot());
                        })
                    .orElse(SafeFuture.COMPLETE));
  }

  private AsyncStream<SlotCustody> retrievePotentiallyIncompleteSlotCustodies(
      final UInt64 toSlotIncluded) {
    return AsyncStream.create(db.getFirstCustodyIncompleteSlot())
        .flatMap(
            maybeFirstIncompleteSlot -> {
              final UInt64 firstIncompleteSlot =
                  maybeFirstIncompleteSlot.orElseGet(
                      () -> minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(currentSlot));
              Stream<UInt64> slotStream =
                  Stream.iterate(
                      firstIncompleteSlot,
                      slot -> slot.isLessThanOrEqualTo(toSlotIncluded),
                      UInt64::increment);
              return AsyncStream.createUnsafe(slotStream.iterator())
                  .mapAsync(this::retrieveSlotCustody);
            });
  }

  private SafeFuture<SlotCustody> retrieveSlotCustody(final UInt64 slot) {
    if (!spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      return SafeFuture.completedFuture(
          new SlotCustody(
              slot, Optional.empty(), Collections.emptyList(), Collections.emptyList()));
    }
    final SafeFuture<Optional<Bytes32>> maybeCanonicalBlockRoot = getBlockRootWithBlobs(slot);
    final List<UInt64> requiredColumns = custodyGroupCountManager.getCustodyColumnIndices();
    final SafeFuture<List<DataColumnSlotAndIdentifier>> existingColumns =
        db.getColumnIdentifiers(slot);
    return SafeFuture.allOf(maybeCanonicalBlockRoot, existingColumns)
        .thenApply(
            __ ->
                new SlotCustody(
                    slot,
                    maybeCanonicalBlockRoot.getImmediately(),
                    requiredColumns,
                    existingColumns.getImmediately()));
  }

  /**
   * Get the block root for a slot that has at least one blob.
   *
   * @param slot The slot to get the block root for.
   * @return The block root if it has at least one blob, otherwise nothing.
   */
  private SafeFuture<Optional<Bytes32>> getBlockRootWithBlobs(final UInt64 slot) {
    return blockResolver
        .getBlockAtSlot(slot)
        .thenApply(
            maybeBlock ->
                maybeBlock
                    .filter(
                        block ->
                            block
                                .getBeaconBlock()
                                .flatMap(b -> b.getBody().toVersionDeneb())
                                .map(BeaconBlockBodyDeneb::getBlobKzgCommitments)
                                .map(commitments -> !commitments.isEmpty())
                                .orElse(false))
                    .map(BeaconBlock::getRoot));
  }

  @Override
  public AsyncStream<DataColumnSlotAndIdentifier> retrieveMissingColumns() {
    // Wait GOSSIP_WAIT_SLOTS for the column to be delivered by gossip before considering it missing
    return retrievePotentiallyIncompleteSlotCustodies(currentSlot.minusMinZero(GOSSIP_WAIT_SLOTS))
        .flatMap(SlotCustody::streamIncompleteColumns);
  }
}
