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

package tech.pegasys.teku.validator.beaconnode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class TimeBasedEventAdapter implements BeaconChainEventAdapter {
  private static final Logger LOG = LogManager.getLogger();

  private final GenesisDataProvider genesisDataProvider;
  private final RepeatingTaskScheduler taskScheduler;
  private final TimeProvider timeProvider;
  private final ValidatorTimingChannel validatorTimingChannel;
  private final Spec spec;
  private final boolean useIndependentAttestationTiming;
  private UInt64 genesisTime;

  public TimeBasedEventAdapter(
      final GenesisDataProvider genesisDataProvider,
      final RepeatingTaskScheduler taskScheduler,
      final TimeProvider timeProvider,
      final ValidatorTimingChannel validatorTimingChannel,
      final boolean useIndependentAttestationTiming,
      final Spec spec) {
    this.genesisDataProvider = genesisDataProvider;
    this.taskScheduler = taskScheduler;
    this.timeProvider = timeProvider;
    this.validatorTimingChannel = validatorTimingChannel;
    this.useIndependentAttestationTiming = useIndependentAttestationTiming;
    this.spec = spec;
  }

  void start(final UInt64 genesisTime) {
    this.genesisTime = genesisTime;
    final UInt64 currentSlot = spec.getCurrentSlot(timeProvider.getTimeInSeconds(), genesisTime);
    final UInt64 nextSlotStartTime = spec.getSlotStartTime(currentSlot.plus(1), genesisTime);
    final UInt64 secondsPerSlot = UInt64.valueOf(spec.getSecondsPerSlot(currentSlot));

    // NOTE: seconds_per_slot currently based on genesis slot, and timings set up based on this
    //       if seconds_per_slot ever changes, timers would have to be updated, which isn't
    //       currently implemented.
    final long oneThirdSlotSeconds = spec.getSecondsPerSlot(currentSlot) / 3;
    final long twoThirdSlotSeconds = oneThirdSlotSeconds * 2;
    taskScheduler.scheduleRepeatingEvent(nextSlotStartTime, secondsPerSlot, this::onStartSlot);
    if (useIndependentAttestationTiming) {
      taskScheduler.scheduleRepeatingEvent(
          nextSlotStartTime.plus(oneThirdSlotSeconds),
          secondsPerSlot,
          this::onAttestationCreationDue);
    }
    taskScheduler.scheduleRepeatingEvent(
        nextSlotStartTime.plus(twoThirdSlotSeconds), secondsPerSlot, this::onAggregationDue);
  }

  private void onStartSlot(final UInt64 scheduledTime, final UInt64 actualTime) {
    final UInt64 slot = spec.getCurrentSlot(scheduledTime, genesisTime);
    if (isTooLate(scheduledTime, actualTime)) {
      LOG.warn(
          "Skipping block creation for slot {} due to unexpected delay in slot processing", slot);
      return;
    }
    validatorTimingChannel.onSlot(slot);
    validatorTimingChannel.onBlockProductionDue(slot);
  }

  private void onAttestationCreationDue(final UInt64 scheduledTime, final UInt64 actualTime) {
    final UInt64 slot = spec.getCurrentSlot(scheduledTime, genesisTime);
    if (isTooLate(scheduledTime, actualTime)) {
      LOG.warn("Skipping attestation for slot {} due to unexpected delay in slot processing", slot);
      return;
    }
    validatorTimingChannel.onAttestationCreationDue(slot);
  }

  private void onAggregationDue(final UInt64 scheduledTime, final UInt64 actualTime) {
    final UInt64 slot = spec.getCurrentSlot(scheduledTime, genesisTime);
    if (isTooLate(scheduledTime, actualTime)) {
      LOG.warn("Skipping aggregation for slot {} due to unexpected delay in slot processing", slot);
      return;
    }
    validatorTimingChannel.onAttestationAggregationDue(slot);
  }

  private boolean isTooLate(final UInt64 scheduledTime, final UInt64 actualTime) {
    final UInt64 currentSlot = spec.getCurrentSlot(timeProvider.getTimeInSeconds(), genesisTime);
    final UInt64 secondsPerSlot = UInt64.valueOf(spec.getSecondsPerSlot(currentSlot));
    return scheduledTime.plus(secondsPerSlot).isLessThan(actualTime);
  }

  @Override
  public SafeFuture<Void> start() {
    // Don't wait for the genesis time to be available before considering startup complete
    // The beacon node may not be available or genesis may not yet be known.
    genesisDataProvider.getGenesisTime().thenAccept(this::start).reportExceptions();
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> stop() {
    return SafeFuture.COMPLETE;
  }
}
