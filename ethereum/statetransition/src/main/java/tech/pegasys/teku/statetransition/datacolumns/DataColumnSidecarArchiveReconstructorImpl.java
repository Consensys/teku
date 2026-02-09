/*
 * Copyright Consensys Software Inc., 2025
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.SidecarArchivePrunableChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class DataColumnSidecarArchiveReconstructorImpl
    implements DataColumnSidecarArchiveReconstructor, FinalizedCheckpointChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final CombinedChainDataClient chainDataClient;
  private final AsyncRunner reconstructionAsyncRunner;
  private final AtomicInteger nextTaskId = new AtomicInteger(0);
  // FIXME: I stink!
  private final Map<
          Integer, Map<SlotAndBlockRoot, SafeFuture<Map<UInt64, Optional<DataColumnSidecar>>>>>
      recoveryTasks;
  private final SpecConfigFulu specConfigFulu;
  private final int halfColumns;
  private final MiscHelpersFulu miscHelpersFulu;
  private final SchemaDefinitionsFulu schemaDefinitionsFulu;

  private final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier;
  private final Spec spec;
  private final UInt64 dataColumnSidecarExtensionRetentionEpochs;
  private final SidecarArchivePrunableChannel sidecarArchivePrunableChannel;

  // TODO: add metrics
  // FIXME: NOOP??
  public DataColumnSidecarArchiveReconstructorImpl(
      final CombinedChainDataClient chainDataClient,
      final AsyncRunner reconstructionAsyncRunner,
      final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier,
      final Spec spec,
      final int dataColumnSidecarExtensionRetentionEpochs,
      final SidecarArchivePrunableChannel sidecarArchivePrunableChannel) {
    this.chainDataClient = chainDataClient;
    this.reconstructionAsyncRunner = reconstructionAsyncRunner;
    this.custodyGroupCountManagerSupplier = custodyGroupCountManagerSupplier;
    this.spec = spec;
    this.dataColumnSidecarExtensionRetentionEpochs =
        UInt64.valueOf(dataColumnSidecarExtensionRetentionEpochs);
    this.specConfigFulu =
        SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
    this.halfColumns = specConfigFulu.getNumberOfColumns() / 2;
    this.miscHelpersFulu =
        MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
    this.schemaDefinitionsFulu =
        SchemaDefinitionsFulu.required(
            spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions());
    this.recoveryTasks = new ConcurrentHashMap<>();
    this.sidecarArchivePrunableChannel = sidecarArchivePrunableChannel;
  }

  // TODO: refactor, make concurrent safe
  @Override
  public SafeFuture<Optional<DataColumnSidecar>> reconstructDataColumnSidecar(
      final SignedBeaconBlock block, final UInt64 index, final int requestId) {
    final Map<SlotAndBlockRoot, SafeFuture<Map<UInt64, Optional<DataColumnSidecar>>>>
        slotAndBlockRootSafeFutureMap =
            recoveryTasks.computeIfAbsent(requestId, __ -> new HashMap<>());
    return slotAndBlockRootSafeFutureMap
        .computeIfAbsent(block.getSlotAndBlockRoot(), __ -> createTask(block))
        .thenApply(aMap -> aMap.get(index));
  }

  @Override
  public int onRequest() {
    return getNextTaskId();
  }

  private SafeFuture<Map<UInt64, Optional<DataColumnSidecar>>> createTask(
      final SignedBeaconBlock block) {
    final BlobSchema blobSchema = schemaDefinitionsFulu.getBlobSchema();

    final List<UInt64> firstHalfOfIndices =
        Stream.iterate(UInt64.ZERO, UInt64::increment).limit(halfColumns).toList();
    final SafeFuture<List<DataColumnSidecar>> dataColumnSidecars =
        chainDataClient.getDataColumnSidecars(block.getSlot(), firstHalfOfIndices);
    final SafeFuture<List<List<KZGProof>>> kzgProofs =
        chainDataClient.getDataColumnSidecarProofs(block.getSlot());
    // FIXME: Gloas??

    return reconstructionAsyncRunner
        .runAsync(
            () ->
                dataColumnSidecars.thenCombineAsync(
                    kzgProofs,
                    (sidecars, proofs) -> {
                      if (sidecars.isEmpty() || proofs.isEmpty()) {
                        return getEmptyResponse();
                      }

                      final List<BlobAndCellProofs> blobAndCellProofsList = new ArrayList<>();
                      for (int i = 0; i < sidecars.getFirst().getKzgCommitments().size(); i++) {
                        final int blobIndex = i;
                        final Bytes blob =
                            sidecars.stream()
                                .map(sidecar -> sidecar.getColumn().get(blobIndex).getBytes())
                                .reduce(Bytes.EMPTY, Bytes::concatenate);
                        final List<KZGProof> blobProofs = new ArrayList<>();
                        for (int j = 0; j < halfColumns; j++) {
                          blobProofs.add(
                              sidecars.get(j).getKzgProofs().get(blobIndex).getKZGProof());
                        }
                        for (int j = 0; j < halfColumns; j++) {
                          blobProofs.add(proofs.get(j).get(blobIndex));
                        }
                        final BlobAndCellProofs blobAndCellProofs =
                            new BlobAndCellProofs(blobSchema.create(blob), blobProofs);
                        blobAndCellProofsList.add(blobAndCellProofs);
                      }
                      final List<DataColumnSidecar> allDataColumnSidecars =
                          miscHelpersFulu.constructDataColumnSidecars(block, blobAndCellProofsList);

                      return Stream.iterate(UInt64.valueOf(halfColumns), UInt64::increment)
                          .limit(halfColumns)
                          .collect(
                              Collectors.toMap(
                                  index -> index,
                                  index ->
                                      Optional.of(allDataColumnSidecars.get(index.intValue()))));
                    }))
        .exceptionally(
            ex -> {
              LOG.error(ex.getMessage(), ex);
              return getEmptyResponse();
            });
  }

  private Map<UInt64, Optional<DataColumnSidecar>> getEmptyResponse() {
    return Stream.iterate(UInt64.valueOf(halfColumns), UInt64::increment)
        .limit(halfColumns)
        .collect(Collectors.toMap(index -> index, __ -> Optional.empty()));
  }

  @Override
  public boolean isSidecarPruned(final UInt64 slot, final UInt64 index) {
    final boolean isSupernode =
        specConfigFulu.getNumberOfColumns()
            == custodyGroupCountManagerSupplier.get().getCustodyGroupCount();
    if (!isSupernode) {
      return false;
    }

    if (index.isLessThan(halfColumns)) {
      return false;
    }

    final UInt64 slotEpoch = spec.computeEpochAtSlot(slot);
    if (!spec.isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
        chainDataClient.getStore(), slotEpoch)) {
      return false;
    }

    if (chainDataClient.getFinalizedBlockSlot().isEmpty()) {
      return false;
    }

    final UInt64 finalizedEpoch =
        spec.computeEpochAtSlot(chainDataClient.getFinalizedBlockSlot().get());
    final UInt64 currentEpoch = spec.getCurrentEpoch(chainDataClient.getStore());
    return slotEpoch.isLessThan(
        finalizedEpoch.min(currentEpoch.minusMinZero(dataColumnSidecarExtensionRetentionEpochs)));
  }

  @Override
  public void onRequestCompleted(final int requestId) {
    final Map<SlotAndBlockRoot, SafeFuture<Map<UInt64, Optional<DataColumnSidecar>>>> removed =
        recoveryTasks.remove(requestId);
    if (removed != null) {
      LOG.debug("Request completed: {}, removing tasks ()", requestId);
    }
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    final boolean isSupernode =
        specConfigFulu.getNumberOfColumns()
            == custodyGroupCountManagerSupplier.get().getCustodyGroupCount();
    if (!isSupernode) {
      return;
    }
    final UInt64 finalizedEpoch = checkpoint.getEpoch();
    final UInt64 currentEpoch = spec.getCurrentEpoch(chainDataClient.getStore());
    // TODO: check on +1 error
    final UInt64 lastPrunableSlot =
        spec.computeStartSlotAtEpoch(
            finalizedEpoch.min(
                currentEpoch.minusMinZero(dataColumnSidecarExtensionRetentionEpochs)));
    sidecarArchivePrunableChannel.onSidecarArchivePrunableSlot(lastPrunableSlot).finishDebug(LOG);
  }

  private int getNextTaskId() {
    final int nextTaskId = this.nextTaskId.getAndIncrement();
    if (this.nextTaskId.get() > (Integer.MAX_VALUE - 1000)) {
      this.nextTaskId.set(0);
    }
    return nextTaskId;
  }
}
