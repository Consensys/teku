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

import static java.util.Collections.emptyList;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.isAggregator;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.AttesterDuty;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

public class AttestationDutyLoader extends AbstractDutyLoader<AttesterDuty> {

  private static final Logger LOG = LogManager.getLogger();
  private final ValidatorApiChannel validatorApiChannel;
  private final ForkProvider forkProvider;
  private final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions;

  public AttestationDutyLoader(
      final ValidatorApiChannel validatorApiChannel,
      final ForkProvider forkProvider,
      final Supplier<ScheduledDuties> scheduledDutiesFactory,
      final Map<BLSPublicKey, Validator> validators,
      final ValidatorIndexProvider validatorIndexProvider,
      final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions) {
    super(scheduledDutiesFactory, validators, validatorIndexProvider);
    this.validatorApiChannel = validatorApiChannel;
    this.forkProvider = forkProvider;
    this.beaconCommitteeSubscriptions = beaconCommitteeSubscriptions;
  }

  @Override
  protected SafeFuture<Optional<List<AttesterDuty>>> requestDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    if (validatorIndices.isEmpty()) {
      return SafeFuture.completedFuture(Optional.of(emptyList()));
    }
    return validatorApiChannel
        .getAttestationDuties(epoch, validatorIndices)
        .thenApply(result -> result.map(AttesterDuties::getDuties));
  }

  @Override
  protected SafeFuture<Void> scheduleAllDuties(
      final ScheduledDuties scheduledDuties, final List<AttesterDuty> duties) {
    return super.scheduleAllDuties(scheduledDuties, duties)
        .alwaysRun(beaconCommitteeSubscriptions::sendRequests);
  }

  @Override
  protected SafeFuture<Void> scheduleDuties(
      final ScheduledDuties scheduledDuties, final AttesterDuty duty) {
    final Validator validator = validators.get(duty.getPublicKey());
    final int aggregatorModulo = CommitteeUtil.getAggregatorModulo(duty.getCommitteeLength());

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
      final ScheduledDuties scheduledDuties,
      final int attestationCommitteeIndex,
      final int attestationCommitteePosition,
      final int attestationCommitteeSize,
      final int validatorIndex,
      final Validator validator,
      final UInt64 slot) {
    return scheduledDuties.scheduleAttestationProduction(
        slot,
        validator,
        attestationCommitteeIndex,
        attestationCommitteePosition,
        attestationCommitteeSize,
        validatorIndex);
  }

  private SafeFuture<Void> scheduleAggregation(
      final ScheduledDuties scheduledDuties,
      final int attestationCommitteeIndex,
      final int committeesAtSlot,
      final int validatorIndex,
      final Validator validator,
      final UInt64 slot,
      final int aggregatorModulo,
      final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture) {
    return forkProvider
        .getForkInfo()
        .thenCompose(forkInfo -> validator.getSigner().signAggregationSlot(slot, forkInfo))
        .thenAccept(
            slotSignature -> {
              final boolean isAggregator = isAggregator(slotSignature, aggregatorModulo);
              beaconCommitteeSubscriptions.subscribeToBeaconCommittee(
                  new CommitteeSubscriptionRequest(
                      validatorIndex,
                      attestationCommitteeIndex,
                      UInt64.valueOf(committeesAtSlot),
                      slot,
                      isAggregator));
              if (isAggregator) {
                scheduledDuties.scheduleAggregationDuties(
                    slot,
                    validator,
                    validatorIndex,
                    slotSignature,
                    attestationCommitteeIndex,
                    unsignedAttestationFuture);
              }
            })
        .exceptionally(
            error -> {
              LOG.error("Failed to schedule aggregation duties", error);
              return null;
            });
  }
}
