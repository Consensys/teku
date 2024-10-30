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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

public class DasSamplerBasic implements DataAvailabilitySampler, FinalizedCheckpointChannel {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final UInt256 nodeId;
  private final int totalCustodySubnetCount;
  private final UpdatableDataColumnSidecarCustody custody;
  private final DataColumnSidecarRetriever retriever;

  private final Spec spec;
  private final CurrentSlotProvider currentSlotProvider;
  private final DataColumnSidecarDbAccessor db;

  public DasSamplerBasic(
      final Spec spec,
      final CurrentSlotProvider currentSlotProvider,
      final DataColumnSidecarDbAccessor db,
      final UpdatableDataColumnSidecarCustody custody,
      final DataColumnSidecarRetriever retriever,
      final UInt256 nodeId,
      final int totalCustodySubnetCount) {
    this.currentSlotProvider = currentSlotProvider;
    checkNotNull(spec);
    checkNotNull(db);
    checkNotNull(custody);
    checkNotNull(retriever);
    this.spec = spec;
    this.db = db;
    this.custody = custody;
    this.retriever = retriever;
    this.nodeId = nodeId;
    this.totalCustodySubnetCount = totalCustodySubnetCount;
  }

  private int getColumnCount(UInt64 slot) {
    return SpecConfigEip7594.required(spec.atSlot(slot).getConfig()).getNumberOfColumns();
  }

  private List<DataColumnSlotAndIdentifier> calculateSamplingColumnIds(
      UInt64 slot, Bytes32 blockRoot) {
    final Optional<MiscHelpersEip7594> maybeMiscHelpers =
        spec.atSlot(slot).miscHelpers().toVersionEip7594();
    return maybeMiscHelpers
        .map(
            miscHelpersEip7594 ->
                miscHelpersEip7594.computeCustodyColumnIndexes(nodeId, totalCustodySubnetCount))
        .orElse(Collections.emptyList())
        .stream()
        .map(columnIndex -> new DataColumnSlotAndIdentifier(slot, blockRoot, columnIndex))
        .toList();
  }

  private SafeFuture<Optional<DataColumnSlotAndIdentifier>> checkColumnInCustody(
      DataColumnSlotAndIdentifier columnIdentifier) {
    return custody
        .hasCustodyDataColumnSidecar(columnIdentifier)
        .thenApply(hasColumn -> hasColumn ? Optional.of(columnIdentifier) : Optional.empty());
  }

  private SafeFuture<List<DataColumnSlotAndIdentifier>> maybeHasColumnsInCustody(
      Collection<DataColumnSlotAndIdentifier> columnIdentifiers) {
    return SafeFuture.collectAll(columnIdentifiers.stream().map(this::checkColumnInCustody))
        .thenApply(list -> list.stream().flatMap(Optional::stream).toList());
  }

  @Override
  public SafeFuture<List<UInt64>> checkDataAvailability(
      UInt64 slot, Bytes32 blockRoot, Bytes32 parentRoot) {

    final Set<DataColumnSlotAndIdentifier> requiredColumnIdentifiers =
        new HashSet<>(calculateSamplingColumnIds(slot, blockRoot));

    LOG.debug(
        "checkDataAvailability(): checking {} columns for block {} ({})",
        requiredColumnIdentifiers.size(),
        slot,
        blockRoot);

    SafeFuture<List<DataColumnSlotAndIdentifier>> columnsInCustodyFuture =
        maybeHasColumnsInCustody(requiredColumnIdentifiers);

    return columnsInCustodyFuture.thenCompose(
        columnsInCustodyList -> {
          Set<DataColumnSlotAndIdentifier> columnsInCustody = new HashSet<>(columnsInCustodyList);

          Set<DataColumnSlotAndIdentifier> missingColumn =
              Sets.difference(requiredColumnIdentifiers, columnsInCustody);

          if (LOG.isInfoEnabled()) {
            List<Integer> existingColumnIndexes =
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

          SafeFuture<List<DataColumnSidecar>> columnsRetrievedFuture =
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

  private boolean isEIP7594(BeaconBlock block) {
    return spec.atSlot(block.getSlot())
        .getMilestone()
        .isGreaterThanOrEqualTo(SpecMilestone.EIP7594);
  }

  private boolean hasBlobs(BeaconBlock block) {
    return !block.getBody().getOptionalBlobKzgCommitments().orElseThrow().isEmpty();
  }

  private boolean isInCustodyPeriod(BeaconBlock block) {
    final MiscHelpersEip7594 miscHelpersEip7594 =
        MiscHelpersEip7594.required(spec.atSlot(block.getSlot()).miscHelpers());
    UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlotProvider.getCurrentSlot());
    return miscHelpersEip7594.isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
        currentEpoch, spec.computeEpochAtSlot(block.getSlot()));
  }

  @Override
  public SamplingEligibilityStatus checkSamplingEligibility(BeaconBlock block) {
    if (!isEIP7594(block)) {
      return SamplingEligibilityStatus.NOT_REQUIRED_BEFORE_EIP7594;
    } else if (!isInCustodyPeriod(block)) {
      return SamplingEligibilityStatus.NOT_REQUIRED_OLD_EPOCH;
    } else if (!hasBlobs(block)) {
      return SamplingEligibilityStatus.NOT_REQUIRED_NO_BLOBS;
    } else {
      return SamplingEligibilityStatus.REQUIRED;
    }
  }

  @Override
  public void onNewFinalizedCheckpoint(Checkpoint checkpoint, boolean fromOptimisticBlock) {
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
