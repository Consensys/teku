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

import java.util.concurrent.ConcurrentSkipListMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;

public class LightClientUpdateStore {
  private final ConcurrentSkipListMap<UInt64, LightClientUpdate> lightClientUpdateCache =
      new ConcurrentSkipListMap<>();

  private final Spec spec;

  public LightClientUpdateStore(final Spec spec) {
    this.spec = spec;
  }

  public

  /** {@code is_better_update}. */
  private boolean isBetterUpdate(
      final LightClientUpdate newUpdate, final LightClientUpdate oldUpdate) {
    final int maxActiveParticipants = newUpdate.getSyncAggregate().getSyncCommitteeBits().size();
    final int newUpdateActiveParticipants =
        newUpdate.getSyncAggregate().getSyncCommitteeBits().getBitCount();
    final int oldUpdateActiveParticipants =
        oldUpdate.getSyncAggregate().getSyncCommitteeBits().getBitCount();

    boolean newUpdateHasSupermajority =
        newUpdateActiveParticipants * 3 >= maxActiveParticipants * 2;
    boolean oldUpdateHasSupermajority =
        oldUpdateActiveParticipants * 3 >= maxActiveParticipants * 2;

    if (newUpdateHasSupermajority != oldUpdateHasSupermajority) {
      return newUpdateHasSupermajority;
    }

    if (!newUpdateHasSupermajority && newUpdateActiveParticipants != oldUpdateActiveParticipants) {
      return newUpdateActiveParticipants > oldUpdateActiveParticipants;
    }

    UInt64 newUpdateSignatureSlot = newUpdate.getSignatureSlot().get();
    UInt64 oldUpdateSignatureSlot = oldUpdate.getSignatureSlot().get();
    UInt64 newUpdateAttestedSlot = newUpdate.getAttestedHeader().getBeacon().getSlot();
    UInt64 oldUpdateAttestedSlot = oldUpdate.getAttestedHeader().getBeacon().getSlot();

    boolean newUpdateHasRelevantSyncCommittee =
        isSyncCommitteeUpdate(newUpdate)
            && (syncCommitteePeriodAtSlot(newUpdateAttestedSlot)
                == syncCommitteePeriodAtSlot(newUpdateSignatureSlot));
    boolean oldUpdateHasRelevantSyncCommittee =
        isSyncCommitteeUpdate(oldUpdate)
            && (syncCommitteePeriodAtSlot(oldUpdateAttestedSlot)
                == syncCommitteePeriodAtSlot(oldUpdateSignatureSlot));

    if (newUpdateHasRelevantSyncCommittee != oldUpdateHasRelevantSyncCommittee) {
      return newUpdateHasRelevantSyncCommittee;
    }

    boolean newUpdateHasFinality = isFinalityUpdate(newUpdate);
    boolean oldUpdateHasFinality = isFinalityUpdate(oldUpdate);

    if (newUpdateHasFinality != oldUpdateHasFinality) {
      return newUpdateHasFinality;
    }

    if (newUpdateHasFinality) {
      boolean newUpdateHasSyncCommitteeFinality =
          syncCommitteePeriodAtSlot(newUpdate.getFinalizedHeader().getBeacon().getSlot())
              == syncCommitteePeriodAtSlot(newUpdateSignatureSlot);
      boolean oldUpdateHasSyncCommitteeFinality =
          syncCommitteePeriodAtSlot(oldUpdate.getFinalizedHeader().getBeacon().getSlot())
              == syncCommitteePeriodAtSlot(oldUpdateSignatureSlot);

      if (newUpdateHasSyncCommitteeFinality != oldUpdateHasSyncCommitteeFinality) {
        return newUpdateHasSyncCommitteeFinality;
      }
    }

    if (newUpdateActiveParticipants != oldUpdateActiveParticipants) {
      return newUpdateActiveParticipants > oldUpdateActiveParticipants;
    }

    if (newUpdateAttestedSlot == oldUpdateAttestedSlot) {
      return newUpdateAttestedSlot.intValue() < oldUpdateAttestedSlot.intValue();
    }

    return newUpdate.getSignatureSlot().get().intValue()
        < oldUpdate.getSignatureSlot().get().intValue();
  }

  private UInt64 syncCommitteePeriodAtSlot(final UInt64 slot) {
    return spec.getSyncCommitteeUtilRequired(slot)
        .computeSyncCommitteePeriod(spec.computeEpochAtSlot(slot));
  }

  /** {@code is_sync_committee_update}. */
  private boolean isSyncCommitteeUpdate(final LightClientUpdate update) {
    return !update.getNextSyncCommitteeBranch().isDefault();
  }

  /** {@code is_finality_update}. */
  private boolean isFinalityUpdate(final LightClientUpdate update) {
    return !update.getFinalityBranch().isDefault();
  }
}
