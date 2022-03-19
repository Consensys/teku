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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.Constants.ATTESTATION_PROPAGATION_SLOT_RANGE;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ACCEPT;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import java.util.OptionalInt;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttestationValidator {
  private static final UInt64 MAX_FUTURE_SLOT_ALLOWANCE = UInt64.valueOf(3);
  private static final UInt64 MAXIMUM_GOSSIP_CLOCK_DISPARITY =
      UInt64.valueOf(Constants.MAXIMUM_GOSSIP_CLOCK_DISPARITY);

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final AsyncBLSSignatureVerifier signatureVerifier;
  private final AttestationStateSelector stateSelector;

  public AttestationValidator(
      final Spec spec,
      RecentChainData recentChainData,
      AsyncBLSSignatureVerifier signatureVerifier) {
    this.recentChainData = recentChainData;
    this.spec = spec;
    this.signatureVerifier = signatureVerifier;
    this.stateSelector = new AttestationStateSelector(spec, recentChainData);
  }

  public SafeFuture<InternalValidationResult> validate(
      final ValidateableAttestation validateableAttestation) {
    Attestation attestation = validateableAttestation.getAttestation();
    final InternalValidationResult internalValidationResult = singleAttestationChecks(attestation);
    if (internalValidationResult.code() != ACCEPT) {
      return completedFuture(internalValidationResult);
    }

    return singleOrAggregateAttestationChecks(
            signatureVerifier,
            validateableAttestation,
            validateableAttestation.getReceivedSubnetId())
        .thenApply(Pair::left);
  }

  private InternalValidationResult singleAttestationChecks(final Attestation attestation) {
    // The attestation is unaggregated -- that is, it has exactly one participating validator
    // (len([bit for bit in attestation.aggregation_bits if bit == 0b1]) == 1).
    final int bitCount = attestation.getAggregationBits().getBitCount();
    if (bitCount != 1) {
      return InternalValidationResult.reject("Attestation has %s bits set instead of 1", bitCount);
    }
    return InternalValidationResult.ACCEPT;
  }

  SafeFuture<Pair<InternalValidationResult, Optional<BeaconState>>>
      singleOrAggregateAttestationChecks(
          final AsyncBLSSignatureVerifier signatureVerifier,
          final ValidateableAttestation validateableAttestation,
          final OptionalInt receivedOnSubnetId) {

    Attestation attestation = validateableAttestation.getAttestation();
    final AttestationData data = attestation.getData();
    // The attestation's epoch matches its target
    if (!data.getTarget().getEpoch().equals(spec.computeEpochAtSlot(data.getSlot()))) {
      return completedFuture(
          Pair.of(
              InternalValidationResult.reject(
                  "Attestation slot %s is not from target epoch %s",
                  data.getSlot(), data.getTarget().getEpoch()),
              Optional.empty()));
    }

    // attestation.data.slot is within the last ATTESTATION_PROPAGATION_SLOT_RANGE slots (within a
    // MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. attestation.data.slot +
    // ATTESTATION_PROPAGATION_SLOT_RANGE >= current_slot >= attestation.data.slot (a client MAY
    // queue
    // future attestations for processing at the appropriate slot).
    final UInt64 currentTimeMillis = secondsToMillis(recentChainData.getStore().getTime());
    if (isCurrentTimeAfterAttestationPropagationSlotRange(currentTimeMillis, attestation)
        || isFromFarFuture(attestation, currentTimeMillis)) {
      return completedFuture(Pair.of(InternalValidationResult.IGNORE, Optional.empty()));
    }
    if (isCurrentTimeBeforeMinimumAttestationBroadcastTime(attestation, currentTimeMillis)) {
      return completedFuture(Pair.of(InternalValidationResult.SAVE_FOR_FUTURE, Optional.empty()));
    }

    // The block being voted for (attestation.data.beacon_block_root) passes validation.
    // It must pass validation to be in the store.
    // If it's not in the store, it may not have been processed yet so save for future.
    if (!recentChainData.containsBlock(data.getBeacon_block_root())) {
      return completedFuture(Pair.of(InternalValidationResult.SAVE_FOR_FUTURE, Optional.empty()));
    }

    return stateSelector
        .getStateToValidate(attestation.getData())
        .thenCompose(
            maybeState -> {
              if (maybeState.isEmpty()) {
                // We know the block is imported but now don't have a state to validate against
                // Must have got pruned between checks
                return completedFuture(Pair.of(InternalValidationResult.IGNORE, maybeState));
              }
              final BeaconState state = maybeState.get();
              // The committee index is within the expected range
              if (data.getIndex()
                  .isGreaterThanOrEqualTo(
                      spec.getCommitteeCountPerSlot(state, data.getTarget().getEpoch()))) {
                return completedFuture(
                    Pair.of(
                        InternalValidationResult.reject(
                            "Committee index %s is out of range", data.getIndex()),
                        maybeState));
              }

              // The attestation's committee index (attestation.data.index) is for the correct
              // subnet.
              if (receivedOnSubnetId.isPresent()
                  && spec.computeSubnetForAttestation(state, attestation)
                      != receivedOnSubnetId.getAsInt()) {
                return completedFuture(
                    Pair.of(
                        InternalValidationResult.reject(
                            "Attestation received on incorrect subnet (%s) for specified committee index (%s)",
                            attestation.getData().getIndex(), receivedOnSubnetId.getAsInt()),
                        maybeState));
              }

              // The check below is not specified in the Eth2 networking spec, yet an attestation
              // with aggregation bits size greater/less than the committee size is invalid. So we
              // reject those attestations at the networking layer.
              final IntList committee =
                  spec.getBeaconCommittee(state, data.getSlot(), data.getIndex());
              if (committee.size() != attestation.getAggregationBits().size()) {
                return completedFuture(
                    Pair.of(
                        InternalValidationResult.reject(
                            "Aggregation bit size %s is greater than committee size %s",
                            attestation.getAggregationBits().size(), committee.size()),
                        maybeState));
              }

              return spec.isValidIndexedAttestation(
                      state, validateableAttestation, signatureVerifier)
                  .thenApply(
                      signatureResult -> {
                        if (!signatureResult.isSuccessful()) {
                          return Pair.of(
                              InternalValidationResult.reject(
                                  "Attestation is not a valid indexed attestation: %s",
                                  signatureResult.getInvalidReason()),
                              maybeState);
                        }

                        // The attestation's target block is an ancestor of the block named in the
                        // LMD vote
                        if (!spec.getAncestor(
                                recentChainData.getForkChoiceStrategy().orElseThrow(),
                                data.getBeacon_block_root(),
                                spec.computeStartSlotAtEpoch(data.getTarget().getEpoch()))
                            .map(
                                ancestorOfLMDVote ->
                                    ancestorOfLMDVote.equals(data.getTarget().getRoot()))
                            .orElse(false)) {
                          return Pair.of(
                              InternalValidationResult.reject(
                                  "Attestation LMD vote block does not descend from target block"),
                              maybeState);
                        }

                        // The current finalized_checkpoint is an ancestor of the block defined by
                        // aggregate.data.beacon_block_root
                        // Because all nodes in the proto-array descend from the finalized block,
                        // no further validation is needed to satisfy this rule.

                        // Save committee shuffling seed since the state is available and
                        // attestation is valid
                        validateableAttestation.saveCommitteeShufflingSeed(state);
                        return Pair.of(InternalValidationResult.ACCEPT, maybeState);
                      });
            });
  }

  /**
   * Committee information is only guaranteed to be stable up to 1 epoch ahead, if block attested to
   * is too old, we need to roll the corresponding state forward to process the attestation
   *
   * @param attestation The attestation to be processed
   * @param blockState The state corresponding to the block being attested to
   * @return The state to use for validation of this attestation
   */
  public SafeFuture<Optional<BeaconState>> resolveStateForAttestation(
      final Attestation attestation, final BeaconState blockState) {
    final Bytes32 blockRoot = attestation.getData().getBeacon_block_root();
    final Checkpoint targetEpoch = attestation.getData().getTarget();
    final UInt64 earliestSlot =
        spec.getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(targetEpoch.getEpoch());
    final UInt64 earliestEpoch = spec.computeEpochAtSlot(earliestSlot);

    if (blockState.getSlot().isLessThan(earliestSlot)) {
      final Checkpoint checkpoint = new Checkpoint(earliestEpoch, blockRoot);
      return recentChainData.getStore().retrieveCheckpointState(checkpoint, blockState);
    } else {
      return completedFuture(Optional.of(blockState));
    }
  }

  private boolean isCurrentTimeBeforeMinimumAttestationBroadcastTime(
      final Attestation attestation, final UInt64 currentTimeMillis) {
    final UInt64 minimumBroadcastTimeMillis =
        minimumBroadcastTimeMillis(attestation.getData().getSlot());
    return currentTimeMillis.isLessThan(minimumBroadcastTimeMillis);
  }

  private boolean isFromFarFuture(final Attestation attestation, final UInt64 currentTimeMillis) {
    final int secondsPerSlot = secondsPerSlot(attestation);
    final UInt64 attestationSlotTimeMillis =
        secondsToMillis(
            recentChainData
                .getGenesisTime()
                .plus(
                    attestation
                        .getEarliestSlotForForkChoiceProcessing(spec)
                        .times(secondsPerSlot)));
    final UInt64 discardAttestationsAfterMillis =
        currentTimeMillis.plus(secondsToMillis(MAX_FUTURE_SLOT_ALLOWANCE.times(secondsPerSlot)));
    return attestationSlotTimeMillis.isGreaterThan(discardAttestationsAfterMillis);
  }

  private boolean isCurrentTimeAfterAttestationPropagationSlotRange(
      final UInt64 currentTimeMillis, final Attestation attestation) {
    final UInt64 attestationSlot = attestation.getData().getSlot();
    return maximumBroadcastTimeMillis(attestationSlot).isLessThan(currentTimeMillis);
  }

  private UInt64 minimumBroadcastTimeMillis(final UInt64 attestationSlot) {
    final UInt64 lastAllowedTime =
        recentChainData
            .getGenesisTime()
            .plus(attestationSlot.times(secondsPerSlot(attestationSlot)));
    final UInt64 lastAllowedTimeMillis = secondsToMillis(lastAllowedTime);
    return lastAllowedTimeMillis.isGreaterThanOrEqualTo(MAXIMUM_GOSSIP_CLOCK_DISPARITY)
        ? lastAllowedTimeMillis.minus(MAXIMUM_GOSSIP_CLOCK_DISPARITY)
        : ZERO;
  }

  private UInt64 maximumBroadcastTimeMillis(final UInt64 attestationSlot) {
    final UInt64 lastAllowedSlot = attestationSlot.plus(ATTESTATION_PROPAGATION_SLOT_RANGE);
    // The last allowed time is the end of the lastAllowedSlot (hence the plus 1).
    final UInt64 lastAllowedTime =
        recentChainData
            .getGenesisTime()
            .plus(lastAllowedSlot.plus(ONE).times(secondsPerSlot(attestationSlot)));

    // Add allowed clock disparity
    return secondsToMillis(lastAllowedTime).plus(MAXIMUM_GOSSIP_CLOCK_DISPARITY);
  }

  private int secondsPerSlot(final UInt64 slot) {
    return spec.getSecondsPerSlot(slot);
  }

  private int secondsPerSlot(final Attestation attestation) {
    return spec.getSecondsPerSlot(attestation.getData().getSlot());
  }
}
