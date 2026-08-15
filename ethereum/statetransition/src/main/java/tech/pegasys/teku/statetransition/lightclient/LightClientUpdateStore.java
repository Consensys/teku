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

package tech.pegasys.teku.statetransition.lightclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;

public class LightClientUpdateStore {
  private final Spec spec;

  private final ConcurrentNavigableMap<UInt64, LightClientUpdate> bestUpdatesByPeriod =
      new ConcurrentSkipListMap<>();
  private final AtomicReference<Optional<LightClientFinalityUpdate>> latestFinalityUpdate =
      new AtomicReference<>(Optional.empty());
  private final AtomicReference<Optional<LightClientOptimisticUpdate>> latestOptimisticUpdate =
      new AtomicReference<>(Optional.empty());

  public LightClientUpdateStore(final Spec spec) {
    this.spec = spec;
  }

  public void addUpdate(final LightClientUpdate update) {
    final UInt64 attestedPeriod =
        syncCommitteePeriodAtSlot(update.getAttestedHeader().getBeacon().getSlot());
    if (!attestedPeriod.equals(syncCommitteePeriodAtSlot(update.getSignatureSlot().get()))) {
      return;
    }

    bestUpdatesByPeriod.merge(
        attestedPeriod,
        update,
        (existingUpdate, incomingUpdate) ->
            isBetterUpdate(incomingUpdate, existingUpdate) ? incomingUpdate : existingUpdate);
  }

  public void addFinalityUpdate(final LightClientFinalityUpdate finalityUpdate) {
    latestFinalityUpdate.updateAndGet(
        currentFinalityUpdate -> {
          if (currentFinalityUpdate.isEmpty()) {
            return Optional.of(finalityUpdate);
          }

          return isLaterUpdate(
                  finalityUpdate.getAttestedHeader().getBeacon().getSlot(),
                  finalityUpdate.getSignatureSlot().get(),
                  currentFinalityUpdate.get().getAttestedHeader().getBeacon().getSlot(),
                  currentFinalityUpdate.get().getSignatureSlot().get())
              ? Optional.of(finalityUpdate)
              : currentFinalityUpdate;
        });
  }

  public void addOptimisticUpdate(final LightClientOptimisticUpdate optimisticUpdate) {
    latestOptimisticUpdate.updateAndGet(
        currentOptimisticUpdate -> {
          if (currentOptimisticUpdate.isEmpty()) {
            return Optional.of(optimisticUpdate);
          }

          return isLaterUpdate(
                  optimisticUpdate.getAttestedHeader().getBeacon().getSlot(),
                  optimisticUpdate.getSignatureSlot().get(),
                  currentOptimisticUpdate.get().getAttestedHeader().getBeacon().getSlot(),
                  currentOptimisticUpdate.get().getSignatureSlot().get())
              ? Optional.of(optimisticUpdate)
              : currentOptimisticUpdate;
        });
  }

  public List<LightClientUpdate> getBestUpdatesInRange(final UInt64 startPeriod, final int count) {
    final ConcurrentNavigableMap<UInt64, LightClientUpdate> updatesInRange =
        bestUpdatesByPeriod.subMap(startPeriod, true, startPeriod.plus(count), false);
    if (updatesInRange.isEmpty()) {
      return List.of();
    }

    final UInt64 earliestPeriod = updatesInRange.firstKey();
    final List<LightClientUpdate> consecutiveUpdates = new ArrayList<>();

    for (final Map.Entry<UInt64, LightClientUpdate> entry : updatesInRange.entrySet()) {
      if (!entry.getKey().equals(earliestPeriod.plus(consecutiveUpdates.size()))) {
        break;
      }
      consecutiveUpdates.add(entry.getValue());
    }

    return consecutiveUpdates;
  }

  public void pruneUpdatesBefore(final UInt64 period) {
    bestUpdatesByPeriod.headMap(period).clear();
  }

  public Optional<LightClientFinalityUpdate> getLatestFinalityUpdate() {
    return latestFinalityUpdate.get();
  }

  public Optional<LightClientOptimisticUpdate> getLatestOptimisticUpdate() {
    return latestOptimisticUpdate.get();
  }

  /** {@code is_better_update}. */
  private boolean isBetterUpdate(
      final LightClientUpdate newUpdate, final LightClientUpdate oldUpdate) {
    final int maxActiveParticipants = newUpdate.getSyncAggregate().getSyncCommitteeBits().size();
    final int newUpdateActiveParticipants =
        newUpdate.getSyncAggregate().getSyncCommitteeBits().getBitCount();
    final int oldUpdateActiveParticipants =
        oldUpdate.getSyncAggregate().getSyncCommitteeBits().getBitCount();

    final boolean newUpdateHasSupermajority =
        newUpdateActiveParticipants * 3 >= maxActiveParticipants * 2;
    final boolean oldUpdateHasSupermajority =
        oldUpdateActiveParticipants * 3 >= maxActiveParticipants * 2;

    if (newUpdateHasSupermajority != oldUpdateHasSupermajority) {
      return newUpdateHasSupermajority;
    }

    if (!newUpdateHasSupermajority && newUpdateActiveParticipants != oldUpdateActiveParticipants) {
      return newUpdateActiveParticipants > oldUpdateActiveParticipants;
    }

    final UInt64 newUpdateSignatureSlot = newUpdate.getSignatureSlot().get();
    final UInt64 oldUpdateSignatureSlot = oldUpdate.getSignatureSlot().get();
    final UInt64 newUpdateAttestedSlot = newUpdate.getAttestedHeader().getBeacon().getSlot();
    final UInt64 oldUpdateAttestedSlot = oldUpdate.getAttestedHeader().getBeacon().getSlot();

    final boolean newUpdateHasRelevantSyncCommittee =
        isSyncCommitteeUpdate(newUpdate)
            && syncCommitteePeriodAtSlot(newUpdateAttestedSlot)
                .equals(syncCommitteePeriodAtSlot(newUpdateSignatureSlot));
    final boolean oldUpdateHasRelevantSyncCommittee =
        isSyncCommitteeUpdate(oldUpdate)
            && syncCommitteePeriodAtSlot(oldUpdateAttestedSlot)
                .equals(syncCommitteePeriodAtSlot(oldUpdateSignatureSlot));

    if (newUpdateHasRelevantSyncCommittee != oldUpdateHasRelevantSyncCommittee) {
      return newUpdateHasRelevantSyncCommittee;
    }

    final boolean newUpdateHasFinality = isFinalityUpdate(newUpdate);
    final boolean oldUpdateHasFinality = isFinalityUpdate(oldUpdate);

    if (newUpdateHasFinality != oldUpdateHasFinality) {
      return newUpdateHasFinality;
    }

    if (newUpdateHasFinality) {
      final boolean newUpdateHasSyncCommitteeFinality =
          syncCommitteePeriodAtSlot(newUpdate.getFinalizedHeader().getBeacon().getSlot())
              .equals(syncCommitteePeriodAtSlot(newUpdateAttestedSlot));
      final boolean oldUpdateHasSyncCommitteeFinality =
          syncCommitteePeriodAtSlot(oldUpdate.getFinalizedHeader().getBeacon().getSlot())
              .equals(syncCommitteePeriodAtSlot(oldUpdateAttestedSlot));

      if (newUpdateHasSyncCommitteeFinality != oldUpdateHasSyncCommitteeFinality) {
        return newUpdateHasSyncCommitteeFinality;
      }
    }

    if (newUpdateActiveParticipants != oldUpdateActiveParticipants) {
      return newUpdateActiveParticipants > oldUpdateActiveParticipants;
    }

    if (!newUpdateAttestedSlot.equals(oldUpdateAttestedSlot)) {
      return newUpdateAttestedSlot.isLessThan(oldUpdateAttestedSlot);
    }

    return newUpdateSignatureSlot.isLessThan(oldUpdateSignatureSlot);
  }

  /** {@code is_sync_committee_update}. */
  private boolean isSyncCommitteeUpdate(final LightClientUpdate update) {
    return !update.getNextSyncCommitteeBranch().isDefault();
  }

  /** {@code is_finality_update}. */
  private boolean isFinalityUpdate(final LightClientUpdate update) {
    return !update.getFinalityBranch().isDefault();
  }

  private boolean isLaterUpdate(
      final UInt64 newAttestedSlot,
      final UInt64 newSignatureSlot,
      final UInt64 oldAttestedSlot,
      final UInt64 oldSignatureSlot) {
    if (!newAttestedSlot.equals(oldAttestedSlot)) {
      return newAttestedSlot.isGreaterThan(oldAttestedSlot);
    }

    return newSignatureSlot.isGreaterThan(oldSignatureSlot);
  }

  private UInt64 syncCommitteePeriodAtSlot(final UInt64 slot) {
    return spec.getSyncCommitteeUtilRequired(slot)
        .computeSyncCommitteePeriod(spec.computeEpochAtSlot(slot));
  }
}
