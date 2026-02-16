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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class GloasTimeBasedEventAdapter extends TimeBasedEventAdapter {
  private static final Logger LOG = LogManager.getLogger();

  public GloasTimeBasedEventAdapter(
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

    // NOTE: seconds_per_slot currently based on genesis slot, and timings set up based on this
    //       if seconds_per_slot ever changes, timers would have to be updated, which isn't
    //       currently implemented.

    final UInt64 nextSlotStartTimeMillis =
        spec.computeTimeMillisAtSlot(currentSlot.increment(), genesisTime);
    final UInt64 millisPerSlot = getMillisPerSlot(currentSlot);
    taskScheduler.scheduleRepeatingEventInMillis(
        nextSlotStartTimeMillis, millisPerSlot, this::onStartSlot);

    final UInt64 firstGloasSlot =
        spec.computeStartSlotAtEpoch(
            spec.getForkSchedule().getFork(SpecMilestone.GLOAS).getEpoch());
    final UInt64 gloasAttestationDueSlotTimeOffset =
        UInt64.valueOf(spec.getAttestationDueMillis(firstGloasSlot));
    final UInt64 gloasAggregationDueSlotTimeOffset =
        UInt64.valueOf(spec.getAggregateDueMillis(firstGloasSlot));
    final UInt64 timelinessAttestationDueSlotTimeOffset =
        UInt64.valueOf(spec.getPayloadAttestationDueMillis(firstGloasSlot));

    // we are in Gloas already, don't need to start and expire old duties, schedule 7732 only
    if (isGloasStarted(currentSlot)) {
      startDutiesInGloas(
          millisPerSlot,
          nextSlotStartTimeMillis,
          gloasAttestationDueSlotTimeOffset,
          gloasAggregationDueSlotTimeOffset,
          timelinessAttestationDueSlotTimeOffset);
      return;
    }

    // otherwise we should start and schedule expiration for phase0 duties first
    // and start Gloas duties only when Gloas is started
    final UInt64 gloasStartTimeMillis = spec.computeTimeMillisAtSlot(firstGloasSlot, genesisTime);

    final UInt64 attestationDueSlotTimeOffset =
        UInt64.valueOf(spec.getAttestationDueMillis(currentSlot));
    final UInt64 aggregationDueSlotTimeOffset =
        UInt64.valueOf(spec.getAggregateDueMillis(currentSlot));

    taskScheduler.scheduleRepeatingEventInMillis(
        nextSlotStartTimeMillis.plus(attestationDueSlotTimeOffset),
        millisPerSlot,
        this::onAttestationCreationDue,
        gloasStartTimeMillis,
        (__, ___) ->
            scheduleDuty(
                millisPerSlot, gloasAttestationDueSlotTimeOffset, this::onAttestationCreationDue));
    taskScheduler.scheduleRepeatingEventInMillis(
        nextSlotStartTimeMillis.plus(aggregationDueSlotTimeOffset),
        millisPerSlot,
        this::onAggregationDue,
        gloasStartTimeMillis,
        (__, ___) -> {
          scheduleDuty(millisPerSlot, gloasAggregationDueSlotTimeOffset, this::onAggregationDue);
          scheduleDuty(
              millisPerSlot,
              timelinessAttestationDueSlotTimeOffset,
              this::onPayloadTimelinessAttestationDue);
        });
  }

  private boolean isGloasStarted(final UInt64 currentSlot) {
    final SpecMilestone currentMilestone = spec.atSlot(currentSlot).getMilestone();

    return currentMilestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS);
  }

  private void startDutiesInGloas(
      final UInt64 millisPerSlot,
      final UInt64 nextSlotStartTimeMillis,
      final UInt64 attestationDueSlotTimeOffset,
      final UInt64 aggregationDueSlotTimeOffset,
      final UInt64 timelinessAttestationDueSlotTimeOffset) {
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
    scheduleDuty(
        nextSlotStartTimeMillis,
        millisPerSlot,
        timelinessAttestationDueSlotTimeOffset,
        this::onPayloadTimelinessAttestationDue);
  }

  private void onPayloadTimelinessAttestationDue(
      final UInt64 scheduledTimeInMillis, final UInt64 actualTimeInMillis) {
    final UInt64 slot = getCurrentSlotForMillis(scheduledTimeInMillis);
    if (isTooLateInMillis(scheduledTimeInMillis, actualTimeInMillis)) {
      LOG.warn(
          "Skipping timeliness attestation for slot {} due to unexpected delay in slot processing",
          slot);
      return;
    }
    validatorTimingChannel.onPayloadAttestationCreationDue(slot);
  }
}
