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

package tech.pegasys.teku.validator.client;

import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class AttestationDutyDefaultSchedulingStrategy
    extends AbstractAttestationDutySchedulingStrategy {

  private final ValidatorApiChannel validatorApiChannel;
  private final boolean useDvtEndpoint;

  public AttestationDutyDefaultSchedulingStrategy(
      final Spec spec,
      final ForkProvider forkProvider,
      final Function<Bytes32, SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>>
          scheduledDutiesFactory,
      final OwnedValidators validators,
      final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions,
      final ValidatorApiChannel validatorApiChannel,
      final boolean useDvtEndpoint) {
    super(spec, forkProvider, scheduledDutiesFactory, validators, beaconCommitteeSubscriptions);
    this.validatorApiChannel = validatorApiChannel;
    this.useDvtEndpoint = useDvtEndpoint;
  }

  @Override
  public SafeFuture<SlotBasedScheduledDuties<?, ?>> scheduleAllDuties(
      final UInt64 epoch, final AttesterDuties duties) {
    final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties =
        getScheduledDuties(duties);

    final Optional<DvtAttestationAggregations> dvtAttestationAggregations =
        useDvtEndpoint
            ? Optional.of(
                new DvtAttestationAggregations(validatorApiChannel, duties.getDuties().size()))
            : Optional.empty();

    return scheduleDuties(scheduledDuties, duties.getDuties(), dvtAttestationAggregations)
        .<SlotBasedScheduledDuties<?, ?>>thenApply(__ -> scheduledDuties)
        .alwaysRun(beaconCommitteeSubscriptions::sendRequests);
  }
}
