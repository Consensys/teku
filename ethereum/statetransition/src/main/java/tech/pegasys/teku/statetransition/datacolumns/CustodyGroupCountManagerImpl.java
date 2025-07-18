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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;
import tech.pegasys.teku.statetransition.forkchoice.PreparedProposerInfo;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class CustodyGroupCountManagerImpl implements SlotEventsChannel, CustodyGroupCountManager {
  private static final Logger LOG = LogManager.getLogger();

  private final int initCustodyGroupCount;
  private final AtomicInteger custodyGroupCount;
  private final AtomicInteger custodyGroupSyncedCount;
  private final Spec spec;
  private final SpecConfigFulu specConfigFulu;
  private final MiscHelpersFulu miscHelpersFulu;
  private final ProposersDataManager proposersDataManager;
  private final CustodyGroupCountChannel custodyGroupCountChannel;
  private final CombinedChainDataClient combinedChainDataClient;
  private final UInt256 nodeId;
  private final SettableGauge custodyGroupCountGauge;
  private final SettableGauge custodyGroupSyncedCountGauge;

  private UInt64 lastEpoch = UInt64.MAX_VALUE;
  private boolean hasProcessedGenesisTransition = false;

  public CustodyGroupCountManagerImpl(
      final Spec spec,
      final SpecConfigFulu specConfigFulu,
      final ProposersDataManager proposersDataManager,
      final CustodyGroupCountChannel custodyGroupCountChannel,
      final CombinedChainDataClient combinedChainDataClient,
      final int initCustodyGroupCount,
      final UInt256 nodeId,
      final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.specConfigFulu = specConfigFulu;
    this.miscHelpersFulu =
        MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
    this.proposersDataManager = proposersDataManager;
    this.combinedChainDataClient = combinedChainDataClient;
    this.custodyGroupCountChannel = custodyGroupCountChannel;
    this.initCustodyGroupCount = initCustodyGroupCount;
    this.custodyGroupCount = new AtomicInteger(initCustodyGroupCount);
    this.custodyGroupSyncedCount = new AtomicInteger(0);
    this.nodeId = nodeId;
    this.custodyGroupCountGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "custody_groups",
            "Total number of custody groups within a node");
    this.custodyGroupCountGauge.set(initCustodyGroupCount);
    this.custodyGroupSyncedCountGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "custody_groups_backfilled",
            "Total number of custody groups backfilled by a node");
  }

  @Override
  public void onSlot(final UInt64 slot) {
    if (initCustodyGroupCount == specConfigFulu.getNumberOfCustodyGroups()) {
      // Supernode, we are already subscribed to all groups
      return;
    }

    if (!updateEpoch(spec.computeEpochAtSlot(slot))) {
      return;
    }

    final Map<UInt64, PreparedProposerInfo> preparedProposerInfo =
        proposersDataManager.getPreparedProposerInfo();
    if (preparedProposerInfo.isEmpty()) {
      updateCustodyGroupCount(initCustodyGroupCount);
      return;
    }

    // Check if we need to handle genesis transition
    if (!hasProcessedGenesisTransition) {
      checkAndHandleGenesisTransition();
    }

    combinedChainDataClient
        .getBestFinalizedState()
        .thenAccept(
            maybeState -> {
              if (maybeState.isPresent()) {
                final int custodyGroupCountUpdated =
                    miscHelpersFulu
                        .getValidatorsCustodyRequirement(
                            maybeState.get(), preparedProposerInfo.keySet())
                        .max(initCustodyGroupCount)
                        .intValue();
                updateCustodyGroupCount(custodyGroupCountUpdated);
              }
            })
        .ifExceptionGetsHereRaiseABug();
  }

  @Override
  public int getCustodyGroupCount() {
    return custodyGroupCount.get();
  }

  @Override
  public List<UInt64> getCustodyColumnIndices() {
    return miscHelpersFulu.computeCustodyColumnIndexes(nodeId, getCustodyGroupCount());
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

  private void checkAndHandleGenesisTransition() {
    combinedChainDataClient
        .getLatestFinalized()
        .ifPresent(
            finalizedAnchor -> {
              // Check if we've moved beyond genesis epoch (epoch 0)
              if (finalizedAnchor.getEpoch().isGreaterThan(UInt64.ZERO)) {
                // We've finalized beyond genesis, now get the genesis state to count validators
                final UInt64 genesisSlot = spec.computeStartSlotAtEpoch(UInt64.ZERO);
                combinedChainDataClient
                    .getStateAtSlotExact(genesisSlot)
                    .thenAccept(
                        maybeGenesisState -> {
                          if (maybeGenesisState.isPresent()) {
                            final int genesisValidatorCount =
                                maybeGenesisState.get().getValidators().size();
                            if (genesisValidatorCount > 0) {
                              // Calculate custody group count based on total effective balance of all genesis validators
                              final int custodyGroupCountFromGenesis =
                                  calculateCustodyGroupCountFromGenesisState(maybeGenesisState.get());
                              final int updatedCustodyGroupCount =
                                  Math.max(custodyGroupCountFromGenesis, initCustodyGroupCount);
                              updateCustodyGroupCount(updatedCustodyGroupCount);
                              LOG.info(
                                  "Updated custody group count to {} based on {} genesis validators (total effective balance based)",
                                  updatedCustodyGroupCount,
                                  genesisValidatorCount);
                            }
                          }
                          hasProcessedGenesisTransition = true;
                        })
                    .ifExceptionGetsHereRaiseABug();
              }
            });
  }

  private int calculateCustodyGroupCountFromGenesisState(final BeaconState genesisState) {
    // Calculate total effective balance of all validators in genesis state
    final UInt64 totalEffectiveBalance =
        genesisState.getValidators().stream()
            .map(validator -> validator.getEffectiveBalance())
            .reduce(UInt64.ZERO, UInt64::plus);
    
    // Use the same logic as getValidatorsCustodyRequirement but for total network balance
    final UInt64 custodyRequirement =
        totalEffectiveBalance.dividedBy(specConfigFulu.getBalancePerAdditionalCustodyGroup());
    
    return custodyRequirement
        .max(specConfigFulu.getValidatorCustodyRequirement())
        .min(specConfigFulu.getNumberOfCustodyGroups())
        .intValue();
  }

  private synchronized boolean updateEpoch(final UInt64 epoch) {
    if (!lastEpoch.equals(epoch)) {
      lastEpoch = epoch;
      return true;
    }
    return false;
  }

  private synchronized void updateCustodyGroupCount(final int newCustodyGroupCount) {
    final int oldCustodyGroupCount = custodyGroupCount.getAndSet(newCustodyGroupCount);
    if (oldCustodyGroupCount != newCustodyGroupCount) {
      LOG.debug(
          "Custody group count updated from {} to {}.", oldCustodyGroupCount, newCustodyGroupCount);
      custodyGroupCountChannel.onCustodyGroupCountUpdate(newCustodyGroupCount);
      custodyGroupCountGauge.set(newCustodyGroupCount);
    }
  }
}
