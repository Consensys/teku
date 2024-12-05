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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ACCEPT;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import java.util.OptionalInt;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil.SlotInclusionGossipValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttestationValidator {

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final AsyncBLSSignatureVerifier signatureVerifier;
  private final AttestationStateSelector stateSelector;

  public AttestationValidator(
      final Spec spec,
      final RecentChainData recentChainData,
      final AsyncBLSSignatureVerifier signatureVerifier,
      final MetricsSystem metricsSystem) {
    this.recentChainData = recentChainData;
    this.spec = spec;
    this.signatureVerifier = signatureVerifier;
    this.stateSelector = new AttestationStateSelector(spec, recentChainData, metricsSystem);
  }

  public SafeFuture<InternalValidationResult> validate(
      final ValidatableAttestation validatableAttestation) {
    if (validatableAttestation.isAcceptedAsGossip()) {
      return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
    }
    Attestation attestation = validatableAttestation.getAttestation();
    final InternalValidationResult internalValidationResult = singleAttestationChecks(attestation);
    if (internalValidationResult.code() != ACCEPT) {
      return completedFuture(internalValidationResult);
    }

    return singleOrAggregateAttestationChecks(
            signatureVerifier, validatableAttestation, validatableAttestation.getReceivedSubnetId())
        .thenApply(InternalValidationResultWithState::getResult)
        .thenPeek(
            result -> {
              if (result.isAccept()) {
                validatableAttestation.setAcceptedAsGossip();
              }
            });
  }

  private InternalValidationResult singleAttestationChecks(final Attestation attestation) {
    // if it is a SingleAttestation type we are guaranteed to be a valid single attestation
    if (attestation.isSingleAttestation()) {
      return InternalValidationResult.ACCEPT;
    }

    // The attestation is unaggregated -- that is, it has exactly one participating validator
    // (len([bit for bit in attestation.aggregation_bits if bit == 0b1]) == 1).
    final int bitCount = attestation.getAggregationBits().getBitCount();
    if (bitCount != 1) {
      return InternalValidationResult.reject("Attestation has %s bits set instead of 1", bitCount);
    }
    return InternalValidationResult.ACCEPT;
  }

  SafeFuture<InternalValidationResultWithState> singleOrAggregateAttestationChecks(
      final AsyncBLSSignatureVerifier signatureVerifier,
      final ValidatableAttestation validatableAttestation,
      final OptionalInt receivedOnSubnetId) {

    Attestation attestation = validatableAttestation.getAttestation();
    final AttestationData data = attestation.getData();
    // [REJECT] 4 - The attestation's epoch matches its target
    if (!data.getTarget().getEpoch().equals(spec.computeEpochAtSlot(data.getSlot()))) {
      return completedFuture(
          InternalValidationResultWithState.reject(
              "Attestation slot %s is not from target epoch %s",
              data.getSlot(), data.getTarget().getEpoch()));
    }

    final UInt64 genesisTime = recentChainData.getGenesisTime();
    final UInt64 currentTimeMillis = recentChainData.getStore().getTimeInMillis();

    final Optional<SlotInclusionGossipValidationResult> slotInclusionGossipValidationResult =
        spec.atSlot(data.getSlot())
            .getAttestationUtil()
            .performSlotInclusionGossipValidation(attestation, genesisTime, currentTimeMillis);

    if (slotInclusionGossipValidationResult.isPresent()) {
      return switch (slotInclusionGossipValidationResult.get()) {
        case IGNORE -> completedFuture(InternalValidationResultWithState.ignore());
        case SAVE_FOR_FUTURE -> completedFuture(InternalValidationResultWithState.saveForFuture());
      };
    }

    // The block being voted for (attestation.data.beacon_block_root) passes validation.
    // It must pass validation to be in the store.
    // If it's not in the store, it may not have been processed yet so save for future.
    if (!recentChainData.containsBlock(data.getBeaconBlockRoot())) {
      return completedFuture(InternalValidationResultWithState.saveForFuture());
    }

    if (attestation.requiresCommitteeBits()) {
      // [REJECT] len(committee_indices) == 1, where committee_indices =
      // get_committee_indices(attestation)
      if (attestation.getCommitteeBitsRequired().getBitCount() != 1) {
        return SafeFuture.completedFuture(
            InternalValidationResultWithState.reject(
                "Rejecting attestation because committee bits count is not 1"));
      }

      // [REJECT] attestation.data.index == 0
      if (!attestation.getData().getIndex().isZero()) {
        return SafeFuture.completedFuture(
            InternalValidationResultWithState.reject(
                "Rejecting attestation because attestation data index must be 0"));
      }
    }

    return stateSelector
        .getStateToValidate(attestation.getData())
        .thenCompose(
            maybeState -> {
              if (maybeState.isEmpty()) {
                // We know the block is imported but now don't have a state to validate against
                // Must have got pruned between checks
                return completedFuture(InternalValidationResultWithState.ignore());
              }
              final BeaconState state = maybeState.get();

              // The committee index is within the expected range
              if (attestation
                  .getFirstCommitteeIndex()
                  .isGreaterThanOrEqualTo(
                      spec.getCommitteeCountPerSlot(state, data.getTarget().getEpoch()))) {
                return completedFuture(
                    InternalValidationResultWithState.reject(
                        "Committee index %s is out of range", data.getIndex()));
              }

              // The attestation's committee index (attestation.data.index) is for the correct
              // subnet.
              if (receivedOnSubnetId.isPresent()
                  && spec.computeSubnetForAttestation(state, attestation)
                      != receivedOnSubnetId.getAsInt()) {
                return completedFuture(
                    InternalValidationResultWithState.reject(
                        "Attestation received on incorrect subnet (%s) for specified committee index (%s)",
                        attestation.getFirstCommitteeIndex(), receivedOnSubnetId.getAsInt()));
              }

              if (!attestation.isSingleAttestation()) {
                // [REJECT] The number of aggregation bits matches the committee size
                final IntList committee =
                    spec.getBeaconCommittee(
                        state, data.getSlot(), attestation.getFirstCommitteeIndex());
                if (committee.size() != attestation.getAggregationBits().size()) {
                  return completedFuture(
                      InternalValidationResultWithState.reject(
                          "Aggregation bit size %s is greater than committee size %s",
                          attestation.getAggregationBits().size(), committee.size()));
                }
              }

              return spec.isValidIndexedAttestation(
                      state, validatableAttestation, signatureVerifier)
                  .thenApply(
                      signatureResult -> {
                        if (!signatureResult.isSuccessful()) {
                          return InternalValidationResultWithState.reject(
                              "Attestation is not a valid indexed attestation: %s",
                              signatureResult.getInvalidReason());
                        }

                        // The attestation's target block is an ancestor of the block named in the
                        // LMD vote
                        if (!spec.getAncestor(
                                recentChainData.getForkChoiceStrategy().orElseThrow(),
                                data.getBeaconBlockRoot(),
                                spec.computeStartSlotAtEpoch(data.getTarget().getEpoch()))
                            .map(
                                ancestorOfLMDVote ->
                                    ancestorOfLMDVote.equals(data.getTarget().getRoot()))
                            .orElse(false)) {
                          return InternalValidationResultWithState.reject(
                              "Attestation LMD vote block does not descend from target block");
                        }

                        // The current finalized_checkpoint is an ancestor of the block defined by
                        // aggregate.data.beacon_block_root
                        // Because all nodes in the proto-array descend from the finalized block,
                        // no further validation is needed to satisfy this rule.

                        // Save committee shuffling seed since the state is available and
                        // attestation is valid
                        validatableAttestation.saveCommitteeShufflingSeedAndCommitteesSize(state);
                        return InternalValidationResultWithState.accept(state);
                      });
            });
  }
}
