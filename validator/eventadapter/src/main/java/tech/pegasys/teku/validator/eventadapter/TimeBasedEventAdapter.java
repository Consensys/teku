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

package tech.pegasys.teku.validator.eventadapter;

import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import tech.pegasys.teku.core.ForkChoiceUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.time.TimeProvider;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class TimeBasedEventAdapter implements BeaconChainEventAdapter {
  private final long oneThirdSlotSeconds = SECONDS_PER_SLOT / 3;
  private final long twoThirdSlotSeconds = oneThirdSlotSeconds * 2;

  private final GenesisTimeProvider genesisTimeProvider;
  private final TimedEventQueue timedEventQueue;
  private final TimeProvider timeProvider;
  private final ValidatorTimingChannel validatorTimingChannel;

  public TimeBasedEventAdapter(
      final GenesisTimeProvider genesisTimeProvider,
      final TimedEventQueue timedEventQueue,
      final TimeProvider timeProvider,
      final ValidatorTimingChannel validatorTimingChannel) {
    this.genesisTimeProvider = genesisTimeProvider;
    this.timedEventQueue = timedEventQueue;
    this.timeProvider = timeProvider;
    this.validatorTimingChannel = validatorTimingChannel;
  }

  void start(final UInt64 genesisTime) {
    final UInt64 minimumSlot = UInt64.ZERO;
    scheduleNextDueSlot(genesisTime, minimumSlot);
  }

  private void scheduleNextDueSlot(final UInt64 genesisTime, final UInt64 minimumSlot) {
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    final UInt64 currentSlot = ForkChoiceUtil.getCurrentSlot(currentTime, genesisTime);
    final UInt64 nextSlotToSchedule = currentSlot.max(minimumSlot);
    if (nextSlotToSchedule.isGreaterThan(minimumSlot)) {
      EVENT_LOG.nodeSlotsMissed(minimumSlot, nextSlotToSchedule);
    }

    scheduleSlot(genesisTime, nextSlotToSchedule);
  }

  private void scheduleSlot(final UInt64 genesisTime, final UInt64 slot) {
    final UInt64 slotStartTime = ForkChoiceUtil.getSlotStartTime(slot, genesisTime);
    timedEventQueue.scheduleEvent(
        slotStartTime,
        () -> {
          try {
            validatorTimingChannel.onSlot(slot);
            validatorTimingChannel.onBlockProductionDue(slot);
          } finally {
            scheduleNextDueSlot(genesisTime, slot.increment());
          }
        });
    timedEventQueue.scheduleEvent(
        slotStartTime.plus(twoThirdSlotSeconds),
        () -> validatorTimingChannel.onAttestationAggregationDue(slot));
  }

  @Override
  public SafeFuture<Void> start() {
    return genesisTimeProvider.getGenesisTime().thenAccept(this::start);
  }

  @Override
  public SafeFuture<Void> stop() {
    return SafeFuture.COMPLETE;
  }
}
