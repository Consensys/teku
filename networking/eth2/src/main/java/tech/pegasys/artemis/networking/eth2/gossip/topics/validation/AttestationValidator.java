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

package tech.pegasys.artemis.networking.eth2.gossip.topics.validation;

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_indexed_attestation;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.is_valid_indexed_attestation;
import static tech.pegasys.artemis.networking.eth2.gossip.topics.validation.ValidationResult.INVALID;
import static tech.pegasys.artemis.networking.eth2.gossip.topics.validation.ValidationResult.SAVED_FOR_FUTURE;
import static tech.pegasys.artemis.networking.eth2.gossip.topics.validation.ValidationResult.VALID;
import static tech.pegasys.artemis.util.config.Constants.ATTESTATION_PROPAGATION_SLOT_RANGE;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.artemis.util.config.Constants.VALID_ATTESTATION_SET_SIZE;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.CommitteeUtil;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.util.collections.ConcurrentLimitedSet;
import tech.pegasys.artemis.util.collections.LimitStrategy;
import tech.pegasys.artemis.util.config.Constants;

public class AttestationValidator {

  private static final UnsignedLong MILLIS_PER_SECOND = UnsignedLong.valueOf(1000);
  private static final UnsignedLong MAXIMUM_GOSSIP_CLOCK_DISPARITY =
      UnsignedLong.valueOf(Constants.MAXIMUM_GOSSIP_CLOCK_DISPARITY);

  private final Set<ValidatorAndSlot> receivedValidAttestations =
      ConcurrentLimitedSet.create(
          VALID_ATTESTATION_SET_SIZE, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
  private final RecentChainData recentChainData;

  public AttestationValidator(final RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
  }

  public ValidationResult validate(
      final Attestation attestation, final UnsignedLong receivedOnSubnetId) {
    ValidationResult validationResult = singleAttestationChecks(attestation, receivedOnSubnetId);
    if (validationResult != VALID) {
      return validationResult;
    }

    validationResult = singleOrAggregateAttestationChecks(attestation);
    if (validationResult != VALID) {
      return validationResult;
    }

    return addAndCheckFirstValidAttestation(attestation);
  }

  private ValidationResult addAndCheckFirstValidAttestation(final Attestation attestation) {
    // The attestation is the first valid attestation received for the participating validator for
    // the slot, attestation.data.slot.
    if (!receivedValidAttestations.add(getValidatorAndSlot(attestation))) {
      return INVALID;
    }
    return VALID;
  }

  private ValidationResult singleAttestationChecks(
      final Attestation attestation, final UnsignedLong receivedOnSubnetId) {
    // The attestation's committee index (attestation.data.index) is for the correct subnet.
    if (!CommitteeUtil.getSubnetId(attestation).equals(receivedOnSubnetId)) {
      return INVALID;
    }

    // The attestation is unaggregated -- that is, it has exactly one participating validator
    // (len([bit for bit in attestation.aggregation_bits if bit == 0b1]) == 1).
    if (attestation.getAggregation_bits().countSetBits() != 1) {
      return INVALID;
    }

    // The attestation is the first valid attestation received for the participating validator for
    // the slot, attestation.data.slot.
    if (receivedValidAttestations.contains(getValidatorAndSlot(attestation))) {
      return INVALID;
    }
    return VALID;
  }

  ValidationResult singleOrAggregateAttestationChecks(final Attestation attestation) {
    // attestation.data.slot is within the last ATTESTATION_PROPAGATION_SLOT_RANGE slots (within a
    // MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. attestation.data.slot +
    // ATTESTATION_PROPAGATION_SLOT_RANGE >= current_slot >= attestation.data.slot (a client MAY
    // queue
    // future attestations for processing at the appropriate slot).
    final UnsignedLong currentTimeMillis = secondsToMillis(recentChainData.getStore().getTime());
    if (isAfterPropagationSlotRange(currentTimeMillis, attestation)) {
      return INVALID;
    }
    if (isBeforeMinimumBroadcastTime(attestation, currentTimeMillis)) {
      return SAVED_FOR_FUTURE;
    }

    // The block being voted for (attestation.data.beacon_block_root) passes validation.
    // It must pass validation to be in the store.
    // If it's not in the store, it may not have been processed yet so save for future.
    final Optional<BeaconState> maybeState =
        recentChainData.getBlockState(attestation.getData().getBeacon_block_root());
    if (maybeState.isEmpty()) {
      return SAVED_FOR_FUTURE;
    }

    final BeaconState state = maybeState.get();

    // The signature of attestation is valid.
    final IndexedAttestation indexedAttestation = get_indexed_attestation(state, attestation);
    if (!is_valid_indexed_attestation(state, indexedAttestation)) {
      return INVALID;
    }
    return VALID;
  }

  private ValidatorAndSlot getValidatorAndSlot(final Attestation attestation) {
    return new ValidatorAndSlot(
        attestation.getData().getSlot(),
        attestation.getData().getIndex(),
        attestation.getAggregation_bits().streamAllSetBits().findFirst().orElseThrow());
  }

  private boolean isBeforeMinimumBroadcastTime(
      final Attestation attestation, final UnsignedLong currentTimeMillis) {
    final UnsignedLong minimumBroadcastTimeMillis =
        minimumBroadcastTimeMillis(attestation.getData().getSlot());
    return currentTimeMillis.compareTo(minimumBroadcastTimeMillis) < 0;
  }

  private boolean isAfterPropagationSlotRange(
      final UnsignedLong currentTimeMillis, final Attestation attestation) {
    final UnsignedLong attestationSlot = attestation.getData().getSlot();
    return maximumBroadcastTimeMillis(attestationSlot).compareTo(currentTimeMillis) < 0;
  }

  private UnsignedLong secondsToMillis(final UnsignedLong seconds) {
    return seconds.times(MILLIS_PER_SECOND);
  }

  private UnsignedLong minimumBroadcastTimeMillis(final UnsignedLong attestationSlot) {
    final UnsignedLong lastAllowedTime =
        recentChainData
            .getGenesisTime()
            .plus(attestationSlot.times(UnsignedLong.valueOf(SECONDS_PER_SLOT)));
    final UnsignedLong lastAllowedTimeMillis = secondsToMillis(lastAllowedTime);
    return lastAllowedTimeMillis.compareTo(MAXIMUM_GOSSIP_CLOCK_DISPARITY) >= 0
        ? lastAllowedTimeMillis.minus(MAXIMUM_GOSSIP_CLOCK_DISPARITY)
        : ZERO;
  }

  private UnsignedLong maximumBroadcastTimeMillis(final UnsignedLong attestationSlot) {
    final UnsignedLong lastAllowedSlot = attestationSlot.plus(ATTESTATION_PROPAGATION_SLOT_RANGE);
    // The last allowed time is the end of the lastAllowedSlot (hence the plus 1).
    final UnsignedLong lastAllowedTime =
        recentChainData
            .getGenesisTime()
            .plus(lastAllowedSlot.plus(ONE).times(UnsignedLong.valueOf(SECONDS_PER_SLOT)));

    // Add allowed clock disparity
    return secondsToMillis(lastAllowedTime).plus(MAXIMUM_GOSSIP_CLOCK_DISPARITY);
  }

  private static class ValidatorAndSlot {
    private final UnsignedLong slot;
    // Validator is identified via committee index and position to avoid resolving the actual
    // validator ID before checking for duplicates
    private final UnsignedLong committeeIndex;
    private final int committeePosition;

    private ValidatorAndSlot(
        final UnsignedLong slot, final UnsignedLong committeeIndex, final int committeePosition) {
      this.slot = slot;
      this.committeeIndex = committeeIndex;
      this.committeePosition = committeePosition;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ValidatorAndSlot that = (ValidatorAndSlot) o;
      return committeePosition == that.committeePosition
          && Objects.equals(slot, that.slot)
          && Objects.equals(committeeIndex, that.committeeIndex);
    }

    @Override
    public int hashCode() {
      return Objects.hash(slot, committeeIndex, committeePosition);
    }
  }
}
