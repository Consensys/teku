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

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.spec.constants.NetworkConstants.INTERVALS_PER_SLOT;
import static tech.pegasys.teku.spec.constants.NetworkConstants.INTERVALS_PER_SLOT_EIP7732;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class Eip7732TimeBasedEventAdapter extends TimeBasedEventAdapter {
  private static final Logger LOG = LogManager.getLogger();

  private UInt64 genesisTime;

  public Eip7732TimeBasedEventAdapter(
      final GenesisDataProvider genesisDataProvider,
      final RepeatingTaskScheduler taskScheduler,
      final TimeProvider timeProvider,
      final ValidatorTimingChannel validatorTimingChannel,
      final Spec spec) {
    super(genesisDataProvider, taskScheduler, timeProvider, validatorTimingChannel, spec);
  }

  @Override
  void start(final UInt64 genesisTime) {
    this.genesisTime = genesisTime;
    final UInt64 currentSlot = getCurrentSlot();

    final UInt64 nextSlotStartTime = spec.getSlotStartTime(currentSlot.plus(1), genesisTime);
    final UInt64 secondsPerSlot = getSecondsPerSlot(currentSlot);

    // NOTE: seconds_per_slot currently based on genesis slot, and timings set up based on this
    //       if seconds_per_slot ever changes, timers would have to be updated, which isn't
    //       currently implemented.

    taskScheduler.scheduleRepeatingEvent(nextSlotStartTime, secondsPerSlot, this::onStartSlot);

    final UInt64 nextSlotStartTimeMillis = secondsToMillis(nextSlotStartTime);

    final UInt64 millisPerSlot = secondsToMillis(secondsPerSlot);

    final UInt64 eip7732AttestationDueSlotTimeOffset =
        millisPerSlot.dividedBy(INTERVALS_PER_SLOT_EIP7732);
    final UInt64 eip7732AggregationDueSlotTimeOffset =
        millisPerSlot.times(2).dividedBy(INTERVALS_PER_SLOT_EIP7732);
    final UInt64 timelinessAttestationDueSlotTimeOffset =
        millisPerSlot.times(3).dividedBy(INTERVALS_PER_SLOT_EIP7732);

    // we are in EIP7732 already, don't need to start and expire old duties, schedule 7732 only
    if (isEip7732Started(currentSlot)) {
      startDutiesInEip7732(
          millisPerSlot,
          nextSlotStartTimeMillis,
          eip7732AttestationDueSlotTimeOffset,
          eip7732AggregationDueSlotTimeOffset,
          timelinessAttestationDueSlotTimeOffset);
      return;
    }

    // otherwise we should start and schedule expiration for phase0 duties first
    // and start EIP7732 duties only when EIP7732 is started
    final UInt64 eip7732StartTimeMillis =
        secondsToMillis(
            spec.getSlotStartTime(
                spec.computeStartSlotAtEpoch(
                    spec.getForkSchedule().getFork(SpecMilestone.EIP7732).getEpoch()),
                genesisTime));

    final UInt64 attestationDueSlotTimeOffset = millisPerSlot.dividedBy(INTERVALS_PER_SLOT);
    final UInt64 aggregationDueSlotTimeOffset =
        millisPerSlot.times(2).dividedBy(INTERVALS_PER_SLOT);

    taskScheduler.scheduleRepeatingEventInMillis(
        nextSlotStartTimeMillis.plus(attestationDueSlotTimeOffset),
        millisPerSlot,
        this::onAttestationCreationDue,
        eip7732StartTimeMillis,
        (__, ___) ->
            scheduleDuty(
                millisPerSlot,
                eip7732AttestationDueSlotTimeOffset,
                this::onAttestationCreationDue));
    taskScheduler.scheduleRepeatingEventInMillis(
        nextSlotStartTimeMillis.plus(aggregationDueSlotTimeOffset),
        millisPerSlot,
        this::onAggregationDue,
        eip7732StartTimeMillis,
        (__, ___) -> {
          scheduleDuty(millisPerSlot, eip7732AggregationDueSlotTimeOffset, this::onAggregationDue);
          scheduleDuty(
              millisPerSlot,
              timelinessAttestationDueSlotTimeOffset,
              this::onPayloadTimelinessAttestationDue);
        });
  }

  private boolean isEip7732Started(final UInt64 currentSlot) {
    final SpecMilestone currentMilestone = spec.atSlot(currentSlot).getMilestone();

    return currentMilestone.isGreaterThanOrEqualTo(SpecMilestone.EIP7732);
  }

  private void startDutiesInEip7732(
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
    LOG.info("EVENT *** onPayloadTimelinessAttestationDue");
    validatorTimingChannel.onPayloadAttestationDue(slot);
  }

  @Override
  UInt64 getGenesisTime() {
    return genesisTime;
  }
}
