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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

public class DataColumnSidecarCustodyImpl
    implements UpdatableDataColumnSidecarCustody, SlotEventsChannel, FinalizedCheckpointChannel {

  public interface CanonicalBlockResolver {

    /**
     * Should return the canonical block root at slot if: - a block exist at this slot - block
     * contains any blobs
     */
    Optional<BeaconBlock> getBlockAtSlot(UInt64 slot);
  }

  private record SlotCustody(
      UInt64 slot,
      Optional<Bytes32> canonicalBlockRoot,
      Collection<UInt64> requiredColumnIndices,
      Collection<DataColumnIdentifier> custodiedColumnIndices) {
    public Collection<DataColumnIdentifier> getIncompleteColumns() {
      return canonicalBlockRoot
          .map(
              blockRoot -> {
                Set<UInt64> collectedIndices =
                    custodiedColumnIndices.stream()
                        .filter(identifier -> identifier.getBlockRoot().equals(blockRoot))
                        .map(DataColumnIdentifier::getIndex)
                        .collect(Collectors.toSet());
                return requiredColumnIndices.stream()
                    .filter(requiredColIdx -> !collectedIndices.contains(requiredColIdx))
                    .map(missedColIdx -> new DataColumnIdentifier(blockRoot, missedColIdx));
              })
          .orElse(Stream.empty())
          .toList();
    }

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
  private final DataColumnSidecarDB db;
  private final CanonicalBlockResolver blockResolver;
  private final UInt256 nodeId;
  private final int totalCustodySubnetCount;

  private final UInt64 eip7594StartEpoch;
  private final Duration requestTimeout;

  private UInt64 currentSlot = null;

  @VisibleForTesting
  Map<DataColumnIdentifier, List<SafeFuture<DataColumnSidecar>>> pendingRequests = new HashMap<>();

  public DataColumnSidecarCustodyImpl(
      Spec spec,
      CanonicalBlockResolver blockResolver,
      DataColumnSidecarDB db,
      UInt256 nodeId,
      int totalCustodySubnetCount,
      Duration requestTimeout) {

    checkNotNull(spec);
    checkNotNull(blockResolver);
    checkNotNull(db);
    checkNotNull(nodeId);

    this.spec = spec;
    this.db = db;
    this.blockResolver = blockResolver;
    this.nodeId = nodeId;
    this.totalCustodySubnetCount = totalCustodySubnetCount;
    this.eip7594StartEpoch = spec.getForkSchedule().getFork(SpecMilestone.EIP7594).getEpoch();
    this.requestTimeout = requestTimeout;
  }

  private UInt64 getEarliestCustodySlot(UInt64 currentSlot) {
    UInt64 epoch = getEarliestCustodyEpoch(spec.computeEpochAtSlot(currentSlot));
    return spec.computeStartSlotAtEpoch(epoch);
  }

  private UInt64 getEarliestCustodyEpoch(UInt64 currentEpoch) {
    int custodyPeriod =
        spec.getSpecConfig(currentEpoch)
            .toVersionEip7594()
            .orElseThrow()
            .getMinEpochsForDataColumnSidecarsRequests();
    return currentEpoch.minusMinZero(custodyPeriod).max(eip7594StartEpoch);
  }

  private List<UInt64> getCustodyColumnsForSlot(UInt64 slot) {
    return getCustodyColumnsForEpoch(spec.computeEpochAtSlot(slot));
  }

  private List<UInt64> getCustodyColumnsForEpoch(UInt64 epoch) {
    return MiscHelpersEip7594.required(spec.atEpoch(epoch).miscHelpers())
        .computeCustodyColumnIndexes(nodeId, totalCustodySubnetCount);
  }

  @Override
  public synchronized void onNewValidatedDataColumnSidecar(DataColumnSidecar dataColumnSidecar) {
    if (isMyCustody(dataColumnSidecar.getSlot(), dataColumnSidecar.getIndex())) {
      db.addSidecar(dataColumnSidecar);
      final List<SafeFuture<DataColumnSidecar>> pendingRequests =
          this.pendingRequests.remove(DataColumnIdentifier.createFromSidecar(dataColumnSidecar));
      if (pendingRequests != null) {
        for (SafeFuture<DataColumnSidecar> pendingRequest : pendingRequests) {
          pendingRequest.complete(dataColumnSidecar);
        }
      }
    }
  }

  private synchronized void clearCancelledPendingRequests() {
    pendingRequests.values().forEach(promises -> promises.removeIf(CompletableFuture::isDone));
    pendingRequests.entrySet().removeIf(e -> e.getValue().isEmpty());
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
  public synchronized SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      DataColumnIdentifier columnId) {
    Optional<DataColumnSidecar> existingColumn = db.getSidecar(columnId);
    if (existingColumn.isPresent()) {
      return SafeFuture.completedFuture(existingColumn);
    } else {
      clearCancelledPendingRequests();
      SafeFuture<DataColumnSidecar> promise = new SafeFuture<>();
      pendingRequests.computeIfAbsent(columnId, __ -> new ArrayList<>()).add(promise);
      return promise
          .orTimeout(requestTimeout)
          .thenApply(Optional::of)
          .exceptionally(
              err -> {
                if (ExceptionUtil.hasCause(err, TimeoutException.class)) {
                  return Optional.empty();
                } else {
                  throw new CompletionException(err);
                }
              });
    }
  }

  private void onEpoch(UInt64 epoch) {
    UInt64 pruneSlot = spec.computeStartSlotAtEpoch(getEarliestCustodyEpoch(epoch));
    db.pruneAllSidecars(pruneSlot);
  }

  @Override
  public void onSlot(UInt64 slot) {
    currentSlot = slot;
    UInt64 epoch = spec.computeEpochAtSlot(slot);
    if (slot.equals(spec.computeStartSlotAtEpoch(epoch))) {
      onEpoch(epoch);
    }
  }

  @Override
  public void onNewFinalizedCheckpoint(Checkpoint checkpoint, boolean fromOptimisticBlock) {
    advanceFirstIncompleteSlot(checkpoint.getEpoch());
  }

  private void advanceFirstIncompleteSlot(UInt64 finalizedEpoch) {
    record CompleteIncomplete(SlotCustody firstIncomplete, SlotCustody lastComplete) {
      static final CompleteIncomplete ZERO = new CompleteIncomplete(null, null);

      CompleteIncomplete add(SlotCustody newCustody) {
        if (firstIncomplete == null && newCustody.isIncomplete()) {
          return new CompleteIncomplete(newCustody, lastComplete);
        } else if (newCustody.isComplete()) {
          return new CompleteIncomplete(firstIncomplete, newCustody);
        } else {
          return this;
        }
      }

      Optional<UInt64> getFirstIncompleteSlot() {
        if (firstIncomplete != null) {
          return Optional.of(firstIncomplete.slot);
        } else if (lastComplete != null) {
          return Optional.of(lastComplete.slot.increment());
        } else {
          return Optional.empty();
        }
      }
    }

    UInt64 firstNonFinalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch.increment());

    streamPotentiallyIncompleteSlotCustodies()
        // will move FirstIncompleteSlot only to finalized slots
        .takeWhile(sc -> sc.slot.isLessThan(firstNonFinalizedSlot))
        .map(scan(CompleteIncomplete.ZERO, CompleteIncomplete::add))
        .takeWhile(c -> c.firstIncomplete == null)
        .reduce((a, b) -> b) // takeLast()
        .flatMap(CompleteIncomplete::getFirstIncompleteSlot)
        .ifPresent(db::setFirstIncompleteSlot);
  }

  private Stream<SlotCustody> streamPotentiallyIncompleteSlotCustodies() {
    if (currentSlot == null) {
      return Stream.empty();
    }

    UInt64 firstIncompleteSlot =
        db.getFirstIncompleteSlot().orElseGet(() -> getEarliestCustodySlot(currentSlot));

    return Stream.iterate(
            firstIncompleteSlot, slot -> slot.isLessThanOrEqualTo(currentSlot), UInt64::increment)
        .map(
            slot -> {
              Optional<Bytes32> maybeCanonicalBlockRoot = getBlockRootIfHaveBlobs(slot);
              List<UInt64> requiredColumns = getCustodyColumnsForSlot(slot);
              List<DataColumnIdentifier> existingColumns =
                  db.streamColumnIdentifiers(slot).toList();
              return new SlotCustody(
                  slot, maybeCanonicalBlockRoot, requiredColumns, existingColumns);
            });
  }

  private Optional<Bytes32> getBlockRootIfHaveBlobs(UInt64 slot) {
    return blockResolver
        .getBlockAtSlot(slot)
        .filter(
            block ->
                block
                        .getBeaconBlock()
                        .flatMap(b -> b.getBody().toVersionEip7594())
                        .map(b -> b.getBlobKzgCommitments().size())
                        .orElse(0)
                    > 0)
        .map(BeaconBlock::getRoot);
  }

  @Override
  public Stream<ColumnSlotAndIdentifier> streamMissingColumns() {
    return streamPotentiallyIncompleteSlotCustodies()
        // waiting a column for [gossipWaitSlots] to be delivered by gossip
        // and not considering it missing yet
        .takeWhile(
            slotCustody -> slotCustody.slot.plus(gossipWaitSlots).isLessThanOrEqualTo(currentSlot))
        .flatMap(
            slotCustody ->
                slotCustody.getIncompleteColumns().stream()
                    .map(colId -> new ColumnSlotAndIdentifier(slotCustody.slot(), colId)));
  }

  private static <TElem, TAccum> Function<TElem, TAccum> scan(
      TAccum identity, BiFunction<TAccum, TElem, TAccum> combiner) {
    return new Function<>() {
      private TAccum curValue = identity;

      @Override
      public TAccum apply(TElem elem) {
        curValue = combiner.apply(curValue, elem);
        return curValue;
      }
    };
  }
}
