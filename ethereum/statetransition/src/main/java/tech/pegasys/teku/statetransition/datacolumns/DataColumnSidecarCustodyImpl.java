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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;

import static com.google.common.base.Preconditions.checkNotNull;

public class DataColumnSidecarCustodyImpl
    implements UpdatableDataColumnSidecarCustody, SlotEventsChannel {

  public interface BlockRootResolver {

    Optional<Bytes32> getCanonicalBlockRootAtSlot(UInt64 slot);
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

    public boolean isIncomplete() {
      return !getIncompleteColumns().isEmpty();
    }
  }

  // for how long the custody will wait for a missing column to be gossiped
  private final int gossipWaitSlots = 2;

  private final Spec spec;
  private final DataColumnSidecarDB db;
  private final BlockRootResolver blockRootResolver;
  private final UInt256 nodeId;
  private final int totalCustodySubnetCount;

  private final UInt64 eip7594StartEpoch;

  private UInt64 currentSlot = null;

  public DataColumnSidecarCustodyImpl(
      Spec spec,
      BlockRootResolver blockRootResolver,
      DataColumnSidecarDB db,
      UInt256 nodeId,
      int totalCustodySubnetCount) {

    checkNotNull(spec);
    checkNotNull(blockRootResolver);
    checkNotNull(db);
    checkNotNull(nodeId);

    this.spec = spec;
    this.db = db;
    this.blockRootResolver = blockRootResolver;
    this.nodeId = nodeId;
    this.totalCustodySubnetCount = totalCustodySubnetCount;
    this.eip7594StartEpoch = spec.getForkSchedule().getFork(SpecMilestone.EIP7594).getEpoch();
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

  private Set<UInt64> getCustodyColumnsForSlot(UInt64 slot) {
    return getCustodyColumnsForEpoch(spec.computeEpochAtSlot(slot));
  }

  private Set<UInt64> getCustodyColumnsForEpoch(UInt64 epoch) {
    return MiscHelpersEip7594.required(spec.atEpoch(epoch).miscHelpers())
        .computeCustodyColumnIndexes(nodeId, epoch, totalCustodySubnetCount);
  }

  @Override
  public void onNewValidatedDataColumnSidecar(DataColumnSidecar dataColumnSidecar) {
    if (isMyCustody(dataColumnSidecar.getSlot(), dataColumnSidecar.getIndex())) {
      db.addSidecar(dataColumnSidecar);
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
                    .computeCustodyColumnIndexes(nodeId, epoch, totalCustodySubnetCount)
                    .contains(columnIndex))
        .orElse(false);
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      DataColumnIdentifier columnId) {
    return SafeFuture.completedFuture(db.getSidecar(columnId));
  }

  private void onEpoch(UInt64 epoch) {
    UInt64 pruneSlot = spec.computeStartSlotAtEpoch(getEarliestCustodyEpoch(epoch));
    db.pruneAllSidecars(pruneSlot);
    advanceLatestCompleteSlot();
  }

  @Override
  public void onSlot(UInt64 slot) {
    currentSlot = slot;
    UInt64 epoch = spec.computeEpochAtSlot(slot);
    if (slot.equals(spec.computeStartSlotAtEpoch(epoch))) {
      onEpoch(epoch);
    }
  }

  private void advanceLatestCompleteSlot() {
    streamSlotCustodies()
        .dropWhile(slotCustody -> !slotCustody.isIncomplete())
        .findFirst()
        .ifPresent(firstIncomplete -> db.setFirstIncompleteSlot(firstIncomplete.slot));
  }

  private Stream<SlotCustody> streamSlotCustodies() {
    if (currentSlot == null) {
      return Stream.empty();
    }

    UInt64 firstIncompleteSlot =
        db.getFirstIncompleteSlot().orElseGet(() -> getEarliestCustodySlot(currentSlot));

    return Stream.iterate(
            firstIncompleteSlot,
            slot -> slot.plus(gossipWaitSlots).isLessThanOrEqualTo(currentSlot),
            UInt64::increment)
        .map(
            slot -> {
              Optional<Bytes32> maybeCanonicalBlockRoot =
                  blockRootResolver.getCanonicalBlockRootAtSlot(slot);
              Set<UInt64> requiredColumns = getCustodyColumnsForSlot(slot);
              List<DataColumnIdentifier> existingColumns =
                  db.streamColumnIdentifiers(slot).toList();
              return new SlotCustody(
                  slot, maybeCanonicalBlockRoot, requiredColumns, existingColumns);
            });
  }

  @Override
  public Stream<ColumnSlotAndIdentifier> streamMissingColumns() {
    return streamSlotCustodies()
        .flatMap(
            slotCustody ->
                slotCustody.getIncompleteColumns().stream()
                    .map(colId -> new ColumnSlotAndIdentifier(slotCustody.slot(), colId)));
  }
}
