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

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.HashSet;
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
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

public class DasSamplerBasic
    implements DataAvailabilitySampler, FinalizedCheckpointChannel, SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final DataColumnSidecarCustody custody;
  private final DataColumnSidecarRetriever retriever;

  private final Spec spec;
  private final CurrentSlotProvider currentSlotProvider;
  private final DataColumnSidecarDbAccessor db;
  private final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier;
  private final Map<UInt64, Set<DataColumnSlotAndIdentifier>> recentlySampledColumnsBySlot =
      new ConcurrentHashMap<>();

  public DasSamplerBasic(
      final Spec spec,
      final CurrentSlotProvider currentSlotProvider,
      final DataColumnSidecarDbAccessor db,
      final DataColumnSidecarCustody custody,
      final DataColumnSidecarRetriever retriever,
      final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier) {
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
  }

  private int getColumnCount(final UInt64 slot) {
    return SpecConfigFulu.required(spec.atSlot(slot).getConfig()).getNumberOfColumns();
  }

  private List<DataColumnSlotAndIdentifier> calculateSamplingColumnIds(
      final UInt64 slot, final Bytes32 blockRoot) {
    return custodyGroupCountManagerSupplier.get().getSamplingColumnIndices().stream()
        .map(columnIndex -> new DataColumnSlotAndIdentifier(slot, blockRoot, columnIndex))
        .toList();
  }

  private SafeFuture<Optional<DataColumnSlotAndIdentifier>> checkColumnInCustody(
      final DataColumnSlotAndIdentifier columnIdentifier) {
    return custody
        .hasCustodyDataColumnSidecar(columnIdentifier)
        .thenApply(hasColumn -> hasColumn ? Optional.of(columnIdentifier) : Optional.empty());
  }

  private SafeFuture<List<DataColumnSlotAndIdentifier>> maybeHasColumnsInCustody(
      final Collection<DataColumnSlotAndIdentifier> columnIdentifiers) {
    return SafeFuture.collectAll(columnIdentifiers.stream().map(this::checkColumnInCustody))
        .thenApply(list -> list.stream().flatMap(Optional::stream).toList());
  }

  public void onNewValidatedDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar, final RemoteOrigin remoteOrigin) {
    if (isMySampling(dataColumnSidecar.getIndex())) {
      LOG.info(
          "caching sampling {} - origin: {}",
          DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar),
          remoteOrigin);
      recentlySampledColumnsBySlot
          .computeIfAbsent(dataColumnSidecar.getSlot(), k -> ConcurrentHashMap.newKeySet())
          .add(DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar));
    }
  }

  private boolean isMySampling(final UInt64 columnIndex) {
    return custodyGroupCountManagerSupplier.get().getSamplingColumnIndices().contains(columnIndex);
  }

  @Override
  public SafeFuture<List<UInt64>> checkDataAvailability(
      final UInt64 slot, final Bytes32 blockRoot) {

    final Set<DataColumnSlotAndIdentifier> requiredColumnIdentifiers =
        new HashSet<>(calculateSamplingColumnIds(slot, blockRoot));

    LOG.debug(
        "checkDataAvailability(): checking {} columns for block {} ({})",
        requiredColumnIdentifiers.size(),
        slot,
        blockRoot);

    final Set<DataColumnSlotAndIdentifier> recentlySampledColumnsForSlot =
        recentlySampledColumnsBySlot.getOrDefault(slot, Set.of());

    final Set<DataColumnSlotAndIdentifier> missingColumnsFromRecentlySeen =
        Sets.difference(requiredColumnIdentifiers, recentlySampledColumnsForSlot);

    if (missingColumnsFromRecentlySeen.isEmpty()) {
      LOG.info("All seen slot: {} root: {}", slot, blockRoot);
      return SafeFuture.completedFuture(
          requiredColumnIdentifiers.stream()
              .map(DataColumnSlotAndIdentifier::columnIndex)
              .toList());
    }

    // TODO, we can check in custody only what we custody, not what is required for sampling
    final SafeFuture<List<DataColumnSlotAndIdentifier>> columnsInCustodyFuture =
        maybeHasColumnsInCustody(requiredColumnIdentifiers);

    return columnsInCustodyFuture.thenCompose(
        columnsInCustodyList -> {
          final Set<DataColumnSlotAndIdentifier> columnsInCustodyUnionRecentlySampled =
              new HashSet<>(columnsInCustodyList);
          columnsInCustodyUnionRecentlySampled.addAll(
              recentlySampledColumnsBySlot.getOrDefault(slot, Set.of()));

          LOG.info(
              "sampledAndCustody: {} slot: {} root: {}",
              columnsInCustodyUnionRecentlySampled.stream()
                  .map(DataColumnSlotAndIdentifier::columnIndex)
                  .sorted()
                  .toList(),
              slot,
              blockRoot);

          final Set<DataColumnSlotAndIdentifier> missingColumns =
              Sets.difference(requiredColumnIdentifiers, columnsInCustodyUnionRecentlySampled);

          LOG.info(
              "missing: {} slot: {} root: {}",
              missingColumns.stream()
                  .map(DataColumnSlotAndIdentifier::columnIndex)
                  .sorted()
                  .toList(),
              slot,
              blockRoot);

          if (LOG.isDebugEnabled()) {
            final List<Integer> existingColumnIndices =
                Sets.intersection(requiredColumnIdentifiers, columnsInCustodyUnionRecentlySampled)
                    .stream()
                    .map(it -> it.columnIndex().intValue())
                    .sorted()
                    .toList();

            LOG.debug(
                "checkDataAvailability(): got {} (of {}) columns from custody (or received by Gossip) for block {} ({}), columns: {}",
                existingColumnIndices.size(),
                requiredColumnIdentifiers.size(),
                slot,
                blockRoot,
                StringifyUtil.columnIndicesToString(existingColumnIndices, getColumnCount(slot)));
          }

          final SafeFuture<List<DataColumnSidecar>> columnsRetrievedFuture =
              SafeFuture.collectAll(missingColumns.stream().map(retriever::retrieve))
                  .thenPeek(
                      retrievedColumns -> {
                        if (retrievedColumns.size() == missingColumns.size()) {
                          LOG.debug(
                              "checkDataAvailability(): retrieved remaining {} (of {}) columns via Req/Resp for block {} ({})",
                              retrievedColumns.size(),
                              requiredColumnIdentifiers.size(),
                              slot,
                              blockRoot);

                          retrievedColumns.stream()
                              .map(
                                  sidecar ->
                                      custody.onNewValidatedDataColumnSidecar(
                                          sidecar, RemoteOrigin.RPC))
                              .forEach(updateFuture -> updateFuture.finishStackTrace());
                        } else {
                          throw new IllegalStateException(
                              String.format(
                                  "Retrieved only(%d) out of %d missing columns for slot %s (%s) with %d required columns",
                                  retrievedColumns.size(),
                                  missingColumns.size(),
                                  slot,
                                  blockRoot,
                                  requiredColumnIdentifiers.size()));
                        }
                      });

          return columnsRetrievedFuture.thenApply(
              __ ->
                  requiredColumnIdentifiers.stream()
                      .map(DataColumnSlotAndIdentifier::columnIndex)
                      .toList());
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
  }

  @Override
  public void onSlot(final UInt64 slot) {
    recentlySampledColumnsBySlot.keySet().removeIf(slotKey -> slotKey.isLessThan(slot.minus(64)));
  }
}
