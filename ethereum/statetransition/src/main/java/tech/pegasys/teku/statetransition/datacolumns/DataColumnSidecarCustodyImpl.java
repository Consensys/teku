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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

public class DataColumnSidecarCustodyImpl
    implements UpdatableDataColumnSidecarCustody, SlotEventsChannel, FinalizedCheckpointChannel {

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
      return AsyncStream.create(getIncompleteColumns().iterator());
    }

    @SuppressWarnings("UnusedMethod")
    public boolean isComplete() {
      return canonicalBlockRoot().isPresent() && !isIncomplete();
    }

    public boolean isIncomplete() {
      return !getIncompleteColumns().isEmpty();
    }
  }

  // for how long the custody will wait for a missing column to be gossiped
  private final int gossipWaitSlots = 2;

  private final Spec spec;
  private final DataColumnSidecarDbAccessor db;
  private final CanonicalBlockResolver blockResolver;
  private final UInt256 nodeId;
  private final int totalCustodySubnetCount;
  private final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator;

  private volatile UInt64 currentSlot = null;

  public DataColumnSidecarCustodyImpl(
      Spec spec,
      CanonicalBlockResolver blockResolver,
      DataColumnSidecarDbAccessor db,
      MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator,
      UInt256 nodeId,
      int totalCustodySubnetCount) {
    checkNotNull(spec);
    checkNotNull(blockResolver);
    checkNotNull(minCustodyPeriodSlotCalculator);
    checkNotNull(db);
    checkNotNull(nodeId);

    this.spec = spec;
    this.db = db;
    this.blockResolver = blockResolver;
    this.minCustodyPeriodSlotCalculator = minCustodyPeriodSlotCalculator;
    this.nodeId = nodeId;
    this.totalCustodySubnetCount = totalCustodySubnetCount;
  }

  private List<UInt64> getCustodyColumnsForSlot(UInt64 slot) {
    return getCustodyColumnsForEpoch(spec.computeEpochAtSlot(slot));
  }

  private List<UInt64> getCustodyColumnsForEpoch(UInt64 epoch) {
    return MiscHelpersEip7594.required(spec.atEpoch(epoch).miscHelpers())
        .computeCustodyColumnIndexes(nodeId, totalCustodySubnetCount);
  }

  @Override
  public SafeFuture<Void> onNewValidatedDataColumnSidecar(DataColumnSidecar dataColumnSidecar) {
    if (isMyCustody(dataColumnSidecar.getSlot(), dataColumnSidecar.getIndex())) {
      return db.addSidecar(dataColumnSidecar);
    } else {
      return SafeFuture.COMPLETE;
    }
  }

  private boolean isMyCustody(UInt64 slot, UInt64 columnIndex) {
    UInt64 epoch = spec.computeEpochAtSlot(slot);
    return spec.atEpoch(epoch)
        .miscHelpers()
        .toVersionEip7594()
        .map(
            miscHelpersEip7594 ->
                miscHelpersEip7594
                    .computeCustodyColumnIndexes(nodeId, totalCustodySubnetCount)
                    .contains(columnIndex))
        .orElse(false);
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      DataColumnSlotAndIdentifier columnId) {
    return db.getSidecar(columnId);
  }

  @Override
  public void onSlot(UInt64 slot) {
    currentSlot = slot;
  }

  @Override
  public void onNewFinalizedCheckpoint(Checkpoint checkpoint, boolean fromOptimisticBlock) {
    advanceFirstIncompleteSlot(checkpoint.getEpoch()).ifExceptionGetsHereRaiseABug();
  }

  private SafeFuture<Void> advanceFirstIncompleteSlot(UInt64 finalizedEpoch) {
    UInt64 firstNonFinalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch.increment());
    return retrievePotentiallyIncompleteSlotCustodies(firstNonFinalizedSlot)
        .takeUntil(SlotCustody::isIncomplete, true)
        .findLast()
        .thenCompose(
            maybeFirstIncompleteOrLastComplete ->
                maybeFirstIncompleteOrLastComplete
                    .map(
                        firstIncompleteOrLastComplete ->
                            db.setFirstCustodyIncompleteSlot(firstIncompleteOrLastComplete.slot()))
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
              return AsyncStream.create(slotStream).mapAsync(this::retrieveSlotCustody);
            });
  }

  private SafeFuture<SlotCustody> retrieveSlotCustody(final UInt64 slot) {
    final SafeFuture<Optional<Bytes32>> maybeCanonicalBlockRoot = getBlockRootIfHaveBlobs(slot);
    final List<UInt64> requiredColumns = getCustodyColumnsForSlot(slot);
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

  private SafeFuture<Optional<Bytes32>> getBlockRootIfHaveBlobs(UInt64 slot) {
    return blockResolver
        .getBlockAtSlot(slot)
        .thenApply(
            maybeBlock ->
                maybeBlock
                    .filter(
                        block ->
                            block
                                    .getBeaconBlock()
                                    .flatMap(b -> b.getBody().toVersionEip7594())
                                    .map(b -> b.getBlobKzgCommitments().size())
                                    .orElse(0)
                                > 0)
                    .map(BeaconBlock::getRoot));
  }

  @Override
  public AsyncStream<DataColumnSlotAndIdentifier> retrieveMissingColumns() {
    // waiting a column for [gossipWaitSlots] to be delivered by gossip
    // and not considering it missing yet
    return retrievePotentiallyIncompleteSlotCustodies(currentSlot.minusMinZero(gossipWaitSlots))
        .flatMap(SlotCustody::streamIncompleteColumns);
  }
}
