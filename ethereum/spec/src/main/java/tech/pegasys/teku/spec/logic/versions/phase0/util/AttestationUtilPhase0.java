/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.phase0.util;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.attestation.AttestationWorthinessChecker;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class AttestationUtilPhase0 extends AttestationUtil {

  private static final UInt64 MAX_FUTURE_SLOT_ALLOWANCE = UInt64.valueOf(3);

  public AttestationUtilPhase0(
      final SpecConfig specConfig,
      final SchemaDefinitions schemaDefinitions,
      final BeaconStateAccessors beaconStateAccessors,
      final MiscHelpers miscHelpers) {
    super(specConfig, schemaDefinitions, beaconStateAccessors, miscHelpers);
  }

  @Override
  public AttestationWorthinessChecker createAttestationWorthinessChecker(final BeaconState state) {
    return AttestationWorthinessChecker.NOOP;
  }

  /**
   * [IGNORE] attestation.data.slot is within the last ATTESTATION_PROPAGATION_SLOT_RANGE slots
   * (with a MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. attestation.data.slot +
   * ATTESTATION_PROPAGATION_SLOT_RANGE >= current_slot >= attestation.data.slot (a client MAY queue
   * future attestations for processing at the appropriate slot).
   */
  @Override
  public Optional<SlotInclusionGossipValidationResult> performSlotInclusionGossipValidation(
      final Attestation attestation, final UInt64 genesisTime, final UInt64 currentTimeMillis) {
    final UInt64 attestationSlot = attestation.getData().getSlot();
    if (isCurrentTimeAfterAttestationPropagationSlotRange(
            attestationSlot, genesisTime, currentTimeMillis)
        || isFromFarFuture(attestation, genesisTime, currentTimeMillis)) {
      return Optional.of(SlotInclusionGossipValidationResult.IGNORE);
    }
    if (isCurrentTimeBeforeMinimumAttestationBroadcastTime(
        attestationSlot, genesisTime, currentTimeMillis)) {
      return Optional.of(SlotInclusionGossipValidationResult.SAVE_FOR_FUTURE);
    }
    return Optional.empty();
  }

  protected boolean isFromFarFuture(
      final Attestation attestation, final UInt64 genesisTime, final UInt64 currentTimeMillis) {
    final UInt64 attestationForkChoiceEligibleTimeMillis =
        secondsToMillis(
            genesisTime.plus(
                attestation
                    .getData()
                    .getEarliestSlotForForkChoice(miscHelpers)
                    .times(specConfig.getSecondsPerSlot())));
    final UInt64 discardAttestationsAfterMillis =
        currentTimeMillis.plus(
            secondsToMillis(MAX_FUTURE_SLOT_ALLOWANCE.times(specConfig.getSecondsPerSlot())));
    return attestationForkChoiceEligibleTimeMillis.isGreaterThan(discardAttestationsAfterMillis);
  }

  @VisibleForTesting
  boolean isCurrentTimeAfterAttestationPropagationSlotRange(
      final UInt64 attestationSlot, final UInt64 genesisTime, final UInt64 currentTimeMillis) {
    return maximumBroadcastTimeMillis(attestationSlot, genesisTime).isLessThan(currentTimeMillis);
  }

  @VisibleForTesting
  boolean isCurrentTimeBeforeMinimumAttestationBroadcastTime(
      final UInt64 attestationSlot, final UInt64 genesisTime, final UInt64 currentTimeMillis) {
    final UInt64 minimumBroadcastTimeMillis =
        minimumBroadcastTimeMillis(attestationSlot, genesisTime);
    return currentTimeMillis.isLessThan(minimumBroadcastTimeMillis);
  }

  private UInt64 maximumBroadcastTimeMillis(
      final UInt64 attestationSlot, final UInt64 genesisTime) {
    final UInt64 lastAllowedSlot =
        attestationSlot.plus(specConfig.getAttestationPropagationSlotRange());
    // The last allowed time is the end of the lastAllowedSlot (hence the plus 1).
    final UInt64 lastAllowedTime =
        genesisTime.plus(lastAllowedSlot.plus(ONE).times(specConfig.getSecondsPerSlot()));
    // Add allowed clock disparity
    return secondsToMillis(lastAllowedTime).plus(specConfig.getMaximumGossipClockDisparity());
  }

  private UInt64 minimumBroadcastTimeMillis(
      final UInt64 attestationSlot, final UInt64 genesisTime) {
    final UInt64 lastAllowedTime =
        genesisTime.plus(attestationSlot.times(specConfig.getSecondsPerSlot()));
    final UInt64 lastAllowedTimeMillis = secondsToMillis(lastAllowedTime);
    return lastAllowedTimeMillis.isGreaterThanOrEqualTo(specConfig.getMaximumGossipClockDisparity())
        ? lastAllowedTimeMillis.minus(specConfig.getMaximumGossipClockDisparity())
        : ZERO;
  }
}
