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

package tech.pegasys.teku.validator.beaconnode;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler.RepeatingTask;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public abstract class TimeBasedEventAdapter {
  private static final Logger LOG = LogManager.getLogger();

  private final TimeProvider timeProvider;
  private final UInt64 firstSlot;
  private UInt64 genesisTimeMillis;
  final ValidatorTimingChannel validatorTimingChannel;
  final Spec spec;
  final RepeatingTaskScheduler taskScheduler;

  public TimeBasedEventAdapter(
      final UInt64 firstSlot,
      final RepeatingTaskScheduler taskScheduler,
      final TimeProvider timeProvider,
      final ValidatorTimingChannel validatorTimingChannel,
      final Spec spec) {
    this.firstSlot = firstSlot;
    this.taskScheduler = taskScheduler;
    this.timeProvider = timeProvider;
    this.validatorTimingChannel = validatorTimingChannel;
    this.spec = spec;
  }

  /** Returns the first slot of this adapter's milestone (used for chain expiry computation). */
  UInt64 getFirstSlot() {
    return firstSlot;
  }

  abstract void scheduleDuties(
      UInt64 nextSlotStartTimeMillis,
      UInt64 millisPerSlot,
      Optional<UInt64> expirationTimeMillis,
      Runnable onExpired);

  void start(final UInt64 genesisTime) {
    setGenesisTime(genesisTime);
    final UInt64 currentSlot = getCurrentSlot();
    final UInt64 nextSlotStartTimeMillis = getSlotStartTimeMillis(currentSlot.increment());
    final UInt64 millisPerSlot = getMillisPerSlot(currentSlot);

    scheduleDuties(nextSlotStartTimeMillis, millisPerSlot, Optional.empty(), () -> {});
  }

  void setGenesisTime(final UInt64 genesisTime) {
    this.genesisTimeMillis = secondsToMillis(genesisTime);
  }

  record ScheduledEvent(int offsetMillis, RepeatingTask handler) {}

  void scheduleAll(
      final UInt64 nextSlotStartTimeMillis,
      final UInt64 millisPerSlot,
      final Optional<UInt64> expirationTimeMillis,
      final Runnable onExpired,
      final ScheduledEvent... events) {
    LOG.debug(
        "Scheduling {} events for {} starting at {} with period {}ms, expiry {}",
        events.length,
        getClass().getSimpleName(),
        nextSlotStartTimeMillis,
        millisPerSlot,
        expirationTimeMillis.map(UInt64::toString).orElse("none"));
    for (final ScheduledEvent event : events) {
      final UInt64 firstInvocation = nextSlotStartTimeMillis.plus(event.offsetMillis());
      if (expirationTimeMillis.isPresent()) {
        taskScheduler.scheduleRepeatingEventInMillis(
            firstInvocation,
            millisPerSlot,
            event.handler(),
            expirationTimeMillis.get(),
            (__, ___) -> onExpired.run());
      } else {
        taskScheduler.scheduleRepeatingEventInMillis(
            firstInvocation, millisPerSlot, event.handler());
      }
    }
  }

  private UInt64 getCurrentSlot() {
    return spec.getCurrentSlotFromTimeMillis(timeProvider.getTimeInMillis(), genesisTimeMillis);
  }

  void onStartSlot(final UInt64 scheduledTimeMillis, final UInt64 actualTimeMillis) {
    final UInt64 slot = getCurrentSlotForMillis(scheduledTimeMillis);
    if (isTooLateInMillis(scheduledTimeMillis, actualTimeMillis)) {
      LOG.warn(
          "Skipping block creation for slot {} due to unexpected delay in slot processing", slot);
      return;
    }
    validatorTimingChannel.onSlot(slot);
    validatorTimingChannel.onBlockProductionDue(slot);
  }

  void onAttestationCreationDue(
      final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = getCurrentSlotForMillis(scheduledTimeInMillis);
    if (isTooLateInMillis(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn("Skipping attestation for slot {} due to unexpected delay in slot processing", slot);
      return;
    }
    validatorTimingChannel.onAttestationCreationDue(slot);
  }

  void onAggregationDue(final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = getCurrentSlotForMillis(scheduledTimeInMillis);
    if (isTooLateInMillis(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn("Skipping aggregation for slot {} due to unexpected delay in slot processing", slot);
      return;
    }
    validatorTimingChannel.onAttestationAggregationDue(slot);
  }

  void onSyncCommitteeCreationDue(
      final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = getCurrentSlotForMillis(scheduledTimeInMillis);
    if (isTooLateInMillis(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn(
          "Skipping sync committee message for slot {} due to unexpected delay in slot processing",
          slot);
      return;
    }
    validatorTimingChannel.onSyncCommitteeCreationDue(slot);
  }

  void onContributionCreationDue(
      final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = getCurrentSlotForMillis(scheduledTimeInMillis);
    if (isTooLateInMillis(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn(
          "Skipping contribution message for slot {} due to unexpected delay in slot processing",
          slot);
      return;
    }
    validatorTimingChannel.onContributionCreationDue(slot);
  }

  UInt64 getCurrentSlotForMillis(final UInt64 millis) {
    return spec.getCurrentSlotFromTimeMillis(millis, genesisTimeMillis);
  }

  boolean isTooLateInMillis(final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 currentSlot = getCurrentSlot();
    final UInt64 millisPerSlot = getMillisPerSlot(currentSlot);
    return scheduledTimeInMillis.plus(millisPerSlot).isLessThan(actualTimeInMillis);
  }

  private UInt64 getMillisPerSlot(final UInt64 slot) {
    return UInt64.valueOf(spec.atSlot(slot).getConfig().getSlotDurationMillis());
  }

  private UInt64 getSlotStartTimeMillis(final UInt64 slot) {
    return spec.computeTimeMillisAtSlot(slot, genesisTimeMillis);
  }
}
