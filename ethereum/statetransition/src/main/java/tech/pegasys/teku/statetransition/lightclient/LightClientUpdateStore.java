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

  // TODO: bound this once updates are persisted
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
    bestUpdatesByPeriod.merge(
        syncCommitteePeriodAtSlot(update.getAttestedHeader().getBeacon().getSlot()),
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

          return isBetterFinalityUpdate(finalityUpdate, currentFinalityUpdate.get())
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

          return isBetterOptimisticUpdate(optimisticUpdate, currentOptimisticUpdate.get())
              ? Optional.of(optimisticUpdate)
              : currentOptimisticUpdate;
        });
  }

  public List<LightClientUpdate> getBestUpdatesInRange(final UInt64 startPeriod, final int count) {
    return bestUpdatesByPeriod.tailMap(startPeriod, true).entrySet().stream()
        .takeWhile(entry -> entry.getKey().minus(startPeriod).isLessThan(count))
        .map(Map.Entry::getValue)
        .toList();
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

  private boolean isBetterFinalityUpdate(
      final LightClientFinalityUpdate newUpdate, final LightClientFinalityUpdate oldUpdate) {
    final UInt64 newFinalizedSlot = newUpdate.getFinalizedHeader().getBeacon().getSlot();
    final UInt64 oldFinalizedSlot = oldUpdate.getFinalizedHeader().getBeacon().getSlot();

    if (!newFinalizedSlot.equals(oldFinalizedSlot)) {
      return newFinalizedSlot.isGreaterThan(oldFinalizedSlot);
    }

    return newUpdate
        .getAttestedHeader()
        .getBeacon()
        .getSlot()
        .isGreaterThan(oldUpdate.getAttestedHeader().getBeacon().getSlot());
  }

  private boolean isBetterOptimisticUpdate(
      final LightClientOptimisticUpdate newUpdate, final LightClientOptimisticUpdate oldUpdate) {
    return newUpdate
        .getAttestedHeader()
        .getBeacon()
        .getSlot()
        .isGreaterThan(oldUpdate.getAttestedHeader().getBeacon().getSlot());
  }

  private UInt64 syncCommitteePeriodAtSlot(final UInt64 slot) {
    return spec.getSyncCommitteeUtilRequired(slot)
        .computeSyncCommitteePeriod(spec.computeEpochAtSlot(slot));
  }
}
