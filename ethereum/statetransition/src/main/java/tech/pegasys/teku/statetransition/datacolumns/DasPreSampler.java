/*
 * Copyright Consensys Software Inc., 2026
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

import static tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED;

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
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;

public class DasPreSampler {

  private static final Logger LOG = LogManager.getLogger();

  private final DataAvailabilitySampler sampler;
  private final DataColumnSidecarCustody custody;
  private final CustodyGroupCountManager custodyGroupCountManager;

  public DasPreSampler(
      final DataAvailabilitySampler sampler,
      final DataColumnSidecarCustody custody,
      final CustodyGroupCountManager custodyGroupCountManager) {
    this.sampler = sampler;
    this.custody = custody;
    this.custodyGroupCountManager = custodyGroupCountManager;
  }

  private boolean isSamplingRequired(final SignedBeaconBlock block) {
    if (block == null) {
      LOG.debug("SignedBeaconBlock was unexpectedly null");
      return false;
    }
    return sampler.checkSamplingEligibility(block.getMessage()) == REQUIRED;
  }

  public void onNewPreImportBlocks(final Collection<SignedBeaconBlock> blocks) {
    final List<SignedBeaconBlock> blocksToSample =
        blocks.stream().filter(this::isSamplingRequired).toList();

    LOG.debug(
        "DasPreSampler: requesting pre-sample for {} (of {} received) blocks: {}",
        blocksToSample.size(),
        blocks.size(),
        StringifyUtil.toIntRangeStringWithSize(
            blocksToSample.stream().map(block -> block.getSlot().intValue()).toList()));

    blocksToSample.forEach(this::onNewPreImportBlock);
    sampler.flush();
  }

  private void onNewPreImportBlock(final SignedBeaconBlock block) {
    final Set<DataColumnSlotAndIdentifier> requiredColumnIdentifiers =
        new HashSet<>(calculateSamplingColumnIds(block.getSlot(), block.getRoot()));

    final SafeFuture<List<DataColumnSlotAndIdentifier>> columnsInCustodyFuture =
        maybeHasColumnsInCustody(requiredColumnIdentifiers);

    columnsInCustodyFuture
        .thenAccept(
            columnsInCustody ->
                columnsInCustody.forEach(
                    columnId ->
                        sampler.onNewValidatedDataColumnSidecar(columnId, RemoteOrigin.CUSTODY)))
        .always(
            () ->
                sampler
                    .checkDataAvailability(block.getSlot(), block.getRoot())
                    .finish(
                        succ ->
                            LOG.debug(
                                "DasPreSampler: success pre-sampling block {} ({})",
                                block.getSlot(),
                                block.getRoot()),
                        err ->
                            LOG.debug(
                                "DasPreSampler: error pre-sampling block {} ({}): {}",
                                block.getSlot(),
                                block.getRoot(),
                                err)));
  }

  private List<DataColumnSlotAndIdentifier> calculateSamplingColumnIds(
      final UInt64 slot, final Bytes32 blockRoot) {
    return custodyGroupCountManager.getSamplingColumnIndices().stream()
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
}
