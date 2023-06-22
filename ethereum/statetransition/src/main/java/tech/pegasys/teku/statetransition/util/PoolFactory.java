/*
 * Copyright ConsenSys Software Inc., 2022
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
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackerFactory;
import tech.pegasys.teku.storage.client.RecentChainData;

public class PoolFactory {

  private static final UInt64 DEFAULT_HISTORICAL_SLOT_TOLERANCE = UInt64.valueOf(320);
  // should fit attestations for a slot given validator set size
  // so DEFAULT_MAX_ATTESTATIONS * slots_per_epoch should be >= validator set size ideally
  // on all subnets, you may receive and have to cache that number of messages
  private static final int DEFAULT_MAX_ATTESTATIONS = 30_000;
  private static final int DEFAULT_MAX_BLOCKS = 5000;

  private final SettableLabelledGauge pendingPoolsSizeGauge;
  private final SettableLabelledGauge blobSidecarPoolSizeGauge;

  public PoolFactory(final MetricsSystem metricsSystem) {
    this.pendingPoolsSizeGauge =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "pending_pool_size",
            "Number of items in pending pool",
            "type");

    this.blobSidecarPoolSizeGauge =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "blobs_pool_size",
            "Number of items in blobs pool",
            "type");
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

  public PendingPool<ValidatableAttestation> createPendingPoolForAttestations(final Spec spec) {
    return new PendingPool<>(
        pendingPoolsSizeGauge,
        "attestations",
        spec,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        DEFAULT_MAX_ATTESTATIONS,
        ValidatableAttestation::hashTreeRoot,
        ValidatableAttestation::getDependentBlockRoots,
        ValidatableAttestation::getEarliestSlotForForkChoiceProcessing);
  }

  public BlobSidecarPoolImpl createPoolForBlobSidecars(
      final Spec spec,
      final TimeProvider timeProvider,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData) {
    return createPoolForBlobSidecars(
        spec,
        timeProvider,
        asyncRunner,
        recentChainData,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        DEFAULT_MAX_BLOCKS);
  }

  public BlobSidecarPoolImpl createPoolForBlobSidecars(
      final Spec spec,
      final TimeProvider timeProvider,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final UInt64 historicalBlockTolerance,
      final UInt64 futureBlockTolerance,
      final int maxTrackers) {
    return new BlobSidecarPoolImpl(
        blobSidecarPoolSizeGauge,
        spec,
        timeProvider,
        asyncRunner,
        recentChainData,
        historicalBlockTolerance,
        futureBlockTolerance,
        maxTrackers);
  }

  @VisibleForTesting
  BlobSidecarPoolImpl createPoolForBlobSidecars(
      final Spec spec,
      final TimeProvider timeProvider,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final UInt64 historicalBlockTolerance,
      final UInt64 futureBlockTolerance,
      final int maxItems,
      final BlockBlobSidecarsTrackerFactory trackerFactory) {
    return new BlobSidecarPoolImpl(
        pendingPoolsSizeGauge,
        spec,
        timeProvider,
        asyncRunner,
        recentChainData,
        historicalBlockTolerance,
        futureBlockTolerance,
        maxItems,
        trackerFactory);
  }
}
