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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.AttesterDuty;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class AttestationDutyLoader
    extends AbstractDutyLoader<AttesterDuties, SlotBasedScheduledDuties<?, ?>> {

  private static final Logger LOG = LogManager.getLogger();
  private final ValidatorApiChannel validatorApiChannel;
  private final ForkProvider forkProvider;
  private final Function<
          Bytes32, SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>>
      scheduledDutiesFactory;
  private final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions;
  private final Spec spec;

  public AttestationDutyLoader(
      final ValidatorApiChannel validatorApiChannel,
      final ForkProvider forkProvider,
      final Function<Bytes32, SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>>
          scheduledDutiesFactory,
      final OwnedValidators validators,
      final ValidatorIndexProvider validatorIndexProvider,
      final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions,
      final Spec spec) {
    super(validators, validatorIndexProvider);
    this.validatorApiChannel = validatorApiChannel;
    this.forkProvider = forkProvider;
    this.scheduledDutiesFactory = scheduledDutiesFactory;
    this.beaconCommitteeSubscriptions = beaconCommitteeSubscriptions;
    this.spec = spec;
  }

  @Override
  protected SafeFuture<Optional<AttesterDuties>> requestDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    if (validatorIndices.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    return validatorApiChannel.getAttestationDuties(epoch, validatorIndices);
  }

  @Override
  protected SafeFuture<SlotBasedScheduledDuties<?, ?>> scheduleAllDuties(
      final UInt64 epoch, final AttesterDuties duties) {
    final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties =
        scheduledDutiesFactory.apply(duties.getDependentRoot());
    return SafeFuture.allOf(
            duties.getDuties().stream()
                .map(duty -> scheduleDuties(scheduledDuties, duty))
                .toArray(SafeFuture[]::new))
        .<SlotBasedScheduledDuties<?, ?>>thenApply(__ -> scheduledDuties)
        .alwaysRun(beaconCommitteeSubscriptions::sendRequests);
  }

  private SafeFuture<Void> scheduleDuties(
      final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties,
      final AttesterDuty duty) {
    final Optional<Validator> maybeValidator = validators.getValidator(duty.getPublicKey());
    if (maybeValidator.isEmpty()) {
      return SafeFuture.COMPLETE;
    }
    final Validator validator = maybeValidator.get();
    final int aggregatorModulo =
        spec.atSlot(duty.getSlot())
            .getValidatorsUtil()
            .getAggregatorModulo(duty.getCommitteeLength());

    final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture =
        scheduleAttestationProduction(
            scheduledDuties,
            duty.getCommitteeIndex(),
            duty.getValidatorCommitteeIndex(),
            duty.getCommitteeLength(),
            duty.getValidatorIndex(),
            validator,
            duty.getSlot());

    return scheduleAggregation(
        scheduledDuties,
        duty.getCommitteeIndex(),
        duty.getCommitteesAtSlot(),
        duty.getValidatorIndex(),
        validator,
        duty.getSlot(),
        aggregatorModulo,
        unsignedAttestationFuture);
  }

  private SafeFuture<Optional<AttestationData>> scheduleAttestationProduction(
      final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties,
      final int attestationCommitteeIndex,
      final int attestationCommitteePosition,
      final int attestationCommitteeSize,
      final int validatorIndex,
      final Validator validator,
      final UInt64 slot) {
    return scheduledDuties.scheduleProduction(
        slot,
        validator,
        duty ->
            duty.addValidator(
                validator,
                attestationCommitteeIndex,
                attestationCommitteePosition,
                validatorIndex,
                attestationCommitteeSize));
  }

  private SafeFuture<Void> scheduleAggregation(
      final SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty> scheduledDuties,
      final int attestationCommitteeIndex,
      final int committeesAtSlot,
      final int validatorIndex,
      final Validator validator,
      final UInt64 slot,
      final int aggregatorModulo,
      final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture) {
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(forkInfo -> validator.getSigner().signAggregationSlot(slot, forkInfo))
        .thenAccept(
            slotSignature -> {
              final SpecVersion specVersion = spec.atSlot(slot);
              final boolean isAggregator =
                  specVersion.getValidatorsUtil().isAggregator(slotSignature, aggregatorModulo);
              beaconCommitteeSubscriptions.subscribeToBeaconCommittee(
                  new CommitteeSubscriptionRequest(
                      validatorIndex,
                      attestationCommitteeIndex,
                      UInt64.valueOf(committeesAtSlot),
                      slot,
                      isAggregator));
              if (isAggregator) {
                scheduledDuties.scheduleAggregation(
                    slot,
                    validator,
                    duty ->
                        duty.addValidator(
                            validator,
                            validatorIndex,
                            slotSignature,
                            attestationCommitteeIndex,
                            unsignedAttestationFuture));
              }
            })
        .exceptionally(
            error -> {
              LOG.error("Failed to schedule aggregation duties", error);
              return null;
            });
  }
}
