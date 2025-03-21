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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;
import tech.pegasys.teku.statetransition.forkchoice.PreparedProposerInfo;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class CustodyGroupCountManagerImpl implements SlotEventsChannel, CustodyGroupCountManager {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final int initCustodyGroupCount;
  private final AtomicInteger custodyGroupCount;
  private final AtomicInteger custodyGroupSyncedCount;
  private final Spec spec;
  private final SpecConfigFulu specConfigFulu;
  private final MiscHelpersFulu miscHelpersFulu;
  private final ProposersDataManager proposersDataManager;
  private final CustodyGroupCountChannel custodyGroupCountChannel;
  private final CombinedChainDataClient combinedChainDataClient;

  private UInt64 lastEpoch = UInt64.MAX_VALUE;

  public CustodyGroupCountManagerImpl(
      final Spec spec,
      final SpecConfigFulu specConfigFulu,
      final ProposersDataManager proposersDataManager,
      final CustodyGroupCountChannel custodyGroupCountChannel,
      final CombinedChainDataClient combinedChainDataClient,
      final int initCustodyGroupCount) {
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
      return;
    }

    final UInt64 baseBalance = specConfigFulu.getMinActivationBalance();
    combinedChainDataClient
        .getStateAtSlotExact(slot.safeDecrement())
        .thenAccept(
            maybeState -> {
              if (maybeState.isPresent()) {
                final long activeBases =
                    preparedProposerInfo.keySet().stream()
                        .map(
                            proposerIndex -> {
                              final Validator validator =
                                  maybeState.get().getValidators().get(proposerIndex.intValue());
                              return validator.getEffectiveBalance().dividedBy(baseBalance);
                            })
                        .mapToLong(UInt64::intValue)
                        .sum();
                final UInt64 custodyGroupCountUpdated =
                    miscHelpersFulu.calculateCustodyGroupCount(initCustodyGroupCount, activeBases);
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
  public int getCustodyGroupSyncedCount() {
    return custodyGroupSyncedCount.get();
  }

  @Override
  public void setCustodyGroupSyncedCount(final int custodyGroupSyncedCount) {
    this.custodyGroupSyncedCount.set(custodyGroupSyncedCount);
    LOG.info("Synced custody group count updated to {}.", custodyGroupSyncedCount);
    custodyGroupCountChannel.onCustodyGroupCountSynced(custodyGroupSyncedCount);
  }

  private synchronized boolean updateEpoch(final UInt64 epoch) {
    if (!lastEpoch.equals(epoch)) {
      lastEpoch = epoch;
      return true;
    }
    return false;
  }

  private synchronized void updateCustodyGroupCount(final UInt64 newCustodyGroupCount) {
    final int oldCustodyGroupCount = custodyGroupCount.getAndSet(newCustodyGroupCount.intValue());
    if (oldCustodyGroupCount != newCustodyGroupCount.intValue()) {
      LOG.info(
          "Custody group count updated from {} to {}.",
          oldCustodyGroupCount,
          newCustodyGroupCount.intValue());
      custodyGroupCountChannel.onCustodyGroupCountUpdate(newCustodyGroupCount.intValue());
    }
  }
}
