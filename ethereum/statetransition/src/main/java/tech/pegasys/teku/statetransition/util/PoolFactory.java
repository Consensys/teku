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

import java.util.Collections;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.storage.client.RecentChainData;

public class PoolFactory {

  private static final UInt64 DEFAULT_HISTORICAL_SLOT_TOLERANCE = UInt64.valueOf(320);
  private static final int DEFAULT_MAX_ITEMS = 5000;
  private static final int DEFAULT_MAX_BLOCKS = 5000;
  private final SettableLabelledGauge sizeGauge;

  public PoolFactory(final MetricsSystem metricsSystem) {
    this.sizeGauge =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "pending_pool_size",
            "Number of items in pending pool",
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
        sizeGauge,
        "blocks",
        spec,
        historicalBlockTolerance,
        futureBlockTolerance,
        maxItems,
        block -> block.getMessage().hashTreeRoot(),
        block -> Collections.singleton(block.getParentRoot()),
        SignedBeaconBlock::getSlot);
  }

  public PendingPool<ValidateableAttestation> createPendingPoolForAttestations(final Spec spec) {
    return new PendingPool<>(
        sizeGauge,
        "attestations",
        spec,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        DEFAULT_MAX_ITEMS,
        ValidateableAttestation::hashTreeRoot,
        ValidateableAttestation::getDependentBlockRoots,
        ValidateableAttestation::getEarliestSlotForForkChoiceProcessing);
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
      final int maxItems) {
    return new BlobSidecarPoolImpl(
        sizeGauge,
        spec,
        timeProvider,
        asyncRunner,
        recentChainData,
        historicalBlockTolerance,
        futureBlockTolerance,
        maxItems);
  }
}
