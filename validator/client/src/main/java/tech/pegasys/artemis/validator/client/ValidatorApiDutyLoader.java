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

package tech.pegasys.artemis.validator.client;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.bls.BLSPublicKey;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.CommitteeUtil;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.api.ValidatorDuties;
import tech.pegasys.artemis.validator.client.duties.ScheduledDuties;

class ValidatorApiDutyLoader implements DutyLoader {
  private static final Logger LOG = LogManager.getLogger();
  private final ValidatorApiChannel validatorApiChannel;
  private final ForkProvider forkProvider;
  private final Supplier<ScheduledDuties> scheduledDutiesFactory;
  private final Map<BLSPublicKey, Validator> validators;

  ValidatorApiDutyLoader(
      final ValidatorApiChannel validatorApiChannel,
      final ForkProvider forkProvider,
      final Supplier<ScheduledDuties> scheduledDutiesFactory,
      final Map<BLSPublicKey, Validator> validators) {
    this.validatorApiChannel = validatorApiChannel;
    this.forkProvider = forkProvider;
    this.scheduledDutiesFactory = scheduledDutiesFactory;
    this.validators = validators;
  }

  @Override
  public SafeFuture<ScheduledDuties> loadDutiesForEpoch(final UnsignedLong epoch) {
    return requestAndScheduleDutiesForEpoch(epoch);
  }

  private SafeFuture<ScheduledDuties> requestAndScheduleDutiesForEpoch(final UnsignedLong epoch) {
    LOG.trace("Requesting duties for epoch {}", epoch);
    final ScheduledDuties scheduledDuties = scheduledDutiesFactory.get();
    return validatorApiChannel
        .getDuties(epoch, validators.keySet())
        .thenApply(
            maybeDuties ->
                maybeDuties.orElseThrow(
                    () ->
                        new NodeDataUnavailableException(
                            "Duties could not be calculated because chain data was not yet available")))
        .thenCompose(duties -> scheduleAllDuties(scheduledDuties, duties))
        .thenApply(__ -> scheduledDuties);
  }

  private SafeFuture<Void> scheduleAllDuties(
      final ScheduledDuties scheduledDuties, final List<ValidatorDuties> duties) {
    return SafeFuture.allOf(
        duties.stream()
            .map(validatorDuties -> scheduleDuties(scheduledDuties, validatorDuties))
            .toArray(SafeFuture[]::new));
  }

  private SafeFuture<Void> scheduleDuties(
      final ScheduledDuties scheduledDuties, final ValidatorDuties validatorDuties) {
    LOG.trace("Got validator duties: {}", validatorDuties);
    final Validator validator = validators.get(validatorDuties.getPublicKey());
    return validatorDuties
        .getDuties()
        .map(
            duties -> {
              duties
                  .getBlockProposalSlots()
                  .forEach(slot -> scheduleBlockProduction(scheduledDuties, validator, slot));
              return scheduleAttestationDuties(
                  scheduledDuties,
                  duties.getAttestationCommitteeIndex(),
                  duties.getAttestationCommitteePosition(),
                  duties.getValidatorIndex(),
                  validator,
                  duties.getAttestationSlot(),
                  duties.getAggregatorModulo());
            })
        .orElse(SafeFuture.COMPLETE);
  }

  private void scheduleBlockProduction(
      final ScheduledDuties scheduledDuties, final Validator validator, final UnsignedLong slot) {
    scheduledDuties.scheduleBlockProduction(slot, validator);
  }

  private SafeFuture<Void> scheduleAttestationDuties(
      final ScheduledDuties scheduledDuties,
      final int attestationCommitteeIndex,
      final int attestationCommitteePosition,
      final int validatorIndex,
      final Validator validator,
      final UnsignedLong slot,
      final int aggregatorModulo) {
    final SafeFuture<Optional<Attestation>> unsignedAttestationFuture =
        scheduleAttestationProduction(
            scheduledDuties,
            attestationCommitteeIndex,
            attestationCommitteePosition,
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

  private SafeFuture<Optional<Attestation>> scheduleAttestationProduction(
      final ScheduledDuties scheduledDuties,
      final int attestationCommitteeIndex,
      final int attestationCommitteePosition,
      final Validator validator,
      final UnsignedLong slot) {
    return scheduledDuties.scheduleAttestationProduction(
        slot, validator, attestationCommitteeIndex, attestationCommitteePosition);
  }

  private SafeFuture<Void> scheduleAggregation(
      final ScheduledDuties scheduledDuties,
      final int attestationCommitteeIndex,
      final int validatorIndex,
      final Validator validator,
      final UnsignedLong slot,
      final int aggregatorModulo,
      final SafeFuture<Optional<Attestation>> unsignedAttestationFuture) {
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
