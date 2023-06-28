/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.spec.logic.versions.deneb.util;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.millisToSeconds;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.altair.util.AttestationUtilAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class AttestationUtilDeneb extends AttestationUtilAltair {

  public AttestationUtilDeneb(
      final SpecConfig specConfig,
      final SchemaDefinitions schemaDefinitions,
      final BeaconStateAccessors beaconStateAccessors,
      final MiscHelpers miscHelpers) {
    super(specConfig, schemaDefinitions, beaconStateAccessors, miscHelpers);
  }

  /**
   *
   *
   * <ul>
   *   <li>[IGNORE] attestation.data.slot is equal to or earlier than the current_slot (with a
   *       MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. attestation.data.slot <= current_slot
   *       (a client MAY queue future attestation for processing at the appropriate slot).
   *   <li>[IGNORE] the epoch of attestation.data.slot is either the current or previous epoch (with
   *       a MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e.
   *       compute_epoch_at_slot(attestation.data.slot) in (get_previous_epoch(state),
   *       get_current_epoch(state))
   * </ul>
   */
  @Override
  public Optional<SlotInclusionGossipValidationResult> performSlotInclusionGossipValidation(
      final Attestation attestation, final UInt64 genesisTime, final UInt64 currentTimeMillis) {
    final UInt64 attestationSlot = attestation.getData().getSlot();
    if (isAttestationSlotAfterCurrentTime(attestationSlot, genesisTime, currentTimeMillis)
        && isFromFarFuture(attestation, genesisTime, currentTimeMillis)) {
      return Optional.of(SlotInclusionGossipValidationResult.IGNORE);
    }
    if (!isAttestationSlotCurrentOrPreviousEpoch(attestationSlot, genesisTime, currentTimeMillis)) {
      return Optional.of(SlotInclusionGossipValidationResult.IGNORE);
    }
    // attestation is after current time, but not from the far future, so can save for future
    // processing
    return Optional.of(SlotInclusionGossipValidationResult.SAVE_FOR_FUTURE);
  }

  private boolean isAttestationSlotAfterCurrentTime(
      final UInt64 attestationSlot, final UInt64 genesisTime, final UInt64 currentTimeMillis) {
    final UInt64 attestationTimeMillis =
        secondsToMillis(genesisTime.plus(attestationSlot.times(specConfig.getSecondsPerSlot())));
    return attestationTimeMillis.isGreaterThan(
        currentTimeMillis.plus(specConfig.getMaximumGossipClockDisparity()));
  }

  private boolean isAttestationSlotCurrentOrPreviousEpoch(
      final UInt64 attestationSlot, final UInt64 genesisTime, final UInt64 currentTimeMillis) {
    final UInt64 currentTimeWithDisparity =
        millisToSeconds(currentTimeMillis.plus(specConfig.getMaximumGossipClockDisparity()));
    final UInt64 currentEpoch =
        miscHelpers.computeEpochAtTime(genesisTime, currentTimeWithDisparity);
    final UInt64 attestationEpoch = miscHelpers.computeEpochAtSlot(attestationSlot);
    return attestationEpoch.equals(currentEpoch)
        || attestationEpoch.equals(currentEpoch.minusMinZero(1));
  }
}
