/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.statetransition.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackerFactory;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarELRecoveryManager;
import tech.pegasys.teku.statetransition.datacolumns.util.DataColumnSidecarELRecoveryManagerImpl;
import tech.pegasys.teku.statetransition.validation.BlobSidecarGossipValidator;
import tech.pegasys.teku.storage.client.RecentChainData;

public class PoolFactory {

  private static final UInt64 DEFAULT_HISTORICAL_SLOT_TOLERANCE = UInt64.valueOf(320);

  private static final int DEFAULT_MAX_BLOCKS = 5000;

  private final SettableLabelledGauge pendingPoolsSizeGauge;
  private final SettableLabelledGauge blockBlobSidecarsTrackersPoolSizeGauge;
  private final LabelledMetric<Counter> blockBlobSidecarsTrackersPoolStats;

  public PoolFactory(final MetricsSystem metricsSystem) {
    this.pendingPoolsSizeGauge =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "pending_pool_size",
            "Number of items in pending pool",
            "type");

    this.blockBlobSidecarsTrackersPoolSizeGauge =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "block_blobs_trackers_pool_size",
            "Number of items in block-blobs trackers pool",
            "type");

    this.blockBlobSidecarsTrackersPoolStats =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            "block_blobs_trackers_pool_stats_total",
            "Block-blobs trackers pool statistics",
            "type",
            "subtype");
  }

  public PendingPool<SignedBeaconBlock> createPendingPoolForBlocks(final Spec spec) {
    return createPendingPoolForBlocks(
        spec,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        DEFAULT_MAX_BLOCKS);
  }

  public PendingPool<SignedBeaconBlock> createPendingPoolForBlocks(
      final Spec spec,
      final UInt64 historicalBlockTolerance,
      final UInt64 futureBlockTolerance,
      final int maxItems) {
    return new PendingPool<>(
        pendingPoolsSizeGauge,
        "blocks",
        spec,
        historicalBlockTolerance,
        futureBlockTolerance,
        maxItems,
        block -> block.getMessage().hashTreeRoot(),
        block -> Collections.singleton(block.getParentRoot()),
        SignedBeaconBlock::getSlot);
  }

  public PendingPool<ValidatableAttestation> createPendingPoolForAttestations(
      final Spec spec, final int maxQueueSize) {

    return new PendingPool<>(
        pendingPoolsSizeGauge,
        "attestations",
        spec,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        maxQueueSize,
        ValidatableAttestation::hashTreeRoot,
        ValidatableAttestation::getDependentBlockRoots,
        ValidatableAttestation::getEarliestSlotForForkChoiceProcessing);
  }

  public BlockBlobSidecarsTrackersPoolImpl createPoolForBlockBlobSidecarsTrackers(
      final BlockImportChannel blockImportChannel,
      final Spec spec,
      final TimeProvider timeProvider,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final Supplier<BlobSidecarGossipValidator> gossipValidatorSupplier,
      final Function<BlobSidecar, SafeFuture<Void>> blobSidecarGossipPublisher) {
    return createPoolForBlockBlobSidecarsTrackers(
        blockImportChannel,
        spec,
        timeProvider,
        asyncRunner,
        recentChainData,
        executionLayer,
        gossipValidatorSupplier,
        blobSidecarGossipPublisher,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        DEFAULT_MAX_BLOCKS);
  }

  // TODO: Do we need it here, in pool factory???
  public DataColumnSidecarELRecoveryManager createDataColumnSidecarELRecoveryManager(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final KZG kzg,
      final Consumer<List<DataColumnSidecar>> dataColumnSidecarPublisher,
      final CustodyGroupCountManager custodyGroupCountManager) {
    return new DataColumnSidecarELRecoveryManagerImpl(
        spec,
        asyncRunner,
        recentChainData,
        executionLayer,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        // TODO: constant
        10,
        kzg,
        dataColumnSidecarPublisher,
        custodyGroupCountManager);
  }

  public BlockBlobSidecarsTrackersPoolImpl createPoolForBlockBlobSidecarsTrackers(
      final BlockImportChannel blockImportChannel,
      final Spec spec,
      final TimeProvider timeProvider,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final Supplier<BlobSidecarGossipValidator> gossipValidatorSupplier,
      final Function<BlobSidecar, SafeFuture<Void>> blobSidecarGossipPublisher,
      final UInt64 historicalBlockTolerance,
      final UInt64 futureBlockTolerance,
      final int maxTrackers) {
    return new BlockBlobSidecarsTrackersPoolImpl(
        blockImportChannel,
        blockBlobSidecarsTrackersPoolSizeGauge,
        blockBlobSidecarsTrackersPoolStats,
        spec,
        timeProvider,
        asyncRunner,
        recentChainData,
        executionLayer,
        gossipValidatorSupplier,
        blobSidecarGossipPublisher,
        historicalBlockTolerance,
        futureBlockTolerance,
        maxTrackers);
  }

  @VisibleForTesting
  BlockBlobSidecarsTrackersPoolImpl createPoolForBlockBlobSidecarsTrackers(
      final BlockImportChannel blockImportChannel,
      final Spec spec,
      final TimeProvider timeProvider,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final Supplier<BlobSidecarGossipValidator> gossipValidatorSupplier,
      final Function<BlobSidecar, SafeFuture<Void>> blobSidecarGossipPublisher,
      final UInt64 historicalBlockTolerance,
      final UInt64 futureBlockTolerance,
      final int maxItems,
      final BlockBlobSidecarsTrackerFactory trackerFactory) {
    return new BlockBlobSidecarsTrackersPoolImpl(
        blockImportChannel,
        blockBlobSidecarsTrackersPoolSizeGauge,
        blockBlobSidecarsTrackersPoolStats,
        spec,
        timeProvider,
        asyncRunner,
        recentChainData,
        executionLayer,
        gossipValidatorSupplier,
        blobSidecarGossipPublisher,
        historicalBlockTolerance,
        futureBlockTolerance,
        maxItems,
        trackerFactory);
  }
}
