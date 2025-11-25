/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.beaconnode;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

// TODO-GLOAS: https://github.com/Consensys/teku/issues/10053
public class TimeBasedEventAdapter implements BeaconChainEventAdapter {
  private static final Logger LOG = LogManager.getLogger();

  private final GenesisDataProvider genesisDataProvider;
  private final RepeatingTaskScheduler taskScheduler;
  private final TimeProvider timeProvider;
  private final ValidatorTimingChannel validatorTimingChannel;
  private final Spec spec;
  private UInt64 genesisTimeMillis;

  public TimeBasedEventAdapter(
      final GenesisDataProvider genesisDataProvider,
      final RepeatingTaskScheduler taskScheduler,
      final TimeProvider timeProvider,
      final ValidatorTimingChannel validatorTimingChannel,
      final Spec spec) {
    this.genesisDataProvider = genesisDataProvider;
    this.taskScheduler = taskScheduler;
    this.timeProvider = timeProvider;
    this.validatorTimingChannel = validatorTimingChannel;
    this.spec = spec;
  }

  void start(final UInt64 genesisTime) {
    this.genesisTimeMillis = secondsToMillis(genesisTime);
    final UInt64 currentSlot = getCurrentSlot();

    final UInt64 millisPerSlot = UInt64.valueOf(spec.getSlotDurationMillis(currentSlot));

    // NOTE: seconds_per_slot currently based on genesis slot, and timings set up based on this
    //       if seconds_per_slot ever changes, timers would have to be updated, which isn't
    //       currently implemented.

    final UInt64 nextSlotStartTimeMillis =
        spec.computeTimeMillisAtSlot(currentSlot.increment(), genesisTimeMillis);
    taskScheduler.scheduleRepeatingEventInMillis(
        nextSlotStartTimeMillis, millisPerSlot, this::onStartSlot);

    final UInt64 nextSlot = currentSlot.increment();

    taskScheduler.scheduleRepeatingEventInMillis(
        nextSlotStartTimeMillis.plus(spec.getAttestationDueMillis(nextSlot)),
        millisPerSlot,
        this::onAttestationCreationDue);
    taskScheduler.scheduleRepeatingEventInMillis(
        nextSlotStartTimeMillis.plus(spec.getAggregateDueMillis(nextSlot)),
        millisPerSlot,
        this::onAggregationDue);
    final SpecMilestone milestone = spec.atSlot(nextSlot).getMilestone();
    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)) {
      taskScheduler.scheduleRepeatingEventInMillis(
          nextSlotStartTimeMillis.plus(spec.getSyncMessageDueMillis(nextSlot)),
          millisPerSlot,
          this::onSyncCommitteeCreationDue);
      taskScheduler.scheduleRepeatingEventInMillis(
          nextSlotStartTimeMillis.plus(spec.getContributionDueMillis(nextSlot)),
          millisPerSlot,
          this::onContributionCreationDue);
    }
    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      taskScheduler.scheduleRepeatingEventInMillis(
          nextSlotStartTimeMillis.plus(spec.getPayloadAttestationDueMillis(nextSlot)),
          millisPerSlot,
          this::onPayloadAttestationCreationDue);
    }
  }

  private UInt64 getCurrentSlot() {
    return spec.getCurrentSlotFromTimeMillis(timeProvider.getTimeInMillis(), genesisTimeMillis);
  }

  private void onStartSlot(final UInt64 scheduledTimeMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = spec.getCurrentSlotFromTimeMillis(scheduledTimeMillis, genesisTimeMillis);
    if (isTooLate(scheduledTimeMillis, actualTimeInMillis)) {
      LOG.warn(
          "Skipping block creation for slot {} due to unexpected delay in slot processing", slot);
      return;
    }
    validatorTimingChannel.onSlot(slot);
    validatorTimingChannel.onBlockProductionDue(slot);
  }

  private void onAttestationCreationDue(
      final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = spec.getCurrentSlotFromTimeMillis(scheduledTimeInMillis, genesisTimeMillis);
    if (isTooLate(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn("Skipping attestation for slot {} due to unexpected delay in slot processing", slot);
      return;
    }
    validatorTimingChannel.onAttestationCreationDue(slot);
  }

  private void onSyncCommitteeCreationDue(
      final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = spec.getCurrentSlotFromTimeMillis(scheduledTimeInMillis, genesisTimeMillis);
    if (isTooLate(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn(
          "Skipping sync committee message for slot {} due to unexpected delay in slot processing",
          slot);
      return;
    }
    validatorTimingChannel.onSyncCommitteeCreationDue(slot);
  }

  private void onContributionCreationDue(
      final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = spec.getCurrentSlotFromTimeMillis(scheduledTimeInMillis, genesisTimeMillis);
    if (isTooLate(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn(
          "Skipping contribution message for slot {} due to unexpected delay in slot processing",
          slot);
      return;
    }
    validatorTimingChannel.onContributionCreationDue(slot);
  }

  private void onPayloadAttestationCreationDue(
      final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = spec.getCurrentSlotFromTimeMillis(scheduledTimeInMillis, genesisTimeMillis);
    if (isTooLate(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn(
          "Skipping payload attestation for slot {} due to unexpected delay in slot processing",
          slot);
      return;
    }
    validatorTimingChannel.onPayloadAttestationCreationDue(slot);
  }

  private void onAggregationDue(
      final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = spec.getCurrentSlotFromTimeMillis(scheduledTimeInMillis, genesisTimeMillis);
    if (isTooLate(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn("Skipping aggregation for slot {} due to unexpected delay in slot processing", slot);
      return;
    }
    validatorTimingChannel.onAttestationAggregationDue(slot);
  }

  private boolean isTooLate(final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 currentSlot = getCurrentSlot();
    return scheduledTimeInMillis
        .plus(spec.getSlotDurationMillis(currentSlot))
        .isLessThan(actualTimeInMillis);
  }

  @Override
  public SafeFuture<Void> start() {
    // Don't wait for the genesis time to be available before considering startup complete
    // The beacon node may not be available or genesis may not yet be known.
    genesisDataProvider.getGenesisTime().thenAccept(this::start).finishError(LOG);
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> stop() {
    return SafeFuture.COMPLETE;
  }
}
