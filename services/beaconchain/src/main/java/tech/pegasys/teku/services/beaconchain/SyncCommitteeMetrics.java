/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.services.beaconchain;

import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncCommitteeMetrics {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final RecentChainData recentChainData;

  private final SettableGauge previousLiveSyncCommittee;

  private UInt64 lastProcessedEpoch = UInt64.ZERO;

  public SyncCommitteeMetrics(
      final Spec spec, final RecentChainData recentChainData, final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.recentChainData = recentChainData;

    previousLiveSyncCommittee =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "previous_live_synccommittee",
            "Number of sync committee participant signatures that were included on chain during previous epoch");
  }

  public void updateSyncCommitteeMetrics(final UInt64 slot, final StateAndBlockSummary chainHead) {
    final UInt64 previousEpoch = spec.computeEpochAtSlot(slot).minusMinZero(1);
    final UInt64 previousEpochStartSlot = spec.computeStartSlotAtEpoch(previousEpoch);
    if (previousEpoch.isLessThanOrEqualTo(lastProcessedEpoch)
        || chainHead.getState().toVersionAltair().isEmpty()
        || chainHead.getSlot().isLessThan(previousEpochStartSlot)) {
      return;
    }

    SafeFuture.collectAll(
            UInt64.range(
                    previousEpochStartSlot.min(chainHead.getSlot()),
                    spec.computeStartSlotAtEpoch(previousEpoch.plus(1)).min(chainHead.getSlot()))
                .map(slotInEpoch -> getBlockAtSlotExact(chainHead, slotInEpoch)))
        .finish(
            this::updateSyncCommitteeMetrics,
            error ->
                LOG.warn(
                    "Unable to update sync committee metrics for epoch {}", previousEpoch, error));
    lastProcessedEpoch = previousEpoch;
  }

  private SafeFuture<Optional<BeaconBlock>> getBlockAtSlotExact(
      final StateAndBlockSummary chainHead, final UInt64 slotInEpoch) {
    return recentChainData
        .retrieveBlockByRoot(spec.getBlockRootAtSlot(chainHead.getState(), slotInEpoch))
        .thenApply(maybeBlock -> maybeBlock.filter(block -> block.getSlot().equals(slotInEpoch)));
  }

  private void updateSyncCommitteeMetrics(final List<Optional<BeaconBlock>> blocks) {
    final int totalIncludedSignatures =
        blocks.stream()
            .flatMap(Optional::stream)
            .flatMap(block -> block.getBody().toVersionAltair().stream())
            .mapToInt(
                blockBody -> blockBody.getSyncAggregate().getSyncCommitteeBits().getBitCount())
            .sum();
    previousLiveSyncCommittee.set(totalIncludedSignatures);
  }
}
