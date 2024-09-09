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
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

public class DasSamplerBasic
    implements DataAvailabilitySampler, FinalizedCheckpointChannel, SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final UInt256 nodeId;
  private final int totalCustodySubnetCount;
  private final DataColumnSidecarCustody custody;

  private final Spec spec;
  private final DataColumnSidecarDbAccessor db;

  public DasSamplerBasic(
      final Spec spec,
      final DataColumnSidecarDbAccessor db,
      final DataColumnSidecarCustody custody,
      final UInt256 nodeId,
      final int totalCustodySubnetCount) {
    checkNotNull(spec);
    checkNotNull(db);
    this.spec = spec;
    this.db = db;
    this.custody = custody;
    this.nodeId = nodeId;
    this.totalCustodySubnetCount = totalCustodySubnetCount;
  }

  @Override
  public SafeFuture<List<DataColumnSidecar>> checkDataAvailability(
      UInt64 slot, Bytes32 blockRoot, Bytes32 parentRoot) {
    LOG.info("Requested checkDataAvailability for {}({})", blockRoot, slot);
    final Optional<MiscHelpersEip7594> maybeMiscHelpers =
        spec.atSlot(slot).miscHelpers().toVersionEip7594();
    final Optional<List<UInt64>> columnIndexes =
        maybeMiscHelpers.map(
            miscHelpersEip7594 ->
                miscHelpersEip7594.computeCustodyColumnIndexes(nodeId, totalCustodySubnetCount));
    if (columnIndexes.isEmpty()) {
      return SafeFuture.completedFuture(List.of());
    }

    return SafeFuture.collectAll(
            columnIndexes.get().stream()
                .map(
                    index ->
                        custody.getCustodyDataColumnSidecar(
                            new DataColumnIdentifier(blockRoot, index))))
        .thenApply(
            listOfOptionals -> {
              // TODO: remove or move to debug logging
              LOG.info(
                  "Collected {} DataColumnSidecars in checkDataAvailability for {}({})",
                  listOfOptionals.stream()
                      .map(
                          maybeSidecar ->
                              maybeSidecar.map(DataColumnSidecar::toLogString).orElse("[empty]"))
                      .collect(Collectors.joining(",")),
                  blockRoot,
                  slot);
              return listOfOptionals.stream().map(Optional::orElseThrow).toList();
            });
  }

  @Override
  public void onSlot(UInt64 slot) {}

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
