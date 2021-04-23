/*
 * Copyright 2020 ConsenSys AG.
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
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.ProposerDuty;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.BlockProductionDuty;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class BlockProductionDutyLoader extends AbstractDutyLoader<ProposerDuties> {

  private final ValidatorApiChannel validatorApiChannel;
  private final Function<Bytes32, ScheduledDuties<BlockProductionDuty, Duty>>
      scheduledDutiesFactory;

  protected BlockProductionDutyLoader(
      final ValidatorApiChannel validatorApiChannel,
      final Function<Bytes32, ScheduledDuties<BlockProductionDuty, Duty>> scheduledDutiesFactory,
      final OwnedValidators validators,
      final ValidatorIndexProvider validatorIndexProvider) {
    super(validators, validatorIndexProvider);
    this.validatorApiChannel = validatorApiChannel;
    this.scheduledDutiesFactory = scheduledDutiesFactory;
  }

  @Override
  protected SafeFuture<Optional<ProposerDuties>> requestDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    return validatorApiChannel.getProposerDuties(epoch);
  }

  @Override
  protected SafeFuture<ScheduledDuties<? extends Duty, ? extends Duty>> scheduleAllDuties(
      final ProposerDuties duties) {
    final ScheduledDuties<BlockProductionDuty, Duty> scheduledDuties =
        scheduledDutiesFactory.apply(duties.getDependentRoot());
    duties.getDuties().forEach(duty -> scheduleDuty(scheduledDuties, duty));
    return SafeFuture.completedFuture(scheduledDuties);
  }

  private void scheduleDuty(
      final ScheduledDuties<BlockProductionDuty, Duty> scheduledDuties, final ProposerDuty duty) {
    validators
        .getValidator(duty.getPublicKey())
        .ifPresent(validator -> scheduledDuties.scheduleProduction(duty.getSlot(), validator));
  }
}
