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

import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class Phase0TimeBasedEventAdapter extends TimeBasedEventAdapter {

  public Phase0TimeBasedEventAdapter(
      final GenesisDataProvider genesisDataProvider,
      final RepeatingTaskScheduler taskScheduler,
      final TimeProvider timeProvider,
      final ValidatorTimingChannel validatorTimingChannel,
      final Spec spec) {
    super(genesisDataProvider, taskScheduler, timeProvider, validatorTimingChannel, spec);
  }

  @Override
  void start(final UInt64 genesisTime) {
    setGenesisTime(genesisTime);
    final UInt64 currentSlot = getCurrentSlot();

    final UInt64 nextSlotStartTimeMillis =
        spec.computeTimeMillisAtSlot(currentSlot.increment(), genesisTime);
    final UInt64 millisPerSlot = getMillisPerSlot(currentSlot);

    // NOTE: seconds_per_slot currently based on current slot, and timings set up based on this
    //       if seconds_per_slot ever changes, timers would have to be updated, which isn't
    //       currently implemented.

    taskScheduler.scheduleRepeatingEventInMillis(
        nextSlotStartTimeMillis, millisPerSlot, this::onStartSlot);

    final UInt64 attestationDueSlotTimeOffset =
        UInt64.valueOf(spec.getAttestationDueMillis(currentSlot));
    final UInt64 aggregationDueSlotTimeOffset =
        UInt64.valueOf(spec.getAggregateDueMillis(currentSlot));

    scheduleDuty(
        nextSlotStartTimeMillis,
        millisPerSlot,
        attestationDueSlotTimeOffset,
        this::onAttestationCreationDue);
    scheduleDuty(
        nextSlotStartTimeMillis,
        millisPerSlot,
        aggregationDueSlotTimeOffset,
        this::onAggregationDue);
  }
}
