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
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
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
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
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
  private final Map<Integer, Map<SlotAndBlockRoot, SafeFuture<ReconstructionResult>>> recoveryTasks;
  private final SpecConfigFulu specConfigFulu;
  private final int halfColumns;

  private final Supplier<Boolean> isSuperNodeSupplier;
  private final Spec spec;
  private final UInt64 dataColumnSidecarExtensionRetentionEpochs;
  private final SidecarArchivePrunableChannel sidecarArchivePrunableChannel;

  private final MetricsHistogram reconstructionTimeSeconds;
  private final Counter reconstructionSuccessCounter;
  private final Counter reconstructionFailureCounter;

  public DataColumnSidecarArchiveReconstructorImpl(
      final CombinedChainDataClient chainDataClient,
      final AsyncRunner reconstructionAsyncRunner,
      final Supplier<Boolean> isSuperNodeSupplier,
      final Spec spec,
      final int dataColumnSidecarExtensionRetentionEpochs,
      final SidecarArchivePrunableChannel sidecarArchivePrunableChannel,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider) {
    this.chainDataClient = chainDataClient;
    this.reconstructionAsyncRunner = reconstructionAsyncRunner;
    this.isSuperNodeSupplier = isSuperNodeSupplier;
    this.spec = spec;
    this.dataColumnSidecarExtensionRetentionEpochs =
        UInt64.valueOf(dataColumnSidecarExtensionRetentionEpochs);
    this.specConfigFulu =
        SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
    this.halfColumns = specConfigFulu.getNumberOfColumns() / 2;
    this.recoveryTasks = new ConcurrentHashMap<>();
    this.sidecarArchivePrunableChannel = sidecarArchivePrunableChannel;
    this.reconstructionTimeSeconds =
        new MetricsHistogram(
            metricsSystem,
            timeProvider,
            TekuMetricCategory.BEACON,
            "data_column_sidecar_archive_reconstruction_seconds",
            "Time taken to reconstruct archived data column sidecars",
            new double[] {0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 2.5, 5.0, 10.0});
    this.reconstructionSuccessCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "data_column_sidecar_archive_reconstruction_successes_total",
            "Total number of successful data column sidecar archive reconstructions");
    this.reconstructionFailureCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "data_column_sidecar_archive_reconstruction_failures_total",
            "Total number of failed data column sidecar archive reconstructions");
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> reconstructDataColumnSidecar(
      final SignedBeaconBlock block, final UInt64 index, final int requestId) {
    final Map<SlotAndBlockRoot, SafeFuture<ReconstructionResult>> slotAndBlockRootSafeFutureMap =
        recoveryTasks.computeIfAbsent(requestId, __ -> new ConcurrentHashMap<>());
    return slotAndBlockRootSafeFutureMap
        .computeIfAbsent(block.getSlotAndBlockRoot(), __ -> createTask(block))
        .thenApply(result -> result.get(index));
  }

  @Override
  public int onRequest() {
    return getNextTaskId();
  }

  private SafeFuture<ReconstructionResult> createTask(final SignedBeaconBlock block) {
    final DataColumnSidecarUtil dataColumnSidecarUtil =
        spec.getDataColumnSidecarUtil(block.getSlot());
    final MetricsHistogram.Timer timer = reconstructionTimeSeconds.startTimer();

    final List<UInt64> firstHalfOfIndices =
        Stream.iterate(UInt64.ZERO, UInt64::increment).limit(halfColumns).toList();
    final SafeFuture<List<DataColumnSidecar>> dataColumnSidecars =
        chainDataClient.getDataColumnSidecars(block.getSlot(), firstHalfOfIndices);
    final SafeFuture<List<List<KZGProof>>> kzgProofs =
        chainDataClient.getDataColumnSidecarProofs(block.getSlot());

    return reconstructionAsyncRunner
        .runAsync(
            () ->
                dataColumnSidecars.thenCombineAsync(
                    kzgProofs,
                    (sidecars, proofs) -> {
                      if (sidecars.isEmpty() || proofs.isEmpty()) {
                        return emptyResult();
                      }

                      final List<BlobAndCellProofs> blobAndCellProofsList =
                          constructBlobAndCellProofsList(sidecars, proofs);

                      final List<DataColumnSidecar> allDataColumnSidecars =
                          dataColumnSidecarUtil.constructDataColumnSidecars(
                              Optional.of(block.asHeader()),
                              block.getSlotAndBlockRoot(),
                              Optional.of(
                                  dataColumnSidecarUtil.getKzgCommitments(block.getMessage())),
                              dataColumnSidecarUtil.computeDataColumnKzgCommitmentsInclusionProof(
                                  block.getMessage().getBody()),
                              blobAndCellProofsList);

                      return new ReconstructionResult(
                          Stream.iterate(UInt64.valueOf(halfColumns), UInt64::increment)
                              .limit(halfColumns)
                              .collect(
                                  Collectors.toMap(
                                      index -> index,
                                      index ->
                                          Optional.of(
                                              allDataColumnSidecars.get(index.intValue())))));
                    }))
        .whenComplete(
            (result, error) -> {
              timer.closeUnchecked().run();
              if (error != null) {
                reconstructionFailureCounter.inc();
              } else {
                reconstructionSuccessCounter.inc();
              }
            })
        .exceptionally(
            ex -> {
              LOG.error(ex.getMessage(), ex);
              return emptyResult();
            });
  }

  private List<BlobAndCellProofs> constructBlobAndCellProofsList(
      final List<DataColumnSidecar> sidecars, final List<List<KZGProof>> proofs) {
    final DataColumnSidecar firstSidecar = sidecars.getFirst();
    final int blobCount = firstSidecar.getColumn().size();
    final List<BlobAndCellProofs> blobAndCellProofsList = new ArrayList<>();
    for (int i = 0; i < blobCount; i++) {
      final int blobIndex = i;
      final Bytes blob =
          sidecars.stream()
              .map(sidecar -> sidecar.getColumn().get(blobIndex).getBytes())
              .reduce(Bytes.EMPTY, Bytes::concatenate);
      final List<KZGProof> blobProofs = new ArrayList<>();
      for (int j = 0; j < halfColumns; j++) {
        blobProofs.add(sidecars.get(j).getKzgProofs().get(blobIndex).getKZGProof());
      }
      for (int j = 0; j < halfColumns; j++) {
        blobProofs.add(proofs.get(j).get(blobIndex));
      }

      final BlobSchema blobSchema =
          SchemaDefinitionsFulu.required(spec.atSlot(firstSidecar.getSlot()).getSchemaDefinitions())
              .getBlobSchema();
      final BlobAndCellProofs blobAndCellProofs =
          new BlobAndCellProofs(blobSchema.create(blob), blobProofs);
      blobAndCellProofsList.add(blobAndCellProofs);
    }

    return blobAndCellProofsList;
  }

  private ReconstructionResult emptyResult() {
    return new ReconstructionResult(
        Stream.iterate(UInt64.valueOf(halfColumns), UInt64::increment)
            .limit(halfColumns)
            .collect(Collectors.toMap(index -> index, __ -> Optional.empty())));
  }

  @Override
  public boolean isSidecarPruned(final UInt64 slot, final UInt64 index) {
    if (!isSuperNodeSupplier.get()) {
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
    final Map<SlotAndBlockRoot, SafeFuture<ReconstructionResult>> removed =
        recoveryTasks.remove(requestId);
    if (removed != null) {
      LOG.debug("Request completed: {}, removing tasks ()", requestId);
    }
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    if (!isSuperNodeSupplier.get()) {
      return;
    }
    final UInt64 finalizedEpoch = checkpoint.getEpoch();
    final UInt64 currentEpoch = spec.getCurrentEpoch(chainDataClient.getStore());

    final UInt64 pruningBoundaryEpoch =
        finalizedEpoch.min(currentEpoch.minusMinZero(dataColumnSidecarExtensionRetentionEpochs));
    if (pruningBoundaryEpoch.isZero()) {
      return;
    }
    final UInt64 lastPrunableSlot =
        spec.computeStartSlotAtEpoch(pruningBoundaryEpoch).minusMinZero(1);
    sidecarArchivePrunableChannel.onSidecarArchivePrunableSlot(lastPrunableSlot);
  }

  private int getNextTaskId() {
    final int nextTaskId = this.nextTaskId.getAndIncrement();
    if (this.nextTaskId.get() > (Integer.MAX_VALUE - 1000)) {
      this.nextTaskId.set(0);
    }
    return nextTaskId;
  }

  record ReconstructionResult(Map<UInt64, Optional<DataColumnSidecar>> sidecars) {
    Optional<DataColumnSidecar> get(final UInt64 index) {
      return Optional.ofNullable(sidecars.get(index)).flatMap(v -> v);
    }
  }
}
