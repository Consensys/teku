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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class Phase0TimeBasedEventAdapter extends TimeBasedEventAdapter {

  public Phase0TimeBasedEventAdapter(
      final RepeatingTaskScheduler taskScheduler,
      final TimeProvider timeProvider,
      final ValidatorTimingChannel validatorTimingChannel,
      final Runnable onLastSlot,
      final Spec spec) {
    super(UInt64.ZERO, taskScheduler, timeProvider, validatorTimingChannel, onLastSlot, spec);
  }

  @Override
  void scheduleDuties(
      final UInt64 nextSlotStartTimeMillis, final Optional<UInt64> expirationTimeMillis) {

    scheduleAll(
        nextSlotStartTimeMillis,
        expirationTimeMillis,
        Optional.of(onLastSlot),
        new ScheduledEvent(0, this::onStartSlot));

    scheduleAll(
        nextSlotStartTimeMillis,
        expirationTimeMillis,
        Optional.empty(),
        new ScheduledEvent(
            spec.getAttestationDueMillis(getFirstSlot()), this::onAttestationCreationDue),
        new ScheduledEvent(spec.getAggregateDueMillis(getFirstSlot()), this::onAggregationDue));
  }
}
