/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.statetransition.datacolumns.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil.getRootCauseMessage;
import static tech.pegasys.teku.statetransition.blobs.RemoteOrigin.LOCAL_PROPOSAL;
import static tech.pegasys.teku.statetransition.blobs.RemoteOrigin.RECOVERED;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarELRecoveryManager;
import tech.pegasys.teku.statetransition.util.AbstractIgnoringFutureHistoricalSlot;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DataColumnSidecarELRecoveryManagerImpl extends AbstractIgnoringFutureHistoricalSlot
    implements DataColumnSidecarELRecoveryManager {
  private static final Logger LOG = LogManager.getLogger();
  public static final BiFunction<MetricsSystem, TimeProvider, MetricsHistogram>
      DATA_COLUMN_SIDECAR_COMPUTATION_HISTOGRAM =
          (metricsSystem, timeProvider) ->
              new MetricsHistogram(
                  metricsSystem,
                  timeProvider,
                  TekuMetricCategory.BEACON,
                  "data_column_sidecar_computation_seconds",
                  "Time taken to compute data column sidecar, including cells and inclusion proof (histogram)",
                  new double[] {
                    0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5,
                    5.0, 7.5, 10.0
                  });

  private final ConcurrentSkipListMap<SlotAndBlockRoot, RecoveryTask> recoveryTasks =
      new ConcurrentSkipListMap<>();
  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final ExecutionLayerChannel executionLayer;
  private final int maxTrackers;
  private final Consumer<List<DataColumnSidecar>> dataColumnSidecarPublisher;
  private final CustodyGroupCountManager custodyGroupCountManager;
  private final KZG kzg;

  private final MetricsHistogram dataColumnSidecarComputationTimeSeconds;
  private final Counter getBlobsV2RequestsCounter;
  private final Counter getBlobsV2ResponsesCounter;
  private final MetricsHistogram getBlobsV2RuntimeSeconds;

  private final Supplier<MiscHelpersFulu> miscHelpersFuluSupplier;

  @VisibleForTesting
  public DataColumnSidecarELRecoveryManagerImpl(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final UInt64 historicalSlotTolerance,
      final UInt64 futureSlotTolerance,
      final int maxTrackers,
      final KZG kzg,
      final Consumer<List<DataColumnSidecar>> dataColumnSidecarPublisher,
      final CustodyGroupCountManager custodyGroupCountManager,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider) {
    super(spec, futureSlotTolerance, historicalSlotTolerance);
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.executionLayer = executionLayer;
    this.maxTrackers = maxTrackers;
    this.kzg = kzg;
    this.dataColumnSidecarPublisher = dataColumnSidecarPublisher;
    this.custodyGroupCountManager = custodyGroupCountManager;
    this.dataColumnSidecarComputationTimeSeconds =
        DATA_COLUMN_SIDECAR_COMPUTATION_HISTOGRAM.apply(metricsSystem, timeProvider);
    this.getBlobsV2RequestsCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "engine_getBlobsV2_requests_total",
            "Total number of engine_getBlobsV2 requests sent");
    this.getBlobsV2ResponsesCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "engine_getBlobsV2_responses_total",
            "Total number of engine_getBlobsV2 successful responses received");
    this.getBlobsV2RuntimeSeconds =
        new MetricsHistogram(
            metricsSystem,
            timeProvider,
            TekuMetricCategory.BEACON,
            "engine_getBlobsV2_request_duration_seconds",
            "Duration of engine_getBlobsV2 requests",
            new double[] {0.01, 0.05, 0.1, 0.25, 0.5, 0.75, 1.0, 2.0, 5.0, 10.0});
    this.miscHelpersFuluSupplier =
        () -> MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
  }

  @Override
  public void onNewDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar, final RemoteOrigin remoteOrigin) {
    LOG.debug(
        "Received data column sidecar {} from {}", dataColumnSidecar.toLogString(), remoteOrigin);
    if (spec.atSlot(dataColumnSidecar.getSlot()).getMilestone().isLessThan(SpecMilestone.FULU)) {
      LOG.debug(
          "Received data column sidecar {} before FULU. Ignoring.",
          dataColumnSidecar.toLogString());
      return;
    }
    if (recentChainData.containsBlock(dataColumnSidecar.getBlockRoot())) {
      LOG.debug(
          "Data column sidecar {} is from already imported block. Ignoring.",
          dataColumnSidecar.toLogString());
      return;
    }
    if (shouldIgnoreItemAtSlot(dataColumnSidecar.getSlot())) {
      LOG.debug(
          "Data column sidecar {} is too old or from the future. Ignoring.",
          dataColumnSidecar.toLogString());
      return;
    }

    final SlotAndBlockRoot slotAndBlockRoot = dataColumnSidecar.getSlotAndBlockRoot();

    if (createRecoveryTaskFromDataColumnSidecar(dataColumnSidecar)) {
      onFirstSeen(slotAndBlockRoot, Optional.of(remoteOrigin));
    }
  }

  private boolean createRecoveryTaskFromDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar) {
    if (recoveryTasks.containsKey(dataColumnSidecar.getSlotAndBlockRoot())) {
      return false;
    }
    if (!dataColumnSidecar.getSlot().equals(getCurrentSlot())) {
      return false;
    }
    makeRoomForNewTracker();
    return recoveryTasks.putIfAbsent(
            dataColumnSidecar.getSlotAndBlockRoot(),
            new RecoveryTask(
                dataColumnSidecar.getSignedBeaconBlockHeader(),
                dataColumnSidecar.getSszKZGCommitments(),
                dataColumnSidecar.getKzgCommitmentsInclusionProof().asListUnboxed()))
        == null;
  }

  private boolean createRecoveryTaskFromBlock(final SignedBeaconBlock block) {
    if (recoveryTasks.containsKey(block.getSlotAndBlockRoot())) {
      return false;
    }
    if (!block.getSlot().equals(getCurrentSlot())) {
      return false;
    }
    makeRoomForNewTracker();
    final BeaconBlockBodyDeneb blockBodyDeneb =
        BeaconBlockBodyDeneb.required(block.getMessage().getBody());
    return recoveryTasks.putIfAbsent(
            block.getSlotAndBlockRoot(),
            new RecoveryTask(
                block.asHeader(),
                blockBodyDeneb.getBlobKzgCommitments(),
                miscHelpersFuluSupplier
                    .get()
                    .computeDataColumnKzgCommitmentsInclusionProof(blockBodyDeneb)))
        == null;
  }

  private void publishRecoveredDataColumnSidecars(
      final RecoveryTask recoveryTask, final List<BlobAndCellProofs> blobAndCellProofs) {
    final List<DataColumnSidecar> dataColumnSidecars;
    try (MetricsHistogram.Timer ignored = dataColumnSidecarComputationTimeSeconds.startTimer()) {
      dataColumnSidecars =
          miscHelpersFuluSupplier
              .get()
              .constructDataColumnSidecars(
                  recoveryTask.signedBeaconBlockHeader(),
                  recoveryTask.sszKZGCommitments(),
                  recoveryTask.kzgCommitmentsInclusionProof(),
                  blobAndCellProofs,
                  kzg);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
    final int custodyCount = custodyGroupCountManager.getCustodyGroupCount();
    final int maxCustodyGroups =
        SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig())
            .getNumberOfCustodyGroups();
    final List<DataColumnSidecar> myCustodySidecars;
    if (custodyCount == maxCustodyGroups) {
      myCustodySidecars = dataColumnSidecars;
    } else {
      final Set<UInt64> myCustodyIndices =
          new HashSet<>(custodyGroupCountManager.getCustodyColumnIndices());
      myCustodySidecars =
          dataColumnSidecars.stream()
              .filter(sidecar -> myCustodyIndices.contains(sidecar.getIndex()))
              .toList();
    }

    LOG.debug(
        "Publishing {} data column sidecars for {}",
        myCustodySidecars.size(),
        recoveryTask.getSlotAndBlockRoot());
    dataColumnSidecarPublisher.accept(myCustodySidecars);
  }

  @Override
  public void onNewBlock(final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {
    LOG.debug("Received block {} from {}", block.getSlotAndBlockRoot(), remoteOrigin);
    if (spec.atSlot(block.getSlot()).getMilestone().isLessThan(SpecMilestone.FULU)) {
      LOG.debug("Received block {} before FULU. Ignoring.", block.getSlotAndBlockRoot());
      return;
    }
    if (recentChainData.containsBlock(block.getRoot())) {
      LOG.debug("Block {} is already imported. Ignoring.", block.getSlotAndBlockRoot());
      return;
    }
    if (shouldIgnoreItemAtSlot(block.getSlot())) {
      LOG.debug("Block {} is too old or from the future. Ignoring.", block.getSlotAndBlockRoot());
      return;
    }
    if (createRecoveryTaskFromBlock(block)) {
      onFirstSeen(block.getSlotAndBlockRoot(), remoteOrigin);
    }
  }

  private void removeAllForBlock(final SlotAndBlockRoot slotAndBlockRoot) {
    recoveryTasks.remove(slotAndBlockRoot);
  }

  @VisibleForTesting
  RecoveryTask getRecoveryTask(final SlotAndBlockRoot slotAndBlockRoot) {
    return recoveryTasks.get(slotAndBlockRoot);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    super.onSlot(slot);
    LOG.trace(
        "Recovery tasks: {}",
        () -> {
          final HashMap<SlotAndBlockRoot, RecoveryTask> recoveryTasksCopy =
              new HashMap<>(recoveryTasks);
          return recoveryTasksCopy.toString();
        });
  }

  @VisibleForTesting
  @Override
  protected void prune(final UInt64 slotLimit) {
    SlotAndBlockRoot toRemove = recoveryTasks.keySet().pollFirst();
    while (toRemove != null && toRemove.getSlot().isLessThanOrEqualTo(slotLimit)) {
      removeAllForBlock(toRemove);
      toRemove = recoveryTasks.keySet().pollFirst();
    }
  }

  private void makeRoomForNewTracker() {
    while (recoveryTasks.size() > maxTrackers - 1) {
      final SlotAndBlockRoot toRemove = recoveryTasks.keySet().pollFirst();
      if (toRemove == null) {
        break;
      }
      removeAllForBlock(toRemove);
    }
  }

  private void onFirstSeen(
      final SlotAndBlockRoot slotAndBlockRoot, final Optional<RemoteOrigin> remoteOrigin) {
    LOG.debug(
        "Data from {} is first seen, checking if recovery is needed",
        slotAndBlockRoot.getBlockRoot());
    final boolean isLocalBlockProductionOrRecovered =
        remoteOrigin.map(ro -> Set.of(LOCAL_PROPOSAL, RECOVERED).contains(ro)).orElse(false);
    if (isLocalBlockProductionOrRecovered) {
      LOG.debug(
          "Data from {} is produced by {}. Recovery is not needed.",
          slotAndBlockRoot.getBlockRoot(),
          remoteOrigin);
      return;
    }

    asyncRunner
        .runAsync(
            () ->
                // fetch blobs from EL with no delay
                fetchMissingBlobsFromLocalEL(slotAndBlockRoot))
        .handleException(this::logLocalElBlobsLookupFailure)
        .ifExceptionGetsHereRaiseABug();
  }

  private SafeFuture<Void> fetchMissingBlobsFromLocalEL(final SlotAndBlockRoot slotAndBlockRoot) {
    final RecoveryTask recoveryTask = recoveryTasks.get(slotAndBlockRoot);

    if (recoveryTask == null) {
      return SafeFuture.COMPLETE;
    }
    if (recoveryTask.sszKZGCommitments().isEmpty()) {
      return SafeFuture.COMPLETE;
    }

    final List<BlobIdentifier> missingBlobsIdentifiers =
        IntStream.range(0, recoveryTask.sszKZGCommitments().size())
            .mapToObj(
                index -> new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.valueOf(index)))
            .toList();

    final SpecVersion specVersion = spec.atSlot(slotAndBlockRoot.getSlot());
    final MiscHelpersDeneb miscHelpersDeneb =
        specVersion.miscHelpers().toVersionDeneb().orElseThrow();
    final SszList<SszKZGCommitment> sszKZGCommitments = recoveryTask.sszKZGCommitments();

    final List<VersionedHash> versionedHashes =
        missingBlobsIdentifiers.stream()
            .map(
                blobIdentifier ->
                    miscHelpersDeneb.kzgCommitmentToVersionedHash(
                        sszKZGCommitments
                            .get(blobIdentifier.getIndex().intValue())
                            .getKZGCommitment()))
            .toList();

    getBlobsV2RequestsCounter.inc();
    final MetricsHistogram.Timer timer = getBlobsV2RuntimeSeconds.startTimer();

    return executionLayer
        .engineGetBlobAndCellProofsList(versionedHashes, slotAndBlockRoot.getSlot())
        .whenComplete((result, error) -> timer.closeUnchecked().run())
        .thenAccept(
            blobAndCellProofsList -> {
              LOG.debug("Found {} blobs", blobAndCellProofsList.size());
              if (blobAndCellProofsList.isEmpty()) {
                LOG.debug(
                    "Blobs for {} are not found on local EL, reconstruction is not possible",
                    slotAndBlockRoot);
                return;
              }

              checkArgument(
                  blobAndCellProofsList.size() == versionedHashes.size(),
                  "Queried %s versionedHashed but got %s blobAndProofs",
                  versionedHashes.size(),
                  blobAndCellProofsList.size());

              getBlobsV2ResponsesCounter.inc();
              LOG.debug(
                  "Collected all blobSidecars from EL for slot {}, recovering data column sidecars",
                  slotAndBlockRoot.getSlot());
              publishRecoveredDataColumnSidecars(recoveryTask, blobAndCellProofsList);
            });
  }

  private void logLocalElBlobsLookupFailure(final Throwable error) {
    LOG.debug("Local EL blobs lookup failed: {}", getRootCauseMessage(error));
  }

  record RecoveryTask(
      SignedBeaconBlockHeader signedBeaconBlockHeader,
      SszList<SszKZGCommitment> sszKZGCommitments,
      List<Bytes32> kzgCommitmentsInclusionProof) {
    public SlotAndBlockRoot getSlotAndBlockRoot() {
      return signedBeaconBlockHeader.getMessage().getSlotAndBlockRoot();
    }
  }
}
