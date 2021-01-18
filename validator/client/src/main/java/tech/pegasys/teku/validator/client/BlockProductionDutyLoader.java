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
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.ProposerDuty;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

public class BlockProductionDutyLoader extends AbstractDutyLoader<ProposerDuties> {

  private final ValidatorApiChannel validatorApiChannel;

  protected BlockProductionDutyLoader(
      final ValidatorApiChannel validatorApiChannel,
      final Function<Bytes32, ScheduledDuties> scheduledDutiesFactory,
      final Map<BLSPublicKey, Validator> validators,
      final ValidatorIndexProvider validatorIndexProvider) {
    super(scheduledDutiesFactory, validators, validatorIndexProvider);
    this.validatorApiChannel = validatorApiChannel;
  }

  @Override
  protected SafeFuture<Optional<ProposerDuties>> requestDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    return validatorApiChannel.getProposerDuties(epoch);
  }

  @Override
  protected SafeFuture<ScheduledDuties> scheduleAllDuties(final ProposerDuties duties) {
    final ScheduledDuties scheduledDuties = scheduledDutiesFactory.apply(duties.getDependentRoot());
    duties.getDuties().forEach(duty -> scheduleDuty(scheduledDuties, duty));
    return SafeFuture.completedFuture(scheduledDuties);
  }

  private void scheduleDuty(final ScheduledDuties scheduledDuties, final ProposerDuty duty) {
    final Validator validator = validators.get(duty.getPublicKey());
    if (validator != null) {
      scheduledDuties.scheduleBlockProduction(duty.getSlot(), validator);
    }
  }
}
