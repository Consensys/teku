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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DasSamplerBasic
    implements DataAvailabilitySampler, FinalizedCheckpointChannel, SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final DataColumnSidecarCustody custody;
  private final DataColumnSidecarRetriever retriever;

  private final Spec spec;
  private final CurrentSlotProvider currentSlotProvider;
  private final DataColumnSidecarDbAccessor db;
  private final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier;
  private final Map<Bytes32, DataColumnSamplingTracker> recentlySampledColumnsBySlot =
      new ConcurrentHashMap<>();
  private final RecentChainData recentChainData;

  private volatile UInt64 lastFinalizedSlot = UInt64.ZERO;

  public DasSamplerBasic(
      final Spec spec,
      final CurrentSlotProvider currentSlotProvider,
      final DataColumnSidecarDbAccessor db,
      final DataColumnSidecarCustody custody,
      final DataColumnSidecarRetriever retriever,
      final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier,
      final RecentChainData recentChainData) {
    this.currentSlotProvider = currentSlotProvider;
    checkNotNull(spec);
    checkNotNull(db);
    checkNotNull(custody);
    checkNotNull(retriever);
    this.spec = spec;
    this.db = db;
    this.custody = custody;
    this.retriever = retriever;
    this.custodyGroupCountManagerSupplier = custodyGroupCountManagerSupplier;
    this.recentChainData = recentChainData;
  }

  public void onNewValidatedDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar, final RemoteOrigin remoteOrigin) {
    LOG.debug(
        "sampling data column {} - origin: {}",
        () -> DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar),
        () -> remoteOrigin);

    recentlySampledColumnsBySlot
        .computeIfAbsent(
            dataColumnSidecar.getBeaconBlockRoot(),
            k ->
                DataColumnSamplingTracker.create(
                    dataColumnSidecar.getSlot(),
                    dataColumnSidecar.getBeaconBlockRoot(),
                    custodyGroupCountManagerSupplier.get()))
        .add(DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar), remoteOrigin);
  }

  public void onNewBlock(final BeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {

    LOG.info("sampling for block {} - origin: {}", block.toLogString(), remoteOrigin);

    if (checkSamplingEligibility(block) == SamplingEligibilityStatus.REQUIRED) {
      recentlySampledColumnsBySlot.computeIfAbsent(
          block.getRoot(),
          k ->
              DataColumnSamplingTracker.create(
                  block.getSlot(), block.getRoot(), custodyGroupCountManagerSupplier.get()));
    }
  }

  @Override
  public SafeFuture<List<UInt64>> checkDataAvailability(
      final UInt64 slot, final Bytes32 blockRoot) {

    final DataColumnSamplingTracker tracker =
        recentlySampledColumnsBySlot.computeIfAbsent(
            blockRoot,
            k ->
                DataColumnSamplingTracker.create(
                    slot, blockRoot, custodyGroupCountManagerSupplier.get()));

    final List<DataColumnSlotAndIdentifier> missingColumns = tracker.getMissingColumns();

    SafeFuture.collectAll(
            missingColumns.stream().map(id -> retrieveColumnWithSamplingAndCustody(id, tracker)))
        .thenAccept(
            retrievedColumns -> {
              if (retrievedColumns.size() == missingColumns.size()) {
                LOG.info(
                    "checkDataAvailability(): retrieved remaining {} (of {}) columns via Req/Resp for block {} ({})",
                    retrievedColumns.size(),
                    tracker.samplingRequirement.size(),
                    slot,
                    blockRoot);
              } else {
                throw new IllegalStateException(
                    String.format(
                        "Retrieved only(%d) out of %d missing columns for slot %s (%s) with %d required columns",
                        retrievedColumns.size(),
                        tracker.getMissingColumns().size(),
                        slot,
                        blockRoot,
                        tracker.samplingRequirement.size()));
              }
            })
        .finishError(LOG);

    return tracker.completionFuture;
  }

  private SafeFuture<DataColumnSidecar> retrieveColumnWithSamplingAndCustody(
      final DataColumnSlotAndIdentifier id, final DataColumnSamplingTracker tracker) {
    return retriever
        .retrieve(id)
        .thenPeek(
            sidecar -> {
              tracker.add(id, RemoteOrigin.RPC);
              custody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC).finishError(LOG);
            });
  }

  @Override
  public void flush() {
    retriever.flush();
  }

  private boolean hasBlobs(final BeaconBlock block) {
    return !block.getBody().getOptionalBlobKzgCommitments().orElseThrow().isEmpty();
  }

  private boolean isInCustodyPeriod(final BeaconBlock block) {
    final MiscHelpersFulu miscHelpersFulu =
        MiscHelpersFulu.required(spec.atSlot(block.getSlot()).miscHelpers());
    final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlotProvider.getCurrentSlot());
    return miscHelpersFulu.isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
        currentEpoch, spec.computeEpochAtSlot(block.getSlot()));
  }

  @Override
  public SamplingEligibilityStatus checkSamplingEligibility(final BeaconBlock block) {
    if (!spec.atSlot(block.getSlot()).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      return SamplingEligibilityStatus.NOT_REQUIRED_BEFORE_FULU;
    } else if (!isInCustodyPeriod(block)) {
      return SamplingEligibilityStatus.NOT_REQUIRED_OLD_EPOCH;
    } else if (!hasBlobs(block)) {
      return SamplingEligibilityStatus.NOT_REQUIRED_NO_BLOBS;
    } else {
      return SamplingEligibilityStatus.REQUIRED;
    }
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    db.getFirstSamplerIncompleteSlot()
        .thenCompose(
            maybeSlot ->
                maybeSlot.map(db::setFirstSamplerIncompleteSlot).orElse(SafeFuture.COMPLETE))
        .finish(
            ex ->
                LOG.error(
                    String.format("Failed to update incomplete sampler slot on %s", checkpoint),
                    ex));
    lastFinalizedSlot = spec.computeStartSlotAtEpoch(checkpoint.getEpoch());
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final UInt64 localLastFinalizedSlot = lastFinalizedSlot;
    recentlySampledColumnsBySlot
        .values()
        .removeIf(
            tracker -> {
              if (tracker.completionFuture.isDone()) {
                return true;
              }
              if (tracker.slot.isLessThanOrEqualTo(localLastFinalizedSlot)
                  || recentChainData.containsBlock(tracker.blockRoot)) {

                // make sure the future releases any pending waiters
                tracker.completionFuture.completeExceptionally(
                    new RuntimeException("DAS sampling expired"));
                return true;
              }

              return false;
            });
  }

  private record DataColumnSamplingTracker(
      UInt64 slot,
      Bytes32 blockRoot,
      List<UInt64> samplingRequirement,
      Set<UInt64> missingColumns,
      SafeFuture<List<UInt64>> completionFuture) {
    static DataColumnSamplingTracker create(
        final UInt64 slot,
        final Bytes32 blockRoot,
        final CustodyGroupCountManager custodyGroupCountManager) {
      final List<UInt64> samplingRequirement = custodyGroupCountManager.getSamplingColumnIndices();
      final Set<UInt64> missingColumns = ConcurrentHashMap.newKeySet(samplingRequirement.size());
      missingColumns.addAll(samplingRequirement);
      return new DataColumnSamplingTracker(
          slot, blockRoot, samplingRequirement, missingColumns, new SafeFuture<>());
    }

    public void add(final DataColumnSlotAndIdentifier columnIdentifier, final RemoteOrigin origin) {
      if (slot.equals(columnIdentifier.slot()) && blockRoot.equals(columnIdentifier.blockRoot())) {
        LOG.debug("Adding column {} to sampling tracker", columnIdentifier);
        final boolean removed =
            missingColumns.removeIf(idx -> idx.equals(columnIdentifier.columnIndex()));
        if (!removed) {
          LOG.info(
              "Column {} was already marked as received, origin: {}", columnIdentifier, origin);
          return;
        }

        if (missingColumns.isEmpty()) {
          LOG.info(
              "Sampling complete for slot {} root {} via column {} received via {}",
              slot,
              blockRoot,
              columnIdentifier.columnIndex(),
              origin);
          completionFuture.complete(samplingRequirement);
        } else {
          LOG.debug(
              "Sampling still pending for slot {} root {}, remaining columns: {}",
              slot,
              blockRoot,
              missingColumns);
        }
      }
    }

    private List<DataColumnSlotAndIdentifier> getMissingColumns() {
      return missingColumns.stream()
          .map(idx -> new DataColumnSlotAndIdentifier(slot, blockRoot, idx))
          .toList();
    }
  }
}
