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
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

public class AttestationDutyLoader extends AbstractDutyLoader<AttesterDuties> {

  private static final Logger LOG = LogManager.getLogger();
  private final ValidatorApiChannel validatorApiChannel;
  private final ForkProvider forkProvider;

  public AttestationDutyLoader(
      final ValidatorApiChannel validatorApiChannel,
      final ForkProvider forkProvider,
      final Supplier<ScheduledDuties> scheduledDutiesFactory,
      final Map<BLSPublicKey, Validator> validators,
      final ValidatorIndexProvider validatorIndexProvider) {
    super(scheduledDutiesFactory, validators, validatorIndexProvider);
    this.validatorApiChannel = validatorApiChannel;
    this.forkProvider = forkProvider;
  }

  @Override
  protected SafeFuture<Optional<List<AttesterDuties>>> requestDuties(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    return validatorApiChannel.getAttestationDuties(epoch, validatorIndices);
  }

  @Override
  protected SafeFuture<Void> scheduleDuties(
      final ScheduledDuties scheduledDuties, final AttesterDuties duty) {
    final int attestationCommitteeIndex = duty.getCommitteeIndex();
    final int attestationCommitteePosition = duty.getValidatorCommitteeIndex();
    final int validatorIndex = duty.getValidatorIndex();
    final Validator validator = validators.get(duty.getPublicKey());
    final UInt64 slot = duty.getSlot();
    final int aggregatorModulo = CommitteeUtil.getAggregatorModulo(duty.getCommitteeLength());

    final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture =
        scheduleAttestationProduction(
            scheduledDuties,
            attestationCommitteeIndex,
            attestationCommitteePosition,
            duty.getCommitteeLength(),
            validatorIndex,
            validator,
            slot);

    return scheduleAggregation(
        scheduledDuties,
        attestationCommitteeIndex,
        validatorIndex,
        validator,
        slot,
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
              if (CommitteeUtil.isAggregator(slotSignature, aggregatorModulo)) {
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
