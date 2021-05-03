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
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.SyncCommitteeScheduledDuties;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class SyncCommitteeDutyLoader
    extends AbstractDutyLoader<
        tech.pegasys.teku.validator.api.SyncCommitteeDuties, SyncCommitteeScheduledDuties> {

  private final Spec spec;
  private final ValidatorApiChannel validatorApiChannel;
  private final ForkProvider forkProvider;

  protected SyncCommitteeDutyLoader(
      final OwnedValidators validators,
      final ValidatorIndexProvider validatorIndexProvider,
      final Spec spec,
      final ValidatorApiChannel validatorApiChannel,
      final ForkProvider forkProvider) {
    super(validators, validatorIndexProvider);
    this.spec = spec;
    this.validatorApiChannel = validatorApiChannel;
    this.forkProvider = forkProvider;
  }

  @Override
  protected SafeFuture<Optional<tech.pegasys.teku.validator.api.SyncCommitteeDuties>> requestDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    return validatorApiChannel.getSyncCommitteeDuties(epoch, validatorIndices);
  }

  @Override
  protected SafeFuture<SyncCommitteeScheduledDuties> scheduleAllDuties(
      final tech.pegasys.teku.validator.api.SyncCommitteeDuties duties) {
    final SyncCommitteeScheduledDuties.Builder dutyBuilder =
        SyncCommitteeScheduledDuties.builder()
            .forkProvider(forkProvider)
            .validatorApiChannel(validatorApiChannel)
            .spec(spec);
    duties.getDuties().forEach(duty -> scheduleDuty(dutyBuilder, duty));
    return SafeFuture.completedFuture(dutyBuilder.build());
  }

  private void scheduleDuty(
      final SyncCommitteeScheduledDuties.Builder dutyBuilder,
      final tech.pegasys.teku.validator.api.SyncCommitteeDuty duty) {
    validators
        .getValidator(duty.getPublicKey())
        .ifPresent(
            validator ->
                dutyBuilder.committeeAssignments(
                    validator, duty.getValidatorIndex(), duty.getSyncCommitteeIndices()));
  }
}
