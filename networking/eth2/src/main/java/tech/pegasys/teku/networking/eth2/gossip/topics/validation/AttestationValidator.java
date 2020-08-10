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

import static tech.pegasys.teku.datastructures.util.AttestationUtil.get_indexed_attestation;
import static tech.pegasys.teku.datastructures.util.AttestationUtil.is_valid_indexed_attestation;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.computeSubnetForAttestation;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.get_beacon_committee;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.REJECT;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_PROPAGATION_SLOT_RANGE;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.teku.util.config.Constants.VALID_ATTESTATION_SET_SIZE;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.collections.ConcurrentLimitedSet;
import tech.pegasys.teku.util.collections.LimitStrategy;
import tech.pegasys.teku.util.config.Constants;

public class AttestationValidator {

  private static final UInt64 MAX_FUTURE_SLOT_ALLOWANCE = UInt64.valueOf(3);
  private static final UInt64 MILLIS_PER_SECOND = UInt64.valueOf(1000);
  private static final UInt64 MAXIMUM_GOSSIP_CLOCK_DISPARITY =
      UInt64.valueOf(Constants.MAXIMUM_GOSSIP_CLOCK_DISPARITY);

  private final Set<ValidatorAndTargetEpoch> receivedValidAttestations =
      ConcurrentLimitedSet.create(
          VALID_ATTESTATION_SET_SIZE, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
  private final RecentChainData recentChainData;

  public AttestationValidator(final RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
  }

  public SafeFuture<InternalValidationResult> validate(
      final ValidateableAttestation validateableAttestation, final int receivedOnSubnetId) {
    Attestation attestation = validateableAttestation.getAttestation();
    final InternalValidationResult internalValidationResult = singleAttestationChecks(attestation);
    if (internalValidationResult != ACCEPT) {
      return SafeFuture.completedFuture(internalValidationResult);
    }

    return singleOrAggregateAttestationChecks(attestation, OptionalInt.of(receivedOnSubnetId))
        .thenApply(
            result -> {
              if (result != ACCEPT) {
                return result;
              }

              return addAndCheckFirstValidAttestation(attestation);
            });
  }

  private InternalValidationResult addAndCheckFirstValidAttestation(final Attestation attestation) {
    // The attestation is the first valid attestation received for the participating validator for
    // the slot, attestation.data.slot.
    if (!receivedValidAttestations.add(getValidatorAndTargetEpoch(attestation))) {
      return IGNORE;
    }
    return ACCEPT;
  }

  private InternalValidationResult singleAttestationChecks(final Attestation attestation) {
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

  SafeFuture<InternalValidationResult> singleOrAggregateAttestationChecks(
      final Attestation attestation, final OptionalInt receivedOnSubnetId) {
    // attestation.data.slot is within the last ATTESTATION_PROPAGATION_SLOT_RANGE slots (within a
    // MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. attestation.data.slot +
    // ATTESTATION_PROPAGATION_SLOT_RANGE >= current_slot >= attestation.data.slot (a client MAY
    // queue
    // future attestations for processing at the appropriate slot).
    final UInt64 currentTimeMillis = secondsToMillis(recentChainData.getStore().getTime());
    if (isCurrentTimeAfterAttestationPropagationSlotRange(currentTimeMillis, attestation)
        || isFromFarFuture(attestation, currentTimeMillis)) {
      return SafeFuture.completedFuture(IGNORE);
    }
    if (isCurrentTimeBeforeMinimumAttestationBroadcastTime(attestation, currentTimeMillis)) {
      return SafeFuture.completedFuture(SAVE_FOR_FUTURE);
    }

    // The block being voted for (attestation.data.beacon_block_root) passes validation.
    // It must pass validation to be in the store.
    // If it's not in the store, it may not have been processed yet so save for future.
    return recentChainData
        .retrieveBlockState(attestation.getData().getBeacon_block_root())
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                return SAVE_FOR_FUTURE;
              }

              final BeaconState state = maybeState.get();

              // The attestation's committee index (attestation.data.index) is for the correct
              // subnet.
              if (receivedOnSubnetId.isPresent()
                  && computeSubnetForAttestation(state, attestation)
                      != receivedOnSubnetId.getAsInt()) {
                return REJECT;
              }

              final List<Integer> committee =
                  get_beacon_committee(
                      state, attestation.getData().getSlot(), attestation.getData().getIndex());
              if (committee.size() != attestation.getAggregation_bits().getCurrentSize()) {
                return REJECT;
              }

              // The signature of attestation is valid.
              final IndexedAttestation indexedAttestation =
                  get_indexed_attestation(state, attestation);
              if (!is_valid_indexed_attestation(state, indexedAttestation).isSuccessful()) {
                return REJECT;
              }
              return ACCEPT;
            });
  }

  private ValidatorAndTargetEpoch getValidatorAndTargetEpoch(final Attestation attestation) {
    return new ValidatorAndTargetEpoch(
        attestation.getData().getTarget().getEpoch(),
        attestation.getData().getIndex(),
        attestation.getAggregation_bits().streamAllSetBits().findFirst().orElseThrow());
  }

  private boolean isCurrentTimeBeforeMinimumAttestationBroadcastTime(
      final Attestation attestation, final UInt64 currentTimeMillis) {
    final UInt64 minimumBroadcastTimeMillis =
        minimumBroadcastTimeMillis(attestation.getData().getSlot());
    return currentTimeMillis.compareTo(minimumBroadcastTimeMillis) < 0;
  }

  private boolean isFromFarFuture(final Attestation attestation, final UInt64 currentTimeMillis) {
    final UInt64 attestationSlotTimeMillis =
        secondsToMillis(
            recentChainData
                .getGenesisTime()
                .plus(
                    attestation
                        .getEarliestSlotForForkChoiceProcessing()
                        .times(UInt64.valueOf(SECONDS_PER_SLOT))));
    final UInt64 discardAttestationsAfterMillis =
        currentTimeMillis.plus(
            secondsToMillis(MAX_FUTURE_SLOT_ALLOWANCE.times(UInt64.valueOf(SECONDS_PER_SLOT))));
    return attestationSlotTimeMillis.compareTo(discardAttestationsAfterMillis) > 0;
  }

  private boolean isCurrentTimeAfterAttestationPropagationSlotRange(
      final UInt64 currentTimeMillis, final Attestation attestation) {
    final UInt64 attestationSlot = attestation.getData().getSlot();
    return maximumBroadcastTimeMillis(attestationSlot).compareTo(currentTimeMillis) < 0;
  }

  private UInt64 secondsToMillis(final UInt64 seconds) {
    return seconds.times(MILLIS_PER_SECOND);
  }

  private UInt64 minimumBroadcastTimeMillis(final UInt64 attestationSlot) {
    final UInt64 lastAllowedTime =
        recentChainData
            .getGenesisTime()
            .plus(attestationSlot.times(UInt64.valueOf(SECONDS_PER_SLOT)));
    final UInt64 lastAllowedTimeMillis = secondsToMillis(lastAllowedTime);
    return lastAllowedTimeMillis.compareTo(MAXIMUM_GOSSIP_CLOCK_DISPARITY) >= 0
        ? lastAllowedTimeMillis.minus(MAXIMUM_GOSSIP_CLOCK_DISPARITY)
        : ZERO;
  }

  private UInt64 maximumBroadcastTimeMillis(final UInt64 attestationSlot) {
    final UInt64 lastAllowedSlot = attestationSlot.plus(ATTESTATION_PROPAGATION_SLOT_RANGE);
    // The last allowed time is the end of the lastAllowedSlot (hence the plus 1).
    final UInt64 lastAllowedTime =
        recentChainData
            .getGenesisTime()
            .plus(lastAllowedSlot.plus(ONE).times(UInt64.valueOf(SECONDS_PER_SLOT)));

    // Add allowed clock disparity
    return secondsToMillis(lastAllowedTime).plus(MAXIMUM_GOSSIP_CLOCK_DISPARITY);
  }

  private static class ValidatorAndTargetEpoch {
    private final UInt64 targetEpoch;
    // Validator is identified via committee index and position to avoid resolving the actual
    // validator ID before checking for duplicates
    private final UInt64 committeeIndex;
    private final int committeePosition;

    private ValidatorAndTargetEpoch(
        final UInt64 targetEpoch, final UInt64 committeeIndex, final int committeePosition) {
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
