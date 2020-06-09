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

package tech.pegasys.teku.networking.eth2.gossip.topics.validation;

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static tech.pegasys.teku.datastructures.util.AttestationUtil.get_indexed_attestation;
import static tech.pegasys.teku.datastructures.util.AttestationUtil.is_valid_indexed_attestation;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.get_beacon_committee;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.REJECT;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_PROPAGATION_SLOT_RANGE;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.teku.util.config.Constants.VALID_ATTESTATION_SET_SIZE;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.collections.ConcurrentLimitedSet;
import tech.pegasys.teku.util.collections.LimitStrategy;
import tech.pegasys.teku.util.config.Constants;

public class AttestationValidator {

  private static final UnsignedLong MAX_FUTURE_SLOT_ALLOWANCE = UnsignedLong.valueOf(3);
  private static final UnsignedLong MILLIS_PER_SECOND = UnsignedLong.valueOf(1000);
  private static final UnsignedLong MAXIMUM_GOSSIP_CLOCK_DISPARITY =
      UnsignedLong.valueOf(Constants.MAXIMUM_GOSSIP_CLOCK_DISPARITY);

  private final Set<ValidatorAndTargetEpoch> receivedValidAttestations =
      ConcurrentLimitedSet.create(
          VALID_ATTESTATION_SET_SIZE, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
  private final RecentChainData recentChainData;

  public AttestationValidator(final RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
  }

  public InternalValidationResult validate(
      final ValidateableAttestation validateableAttestation, final int receivedOnSubnetId) {
    Attestation attestation = validateableAttestation.getAttestation();
    InternalValidationResult internalValidationResult =
        singleAttestationChecks(attestation, receivedOnSubnetId);
    if (internalValidationResult != ACCEPT) {
      return internalValidationResult;
    }

    internalValidationResult = singleOrAggregateAttestationChecks(attestation);
    if (internalValidationResult != ACCEPT) {
      return internalValidationResult;
    }

    return addAndCheckFirstValidAttestation(attestation);
  }

  private InternalValidationResult addAndCheckFirstValidAttestation(final Attestation attestation) {
    // The attestation is the first valid attestation received for the participating validator for
    // the slot, attestation.data.slot.
    if (!receivedValidAttestations.add(getValidatorAndTargetEpoch(attestation))) {
      return IGNORE;
    }
    return ACCEPT;
  }

  private InternalValidationResult singleAttestationChecks(
      final Attestation attestation, final int receivedOnSubnetId) {
    // The attestation's committee index (attestation.data.index) is for the correct subnet.
    if (CommitteeUtil.getSubnetId(attestation) != receivedOnSubnetId) {
      return REJECT;
    }

    // The attestation is unaggregated -- that is, it has exactly one participating validator
    // (len([bit for bit in attestation.aggregation_bits if bit == 0b1]) == 1).
    if (attestation.getAggregation_bits().getBitCount() != 1) {
      return REJECT;
    }

    // The attestation is the first valid attestation received for the participating validator for
    // the slot, attestation.data.slot.
    if (receivedValidAttestations.contains(getValidatorAndTargetEpoch(attestation))) {
      return IGNORE;
    }
    return ACCEPT;
  }

  InternalValidationResult singleOrAggregateAttestationChecks(final Attestation attestation) {
    // attestation.data.slot is within the last ATTESTATION_PROPAGATION_SLOT_RANGE slots (within a
    // MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. attestation.data.slot +
    // ATTESTATION_PROPAGATION_SLOT_RANGE >= current_slot >= attestation.data.slot (a client MAY
    // queue
    // future attestations for processing at the appropriate slot).
    final UnsignedLong currentTimeMillis = secondsToMillis(recentChainData.getStore().getTime());
    if (isCurrentTimeAfterAttestationPropagationSlotRange(currentTimeMillis, attestation)
        || isFromFarFuture(attestation, currentTimeMillis)) {
      return IGNORE;
    }
    if (isCurrentTimeBeforeMinimumAttestationBroadcastTime(attestation, currentTimeMillis)) {
      return SAVE_FOR_FUTURE;
    }

    // The block being voted for (attestation.data.beacon_block_root) passes validation.
    // It must pass validation to be in the store.
    // If it's not in the store, it may not have been processed yet so save for future.
    final Optional<BeaconState> maybeState =
        recentChainData.getBlockState(attestation.getData().getBeacon_block_root());
    if (maybeState.isEmpty()) {
      return SAVE_FOR_FUTURE;
    }

    final BeaconState state = maybeState.get();

    final List<Integer> committee =
        get_beacon_committee(
            state, attestation.getData().getSlot(), attestation.getData().getIndex());
    if (committee.size() != attestation.getAggregation_bits().getCurrentSize()) {
      return REJECT;
    }

    // The signature of attestation is valid.
    final IndexedAttestation indexedAttestation = get_indexed_attestation(state, attestation);
    if (!is_valid_indexed_attestation(state, indexedAttestation).isSuccessful()) {
      return REJECT;
    }
    return ACCEPT;
  }

  private ValidatorAndTargetEpoch getValidatorAndTargetEpoch(final Attestation attestation) {
    return new ValidatorAndTargetEpoch(
        attestation.getData().getTarget().getEpoch(),
        attestation.getData().getIndex(),
        attestation.getAggregation_bits().streamAllSetBits().findFirst().orElseThrow());
  }

  private boolean isCurrentTimeBeforeMinimumAttestationBroadcastTime(
      final Attestation attestation, final UnsignedLong currentTimeMillis) {
    final UnsignedLong minimumBroadcastTimeMillis =
        minimumBroadcastTimeMillis(attestation.getData().getSlot());
    return currentTimeMillis.compareTo(minimumBroadcastTimeMillis) < 0;
  }

  private boolean isFromFarFuture(
      final Attestation attestation, final UnsignedLong currentTimeMillis) {
    final UnsignedLong attestationSlotTimeMillis =
        secondsToMillis(
            recentChainData
                .getGenesisTime()
                .plus(
                    attestation
                        .getEarliestSlotForForkChoiceProcessing()
                        .times(UnsignedLong.valueOf(SECONDS_PER_SLOT))));
    final UnsignedLong discardAttestationsAfterMillis =
        currentTimeMillis.plus(
            secondsToMillis(
                MAX_FUTURE_SLOT_ALLOWANCE.times(UnsignedLong.valueOf(SECONDS_PER_SLOT))));
    return attestationSlotTimeMillis.compareTo(discardAttestationsAfterMillis) > 0;
  }

  private boolean isCurrentTimeAfterAttestationPropagationSlotRange(
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

  private static class ValidatorAndTargetEpoch {
    private final UnsignedLong targetEpoch;
    // Validator is identified via committee index and position to avoid resolving the actual
    // validator ID before checking for duplicates
    private final UnsignedLong committeeIndex;
    private final int committeePosition;

    private ValidatorAndTargetEpoch(
        final UnsignedLong targetEpoch,
        final UnsignedLong committeeIndex,
        final int committeePosition) {
      this.targetEpoch = targetEpoch;
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
      final ValidatorAndTargetEpoch that = (ValidatorAndTargetEpoch) o;
      return committeePosition == that.committeePosition
          && Objects.equals(targetEpoch, that.targetEpoch)
          && Objects.equals(committeeIndex, that.committeeIndex);
    }

    @Override
    public int hashCode() {
      return Objects.hash(targetEpoch, committeeIndex, committeePosition);
    }
  }
}
