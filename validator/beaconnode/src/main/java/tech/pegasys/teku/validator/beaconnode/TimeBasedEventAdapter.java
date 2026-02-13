/*
 * Copyright Consensys Software Inc., 2022
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

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.millisToSeconds;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler.RepeatingTask;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public abstract class TimeBasedEventAdapter implements BeaconChainEventAdapter {
  private static final Logger LOG = LogManager.getLogger();

  private final GenesisDataProvider genesisDataProvider;
  private final TimeProvider timeProvider;
  private UInt64 genesisTime;
  final ValidatorTimingChannel validatorTimingChannel;
  final Spec spec;
  final RepeatingTaskScheduler taskScheduler;

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

  abstract void start(final UInt64 genesisTime);

  void setGenesisTime(final UInt64 genesisTime) {
    this.genesisTime = genesisTime;
  }

  void scheduleDuty(final UInt64 period, final UInt64 offset, final RepeatingTask dutyTask) {
    scheduleDuty(getNextSlotStartMillis(), period, offset, dutyTask);
  }

  void scheduleDuty(
      final UInt64 nextSlotStartTimeMillis,
      final UInt64 period,
      final UInt64 offset,
      final RepeatingTask dutyTask) {
    taskScheduler.scheduleRepeatingEventInMillis(
        nextSlotStartTimeMillis.plus(offset), period, dutyTask);
  }

  private UInt64 getNextSlotStartMillis() {
    final UInt64 currentSlot = getCurrentSlot();
    final UInt64 nextSlotStartTime = spec.getSlotStartTime(currentSlot.plus(1), genesisTime);
    return secondsToMillis(nextSlotStartTime);
  }

  UInt64 getCurrentSlot() {
    return spec.getCurrentSlot(timeProvider.getTimeInSeconds(), genesisTime);
  }

  UInt64 getSecondsPerSlot(final UInt64 slot) {
    return UInt64.valueOf(spec.getSecondsPerSlot(slot));
  }

  void onStartSlot(final UInt64 scheduledTime, final UInt64 actualTime) {
    final UInt64 slot = spec.getCurrentSlot(scheduledTime, genesisTime);
    if (isTooLate(scheduledTime, actualTime)) {
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

  UInt64 getCurrentSlotForMillis(final UInt64 millis) {
    return spec.getCurrentSlot(millisToSeconds(millis), genesisTime);
  }

  private boolean isTooLate(final UInt64 scheduledTime, final UInt64 actualTime) {
    final UInt64 currentSlot = getCurrentSlot();
    final UInt64 secondsPerSlot = getSecondsPerSlot(currentSlot);
    return scheduledTime.plus(secondsPerSlot).isLessThan(actualTime);
  }

  boolean isTooLateInMillis(final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 currentSlot = getCurrentSlot();
    final UInt64 millisPerSlot = getMillisPerSlot(currentSlot);
    return scheduledTimeInMillis.plus(millisPerSlot).isLessThan(actualTimeInMillis);
  }

  private UInt64 getMillisPerSlot(final UInt64 slot) {
    return secondsToMillis(getSecondsPerSlot(slot));
  }

  @Override
  public SafeFuture<Void> start() {
    // Don't wait for the genesis time to be available before considering startup complete
    // The beacon node may not be available or genesis may not yet be known.
    genesisDataProvider.getGenesisTime().thenAccept(this::start).ifExceptionGetsHereRaiseABug();
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> stop() {
    return SafeFuture.COMPLETE;
  }
}
