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

import static com.google.common.primitives.UnsignedLong.ONE;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.base.Throwables;
import com.google.common.primitives.UnsignedLong;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.bls.BLSPublicKey;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.CommitteeUtil;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.validator.api.NodeSyncingException;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.api.ValidatorDuties;
import tech.pegasys.artemis.validator.api.ValidatorTimingChannel;
import tech.pegasys.artemis.validator.client.duties.ScheduledDuties;

public class DutyScheduler implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final AtomicReference<UnsignedLong> latestScheduledEpoch = new AtomicReference<>();
  private final ScheduledDuties scheduledDuties;
  private final AsyncRunner asyncRunner;
  private final ValidatorApiChannel validatorApiChannel;
  private final ForkProvider forkProvider;
  private final Map<BLSPublicKey, Validator> validators;

  public DutyScheduler(
      final AsyncRunner asyncRunner,
      final ValidatorApiChannel validatorApiChannel,
      final ForkProvider forkProvider,
      final ScheduledDuties scheduledDuties,
      final Map<BLSPublicKey, Validator> validators) {
    this.asyncRunner = asyncRunner;
    this.validatorApiChannel = validatorApiChannel;
    this.forkProvider = forkProvider;
    this.scheduledDuties = scheduledDuties;
    this.validators = validators;
  }

  @Override
  public void onSlot(final UnsignedLong slotNumber) {
    final UnsignedLong epochNumber = compute_epoch_at_slot(slotNumber);
    latestScheduledEpoch.getAndUpdate(
        lastRequestedEpoch -> {
          final UnsignedLong startEpoch =
              lastRequestedEpoch == null ? epochNumber : lastRequestedEpoch.plus(ONE);
          final UnsignedLong endEpoch = epochNumber.plus(ONE);
          for (UnsignedLong currentEpoch = startEpoch;
              currentEpoch.compareTo(endEpoch) <= 0;
              currentEpoch = currentEpoch.plus(ONE)) {
            scheduleDutiesForEpoch(currentEpoch).reportExceptions();
          }
          return startEpoch.compareTo(endEpoch) > 0 ? lastRequestedEpoch : endEpoch;
        });
  }

  private SafeFuture<Void> scheduleDutiesForEpoch(final UnsignedLong epoch) {
    LOG.trace("Requesting duties for epoch {}", epoch);
    return validatorApiChannel
        .getDuties(epoch, validators.keySet())
        .orTimeout(Constants.VALIDATOR_DUTIES_TIMEOUT, TimeUnit.SECONDS)
        .thenApply(
            maybeDuties ->
                maybeDuties.orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Duties could not be calculated because chain data was not yet available")))
        .thenAccept(duties -> duties.forEach(this::scheduleDuties))
        .exceptionallyCompose(
            error -> {
              if (Throwables.getRootCause(error) instanceof NodeSyncingException) {
                LOG.debug("Unable to schedule duties for epoch {} because node was syncing", epoch);
                return SafeFuture.COMPLETE;
              }
              LOG.error(
                  "Failed to request validator duties for epoch "
                      + epoch
                      + ". Retrying after delay.",
                  error);
              return asyncRunner.runAfterDelay(
                  () -> scheduleDutiesForEpoch(epoch), 5, TimeUnit.SECONDS);
            });
  }

  private void scheduleDuties(final ValidatorDuties validatorDuties) {
    LOG.trace("Got validator duties: {}", validatorDuties);
    final Validator validator = validators.get(validatorDuties.getPublicKey());
    validatorDuties
        .getDuties()
        .ifPresent(
            duties -> {
              duties
                  .getBlockProposalSlots()
                  .forEach(slot -> scheduleBlockProduction(validator, slot));
              scheduleAttestationDuties(
                  duties.getAttestationCommitteeIndex(),
                  duties.getAttestationCommitteePosition(),
                  duties.getValidatorIndex(),
                  validator,
                  duties.getAttestationSlot(),
                  duties.getAggregatorModulo());
            });
  }

  private void scheduleBlockProduction(final Validator validator, final UnsignedLong slot) {
    scheduledDuties.scheduleBlockProduction(slot, validator);
  }

  private void scheduleAttestationDuties(
      final int attestationCommitteeIndex,
      final int attestationCommitteePosition,
      final int validatorIndex,
      final Validator validator,
      final UnsignedLong slot,
      final int aggregatorModulo) {
    final SafeFuture<Optional<Attestation>> unsignedAttestationFuture =
        scheduleAttestationProduction(
            attestationCommitteeIndex, attestationCommitteePosition, validator, slot);

    scheduleAggregation(
        attestationCommitteeIndex,
        validatorIndex,
        validator,
        slot,
        aggregatorModulo,
        unsignedAttestationFuture);
  }

  private SafeFuture<Optional<Attestation>> scheduleAttestationProduction(
      final int attestationCommitteeIndex,
      final int attestationCommitteePosition,
      final Validator validator,
      final UnsignedLong slot) {
    return scheduledDuties.scheduleAttestationProduction(
        slot, validator, attestationCommitteeIndex, attestationCommitteePosition);
  }

  private void scheduleAggregation(
      final int attestationCommitteeIndex,
      final int validatorIndex,
      final Validator validator,
      final UnsignedLong slot,
      final int aggregatorModulo,
      final SafeFuture<Optional<Attestation>> unsignedAttestationFuture) {
    forkProvider
        .getFork()
        .thenCompose(fork -> validator.getSigner().signAggregationSlot(slot, fork))
        .finish(
            slotSignature -> {
              if (CommitteeUtil.isAggregator(slotSignature, aggregatorModulo)) {
                scheduledDuties.scheduleAggregationDuties(
                    slot,
                    validatorIndex,
                    slotSignature,
                    attestationCommitteeIndex,
                    unsignedAttestationFuture);
              }
            },
            error -> LOG.error("Failed to schedule aggregation duties", error));
  }

  @Override
  public void onBlockProductionDue(final UnsignedLong slot) {
    scheduledDuties.produceBlock(slot);
  }

  @Override
  public void onAttestationCreationDue(final UnsignedLong slot) {
    scheduledDuties.produceAttestations(slot);
  }

  @Override
  public void onAttestationAggregationDue(final UnsignedLong slot) {
    scheduledDuties.performAggregation(slot);
  }
}
