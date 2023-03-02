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

package tech.pegasys.teku.validator.client;

import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeDuty;
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

  private final MetricsSystem metricsSystem;

  public SyncCommitteeDutyLoader(
      final OwnedValidators validators,
      final ValidatorIndexProvider validatorIndexProvider,
      final Spec spec,
      final ValidatorApiChannel validatorApiChannel,
      final ChainHeadTracker chainHeadTracker,
      final ForkProvider forkProvider,
      final MetricsSystem metricsSystem) {
    super(validators, validatorIndexProvider);
    this.spec = spec;
    this.validatorApiChannel = validatorApiChannel;
    this.chainHeadTracker = chainHeadTracker;
    this.forkProvider = forkProvider;
    this.metricsSystem = metricsSystem;
  }

  @Override
  protected SafeFuture<Optional<SyncCommitteeDuties>> requestDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    return validatorApiChannel
        .getSyncCommitteeDuties(epoch, validatorIndices)
        .thenPeek(
            maybeDuties -> {
              metricsSystem.createIntegerGauge(
                  TekuMetricCategory.VALIDATOR,
                  "scheduled_sync_committee_duties_current",
                  "Current number of Sync committee members performing duties",
                  () -> maybeDuties.map(d -> d.getDuties().size()).orElse(0));
            });
  }

  @Override
  protected SafeFuture<SyncCommitteeScheduledDuties> scheduleAllDuties(
      final UInt64 epoch, final SyncCommitteeDuties duties) {
    final UInt64 lastEpochInCommitteePeriod = spec.getSyncCommitteeUtilRequired(spec.computeStartSlotAtEpoch(epoch))
            .computeFirstEpochOfNextSyncCommitteePeriod(epoch)
            .minusMinZero(1);
    final SyncCommitteeScheduledDuties.Builder dutyBuilder =
        SyncCommitteeScheduledDuties.builder()
            .forkProvider(forkProvider)
            .validatorApiChannel(validatorApiChannel)
            .chainHeadTracker(chainHeadTracker)
            .spec(spec)
            .lastEpochInCommitteePeriod(
                    lastEpochInCommitteePeriod);
    duties.getDuties().forEach(duty -> scheduleDuty(dutyBuilder, duty));
    final SyncCommitteeScheduledDuties scheduledDuties = dutyBuilder.build();
    scheduledDuties.subscribeToSubnets();

    metricsSystem.createIntegerGauge(
            TekuMetricCategory.VALIDATOR,
            "current_sync_committee_last_epoch",
            "The final epoch of the current sync committee period",
            lastEpochInCommitteePeriod::intValue);
    return SafeFuture.completedFuture(scheduledDuties);
  }

  private void scheduleDuty(
      final SyncCommitteeScheduledDuties.Builder dutyBuilder, final SyncCommitteeDuty duty) {
    validators
        .getValidator(duty.getPublicKey())
        .ifPresent(
            validator ->
                dutyBuilder.committeeAssignments(
                    validator, duty.getValidatorIndex(), duty.getValidatorSyncCommitteeIndices()));
  }
}
