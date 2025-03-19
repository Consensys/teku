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
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

public class DasSamplerBasic implements DataAvailabilitySampler, FinalizedCheckpointChannel {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final DataColumnSidecarCustody custody;
  private final DataColumnSidecarRetriever retriever;

  private final Spec spec;
  private final CurrentSlotProvider currentSlotProvider;
  private final DataColumnSidecarDbAccessor db;

  public DasSamplerBasic(
      final Spec spec,
      final CurrentSlotProvider currentSlotProvider,
      final DataColumnSidecarDbAccessor db,
      final DataColumnSidecarCustody custody,
      final DataColumnSidecarRetriever retriever) {
    this.currentSlotProvider = currentSlotProvider;
    checkNotNull(spec);
    checkNotNull(db);
    checkNotNull(custody);
    checkNotNull(retriever);
    this.spec = spec;
    this.db = db;
    this.custody = custody;
    this.retriever = retriever;
  }

  private int getColumnCount(final UInt64 slot) {
    return SpecConfigFulu.required(spec.atSlot(slot).getConfig()).getNumberOfColumns();
  }

  private List<DataColumnSlotAndIdentifier> calculateSamplingColumnIds(
      final UInt64 slot, final Bytes32 blockRoot) {
    return custody.getCustodyColumnIndices(spec.computeEpochAtSlot(slot)).stream()
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

  @Override
  public SafeFuture<List<UInt64>> checkDataAvailability(
      final UInt64 slot, final Bytes32 blockRoot, final Bytes32 parentRoot) {

    final Set<DataColumnSlotAndIdentifier> requiredColumnIdentifiers =
        new HashSet<>(calculateSamplingColumnIds(slot, blockRoot));

    LOG.debug(
        "checkDataAvailability(): checking {} columns for block {} ({})",
        requiredColumnIdentifiers.size(),
        slot,
        blockRoot);

    final SafeFuture<List<DataColumnSlotAndIdentifier>> columnsInCustodyFuture =
        maybeHasColumnsInCustody(requiredColumnIdentifiers);

    return columnsInCustodyFuture.thenCompose(
        columnsInCustodyList -> {
          final Set<DataColumnSlotAndIdentifier> columnsInCustody =
              new HashSet<>(columnsInCustodyList);

          final Set<DataColumnSlotAndIdentifier> missingColumn =
              Sets.difference(requiredColumnIdentifiers, columnsInCustody);

          if (LOG.isInfoEnabled()) {
            final List<Integer> existingColumnIndexes =
                Sets.intersection(requiredColumnIdentifiers, columnsInCustody).stream()
                    .map(it -> it.columnIndex().intValue())
                    .sorted()
                    .toList();

            LOG.info(
                "checkDataAvailability(): got {} (of {}) columns from custody (or received by Gossip) for block {} ({}), columns: {}",
                existingColumnIndexes.size(),
                requiredColumnIdentifiers.size(),
                slot,
                blockRoot,
                StringifyUtil.columnIndexesToString(existingColumnIndexes, getColumnCount(slot)));
          }

          final SafeFuture<List<DataColumnSidecar>> columnsRetrievedFuture =
              SafeFuture.collectAll(missingColumn.stream().map(retriever::retrieve))
                  .thenPeek(
                      retrievedColumns -> {
                        if (!retrievedColumns.isEmpty()) {
                          LOG.info(
                              "checkDataAvailability(): retrieved remaining {} (of {}) columns via Req/Resp for block {} ({})",
                              retrievedColumns.size(),
                              requiredColumnIdentifiers.size(),
                              slot,
                              blockRoot);
                        }
                        retrievedColumns.stream()
                            .map(custody::onNewValidatedDataColumnSidecar)
                            .forEach(updateFuture -> updateFuture.ifExceptionGetsHereRaiseABug());
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
}
