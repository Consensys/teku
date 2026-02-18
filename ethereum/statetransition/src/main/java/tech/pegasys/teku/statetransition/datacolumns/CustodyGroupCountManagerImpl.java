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

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;
import tech.pegasys.teku.statetransition.forkchoice.PreparedProposerInfo;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class CustodyGroupCountManagerImpl implements SlotEventsChannel, CustodyGroupCountManager {
  private static final Logger LOG = LogManager.getLogger();
  private static final int INITIAL_VALUE = -1;
  private final AtomicInteger custodyGroupCount = new AtomicInteger(INITIAL_VALUE);
  private final AtomicInteger custodyGroupSyncedCount;
  private final Spec spec;
  private final MiscHelpersFulu miscHelpersFulu;
  private final ProposersDataManager proposersDataManager;
  private final CustodyGroupCountChannel custodyGroupCountChannel;
  private final CombinedChainDataClient combinedChainDataClient;
  private final UInt256 nodeId;
  private final SettableGauge custodyGroupCountGauge;
  private final SettableGauge custodyGroupSyncedCountGauge;
  private boolean isMaxCustodyGroups = false;

  private volatile boolean genesisInitialized = false;

  public CustodyGroupCountManagerImpl(
      final Spec spec,
      final ProposersDataManager proposersDataManager,
      final CustodyGroupCountChannel custodyGroupCountChannel,
      final CombinedChainDataClient combinedChainDataClient,
      final int initCustodyGroupCount,
      final UInt256 nodeId,
      final MetricsSystem metricsSystem) {
    this(
        spec,
        MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers()),
        proposersDataManager,
        custodyGroupCountChannel,
        combinedChainDataClient,
        initCustodyGroupCount,
        nodeId,
        metricsSystem);
  }

  @VisibleForTesting
  CustodyGroupCountManagerImpl(
      final Spec spec,
      final MiscHelpersFulu miscHelpersFulu,
      final ProposersDataManager proposersDataManager,
      final CustodyGroupCountChannel custodyGroupCountChannel,
      final CombinedChainDataClient combinedChainDataClient,
      final int initCustodyGroupCount,
      final UInt256 nodeId,
      final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.miscHelpersFulu = miscHelpersFulu;
    this.custodyGroupCountGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "custody_groups",
            "Total number of custody groups within a node");
    this.custodyGroupSyncedCountGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "custody_groups_backfilled",
            "Total number of custody groups backfilled by a node");
    this.proposersDataManager = proposersDataManager;
    this.combinedChainDataClient = combinedChainDataClient;
    this.custodyGroupCountChannel = custodyGroupCountChannel;
    this.custodyGroupSyncedCount = new AtomicInteger(0);
    final Optional<Integer> maybeCustodyCount =
        combinedChainDataClient.getCustodyGroupCount().map(UInt64::intValue);
    if (maybeCustodyCount.isEmpty() || maybeCustodyCount.get() < initCustodyGroupCount) {
      updateCustodyGroupCount(initCustodyGroupCount, maybeCustodyCount);
    } else {
      LOG.info("Using custody group count {} from store", maybeCustodyCount.get());
      updateCustodyGroupCount(maybeCustodyCount.get(), maybeCustodyCount);
    }

    this.nodeId = nodeId;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    if (isMaxCustodyGroups) {
      return;
    }

    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    final Map<UInt64, PreparedProposerInfo> preparedValidators =
        proposersDataManager.getPreparedProposerInfo();

    if (detectGenesisInitialization(currentEpoch, preparedValidators)) {
      return;
    }

    final UInt64 startSlotAtEpoch = spec.computeStartSlotAtEpoch(currentEpoch);
    if (!startSlotAtEpoch.equals(slot) || preparedValidators.isEmpty()) {
      return;
    }

    computeAndUpdateCustodyGroupCount(preparedValidators)
        .finish(
            error ->
                LOG.error(
                    "Failed to compute custody group count for epoch {}", currentEpoch, error));
  }

  private boolean detectGenesisInitialization(
      final UInt64 currentEpoch, final Map<UInt64, PreparedProposerInfo> preparedValidators) {
    if (genesisInitialized) {
      return false;
    }

    if (!currentEpoch.isZero()) {
      genesisInitialized = true;
      return false;
    }

    if (preparedValidators.isEmpty()) {
      return false;
    }

    genesisInitialized = true;

    LOG.debug("Validators at genesis epoch detected, initializing custody group count.");
    computeAndUpdateCustodyGroupCount(preparedValidators)
        .thenAccept(
            maybeCustodyGroupCountUpdated -> {
              if (maybeCustodyGroupCountUpdated.isPresent()) {
                setCustodyGroupSyncedCount(maybeCustodyGroupCountUpdated.get());
                LOG.info(
                    "Custody group count updated to {}, because genesis validators were found.",
                    maybeCustodyGroupCountUpdated.get());
              }
            })
        .finish(
            error ->
                LOG.error(
                    "Failed to compute custody group count for epoch {}", currentEpoch, error));
    return true;
  }

  SafeFuture<Optional<Integer>> computeAndUpdateCustodyGroupCount(
      final Map<UInt64, PreparedProposerInfo> preparedProposerInfo) {
    final Optional<Integer> maybeCustodyGroupCount =
        combinedChainDataClient.getCustodyGroupCount().map(UInt64::intValue);
    return combinedChainDataClient
        .getBestFinalizedState()
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                return Optional.empty();
              }

              final int computedCustody =
                  miscHelpersFulu
                      .getValidatorsCustodyRequirement(
                          maybeState.get(), preparedProposerInfo.keySet())
                      .intValue();
              final int custodyGroupCount =
                  maybeCustodyGroupCount
                      .map(integer -> Math.max(computedCustody, integer))
                      .orElse(computedCustody);
              updateCustodyGroupCount(custodyGroupCount, maybeCustodyGroupCount);

              return Optional.of(custodyGroupCount);
            });
  }

  @Override
  public int getCustodyGroupCount() {
    return custodyGroupCount.get();
  }

  @Override
  public List<UInt64> getCustodyColumnIndices() {
    return miscHelpersFulu.computeCustodyColumnIndices(nodeId, getCustodyGroupCount());
  }

  @Override
  public int getSamplingGroupCount() {
    return miscHelpersFulu.getSamplingGroupCount(custodyGroupCount.get());
  }

  @Override
  public List<UInt64> getSamplingColumnIndices() {
    return miscHelpersFulu.computeCustodyColumnIndices(nodeId, getSamplingGroupCount());
  }

  @Override
  public int getCustodyGroupSyncedCount() {
    return custodyGroupSyncedCount.get();
  }

  @Override
  public void setCustodyGroupSyncedCount(final int custodyGroupSyncedCount) {
    final int oldCustodyGroupSyncedCount =
        this.custodyGroupSyncedCount.getAndSet(custodyGroupSyncedCount);
    if (oldCustodyGroupSyncedCount == custodyGroupSyncedCount) {
      return;
    }
    LOG.debug("Synced custody group count updated to {}.", custodyGroupSyncedCount);
    custodyGroupCountChannel.onCustodyGroupCountSynced(custodyGroupSyncedCount);
    custodyGroupSyncedCountGauge.set(custodyGroupSyncedCount);
  }

  private void updateCustodyGroupCount(
      final int newCustodyGroupCount, final Optional<Integer> maybeCustodyGroupCount) {
    if (maybeCustodyGroupCount.isEmpty() || maybeCustodyGroupCount.get() < newCustodyGroupCount) {
      LOG.info(
          "Persisting custody group count of {} (old value: {}).",
          newCustodyGroupCount,
          maybeCustodyGroupCount.map(Object::toString).orElse("<not set>"));
      combinedChainDataClient.updateCustodyGroupCount(newCustodyGroupCount);
    }
    final int oldValue = custodyGroupCount.getAndSet(newCustodyGroupCount);
    if (oldValue == INITIAL_VALUE) {
      setCustodyGroupSyncedCount(newCustodyGroupCount);
    }
    custodyGroupCountChannel.onGroupCountUpdate(newCustodyGroupCount, getSamplingGroupCount());
    custodyGroupCountGauge.set(newCustodyGroupCount);
    isMaxCustodyGroups = miscHelpersFulu.isSuperNode(newCustodyGroupCount);
  }
}
