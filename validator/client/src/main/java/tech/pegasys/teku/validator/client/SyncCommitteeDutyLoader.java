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

package tech.pegasys.teku.validator.client;

import java.util.Collection;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.synccommittee.ChainHeadTracker;
import tech.pegasys.teku.validator.client.duties.synccommittee.SyncCommitteeScheduledDuties;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class SyncCommitteeDutyLoader
    extends AbstractDutyLoader<SyncCommitteeDuties, SyncCommitteeScheduledDuties> {

  private final Spec spec;
  private final ValidatorApiChannel validatorApiChannel;
  private final ChainHeadTracker chainHeadTracker;
  private final ForkProvider forkProvider;

  public SyncCommitteeDutyLoader(
      final OwnedValidators validators,
      final ValidatorIndexProvider validatorIndexProvider,
      final Spec spec,
      final ValidatorApiChannel validatorApiChannel,
      final ChainHeadTracker chainHeadTracker,
      final ForkProvider forkProvider) {
    super(validators, validatorIndexProvider);
    this.spec = spec;
    this.validatorApiChannel = validatorApiChannel;
    this.chainHeadTracker = chainHeadTracker;
    this.forkProvider = forkProvider;
  }

  @Override
  protected SafeFuture<Optional<SyncCommitteeDuties>> requestDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    return validatorApiChannel.getSyncCommitteeDuties(epoch, validatorIndices);
  }

  @Override
  protected SafeFuture<SyncCommitteeScheduledDuties> scheduleAllDuties(
      final UInt64 epoch, final SyncCommitteeDuties duties) {
    final SyncCommitteeScheduledDuties.Builder dutyBuilder =
        SyncCommitteeScheduledDuties.builder()
            .forkProvider(forkProvider)
            .validatorApiChannel(validatorApiChannel)
            .chainHeadTracker(chainHeadTracker)
            .spec(spec)
            .lastEpochInCommitteePeriod(
                spec.getSyncCommitteeUtilRequired(spec.computeStartSlotAtEpoch(epoch))
                    .computeFirstEpochOfNextSyncCommitteePeriod(epoch)
                    .minusMinZero(1));
    duties.getDuties().forEach(duty -> scheduleDuty(dutyBuilder, duty));
    final SyncCommitteeScheduledDuties scheduledDuties = dutyBuilder.build();
    scheduledDuties.subscribeToSubnets();
    return SafeFuture.completedFuture(scheduledDuties);
  }

  private void scheduleDuty(
      final SyncCommitteeScheduledDuties.Builder dutyBuilder,
      final tech.pegasys.teku.validator.api.SyncCommitteeDuty duty) {
    validators
        .getValidator(duty.getPublicKey())
        .ifPresent(
            validator ->
                dutyBuilder.committeeAssignments(
                    validator, duty.getValidatorIndex(), duty.getValidatorSyncCommitteeIndices()));
  }
}
