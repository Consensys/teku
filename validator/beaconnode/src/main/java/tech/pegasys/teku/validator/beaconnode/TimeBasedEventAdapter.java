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
  private UInt64 genesisTimeMillis;
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
    this.genesisTimeMillis = secondsToMillis(genesisTime);
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
    return spec.computeTimeMillisAtSlot(getCurrentSlot().increment(), genesisTimeMillis);
  }

  UInt64 getCurrentSlot() {
    return spec.getCurrentSlotFromTimeMillis(timeProvider.getTimeInMillis(), genesisTimeMillis);
  }

  void onStartSlot(final UInt64 scheduledTimeMillis, final UInt64 actualTimeMillis) {
    final UInt64 slot = spec.getCurrentSlot(scheduledTimeMillis, genesisTimeMillis);
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

  UInt64 getCurrentSlotForMillis(final UInt64 millis) {
    return spec.getCurrentSlotFromTimeMillis(millis, genesisTimeMillis);
  }

  boolean isTooLateInMillis(final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 currentSlot = getCurrentSlot();
    final UInt64 millisPerSlot = getMillisPerSlot(currentSlot);
    return scheduledTimeInMillis.plus(millisPerSlot).isLessThan(actualTimeInMillis);
  }

  UInt64 getMillisPerSlot(final UInt64 slot) {
    return UInt64.valueOf(spec.atSlot(slot).getConfig().getSlotDurationMillis());
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
