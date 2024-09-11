/*
 * Copyright Consensys Software Inc., 2024
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
import static tech.pegasys.teku.spec.config.Constants.VALID_BLOCK_SET_SIZE;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.constants.PayloadStatus;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil.SlotInclusionGossipValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class PayloadAttestationValidator {

  private final Spec spec;
  private final RecentChainData recentChainData;

  private final Set<UInt64> receivedValidPayloadAttestationInfoSet =
      LimitedSet.createSynchronized(VALID_BLOCK_SET_SIZE);

  public PayloadAttestationValidator(final Spec spec, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  public SafeFuture<InternalValidationResult> validate(
      final PayloadAttestationMessage payloadAttestationMessage) {
    final PayloadAttestationData data = payloadAttestationMessage.getData();
    /*
     * [IGNORE] The message's slot is for the current slot (with a MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance), i.e. data.slot == current_slot.
     */
    final UInt64 genesisTime = recentChainData.getGenesisTime();
    final UInt64 currentTimeMillis = recentChainData.getStore().getTimeInMillis();
    final Optional<SlotInclusionGossipValidationResult> slotInclusionGossipValidationResult =
        spec.atSlot(data.getSlot())
            .getAttestationUtil()
            .performSlotInclusionGossipValidation(
                payloadAttestationMessage.getData(), genesisTime, currentTimeMillis);
    if (slotInclusionGossipValidationResult.isPresent()) {
      return switch (slotInclusionGossipValidationResult.get()) {
        case IGNORE -> completedFuture(InternalValidationResult.IGNORE);
        case SAVE_FOR_FUTURE -> completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
      };
    }
    /*
     * [REJECT] The message's payload status is a valid status, i.e. data.payload_status < PAYLOAD_INVALID_STATUS.
     */
    if (data.getPayloadStatus().compareTo(PayloadStatus.PAYLOAD_INVALID_STATUS.getCode()) >= 0) {
      return completedFuture(
          InternalValidationResult.reject("The message's payload status is invalid"));
    }
    /*
     * [IGNORE] The payload_attestation_message is the first valid message received from the validator with index payload_attestation_message.validate_index.
     */
    final UInt64 validatorIndex = payloadAttestationMessage.getValidatorIndex();
    if (receivedValidPayloadAttestationInfoSet.contains(validatorIndex)) {
      return completedFuture(InternalValidationResult.IGNORE);
    }

    // The block being voted for (data.beacon_block_root) passes validation.
    // It must pass validation to be in the store.
    // If it's not in the store, it may not have been processed yet so save for future.
    if (!recentChainData.containsBlock(data.getBeaconBlockRoot())) {
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }

    return recentChainData
        .retrieveBlockState(data.getBeaconBlockRoot())
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                // We know the block is imported but now don't have a state to validate against
                // Must have got pruned between checks
                return InternalValidationResult.IGNORE;
              }
              final BeaconState state = maybeState.get();
              /*
               * [REJECT] The message's validator index is within the payload committee in get_ptc(state, data.slot). The state is the head state corresponding to processing the block up to the current slot as determined by the fork choice.
               */
              final IntList payloadCommittee = spec.getPtc(state, data.getSlot());
              if (!payloadCommittee.contains(validatorIndex.intValue())) {
                return InternalValidationResult.reject(
                    "Message's validator index is not within the payload committee");
              }
              /*
               * [REJECT] The message's signature of payload_attestation_message.signature is valid with respect to the validator index.
               */
              final Validator validator = state.getValidators().get(validatorIndex.intValue());
              if (!verifyPayloadAttestationSignature(
                  validator.getPublicKey(), payloadAttestationMessage, state)) {
                return InternalValidationResult.reject(
                    "The validator signature is not valid for a validator with public key %s",
                    validator.getPublicKey());
              }

              // cache valid payload attestation
              receivedValidPayloadAttestationInfoSet.add(validatorIndex);

              return InternalValidationResult.ACCEPT;
            });
  }

  private boolean verifyPayloadAttestationSignature(
      final BLSPublicKey publicKey,
      final PayloadAttestationMessage payloadAttestationMessage,
      final BeaconState state) {
    final Bytes32 domain =
        spec.getDomain(
            Domain.PTC_ATTESTER,
            spec.getCurrentEpoch(state),
            state.getFork(),
            state.getGenesisValidatorsRoot());
    final Bytes signingRoot = spec.computeSigningRoot(payloadAttestationMessage.getData(), domain);
    return BLS.verify(publicKey, signingRoot, payloadAttestationMessage.getSignature());
  }
}
