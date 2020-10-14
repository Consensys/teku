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

import static tech.pegasys.teku.core.ForkChoiceUtil.getCurrentSlot;
import static tech.pegasys.teku.core.ForkChoiceUtil.getSlotStartTime;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class TimeBasedEventAdapter implements BeaconChainEventAdapter {
  private static final Logger LOG = LogManager.getLogger();
  private final long oneThirdSlotSeconds = SECONDS_PER_SLOT / 3;
  private final long twoThirdSlotSeconds = oneThirdSlotSeconds * 2;

  private final GenesisTimeProvider genesisTimeProvider;
  private final RepeatingTaskScheduler taskScheduler;
  private final TimeProvider timeProvider;
  private final ValidatorTimingChannel validatorTimingChannel;
  private UInt64 genesisTime;

  public TimeBasedEventAdapter(
      final GenesisTimeProvider genesisTimeProvider,
      final RepeatingTaskScheduler taskScheduler,
      final TimeProvider timeProvider,
      final ValidatorTimingChannel validatorTimingChannel) {
    this.genesisTimeProvider = genesisTimeProvider;
    this.taskScheduler = taskScheduler;
    this.timeProvider = timeProvider;
    this.validatorTimingChannel = validatorTimingChannel;
  }

  void start(final UInt64 genesisTime) {
    this.genesisTime = genesisTime;
    final UInt64 currentSlot = getCurrentSlot(timeProvider.getTimeInSeconds(), genesisTime);
    final UInt64 nextSlotStartTime = getSlotStartTime(currentSlot.plus(1), genesisTime);
    taskScheduler.scheduleRepeatingEvent(
        nextSlotStartTime, UInt64.valueOf(SECONDS_PER_SLOT), this::onStartSlot);
    taskScheduler.scheduleRepeatingEvent(
        nextSlotStartTime.plus(twoThirdSlotSeconds),
        UInt64.valueOf(SECONDS_PER_SLOT),
        this::onAggregationDue);
  }

  private void onStartSlot(final UInt64 scheduledTime, final UInt64 actualTime) {
    final UInt64 slot = getCurrentSlot(scheduledTime, genesisTime);
    if (isTooLate(scheduledTime, actualTime)) {
      LOG.warn(
          "Skipping block creation for slot {} due to unexpected delay in slot processing", slot);
      return;
    }
    validatorTimingChannel.onSlot(slot);
    validatorTimingChannel.onBlockProductionDue(slot);
  }

  private void onAggregationDue(final UInt64 scheduledTime, final UInt64 actualTime) {
    final UInt64 slot = getCurrentSlot(scheduledTime, genesisTime);
    if (isTooLate(scheduledTime, actualTime)) {
      LOG.warn("Skipping aggregation for slot {} due to unexpected delay in slot processing", slot);
      return;
    }
    validatorTimingChannel.onAttestationAggregationDue(slot);
  }

  private boolean isTooLate(final UInt64 scheduledTime, final UInt64 actualTime) {
    return scheduledTime.plus(SECONDS_PER_SLOT).isLessThan(actualTime);
  }

  @Override
  public SafeFuture<Void> start() {
    // Don't wait for the genesis time to be available before considering startup complete
    // The beacon node may not be available or genesis may not yet be known.
    genesisTimeProvider.getGenesisTime().thenAccept(this::start).reportExceptions();
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> stop() {
    return SafeFuture.COMPLETE;
  }
}
