/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.payloadattestation;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.config.Constants.RECENT_SEEN_PAYLOAD_ATTESTATIONS_CACHE_SIZE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class PayloadAttestationMessageGossipValidator {

  private static final Logger LOG = LogManager.getLogger();
  final GossipValidationHelper gossipValidationHelper;
  final SigningRootUtil signingRootUtil;
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots;
  private final Set<ValidatorIndexAndSlot> seenPayloadAttestations =
      LimitedSet.createSynchronized(RECENT_SEEN_PAYLOAD_ATTESTATIONS_CACHE_SIZE);

  public PayloadAttestationMessageGossipValidator(
      final Spec spec,
      final GossipValidationHelper gossipValidationHelper,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots) {
    this.gossipValidationHelper = gossipValidationHelper;
    this.invalidBlockRoots = invalidBlockRoots;
    signingRootUtil = new SigningRootUtil(spec);
  }

  public SafeFuture<InternalValidationResult> validate(
      final PayloadAttestationMessage payloadAttestationMessage) {
    final PayloadAttestationData data = payloadAttestationMessage.getData();

    /*
     * [IGNORE] The message's slot is for the current slot (with a MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance),
     * i.e. data.slot == current_slot
     */
    if (!gossipValidationHelper.isCurrentSlotWithGossipDisparityAllowance(data.getSlot())) {
      LOG.trace(
          "Ignoring payload attestation with slot {} from validator with index {} because it's not from the current slot",
          data.getSlot(),
          payloadAttestationMessage.getValidatorIndex());
      return completedFuture(
          ignore(
              "Ignoring payload attestation with slot %s from validator with index %s because it's not from the current slot",
              data.getSlot(), payloadAttestationMessage.getValidatorIndex()));
    }

    /*
     * [IGNORE] The payload_attestation_message is the first valid message received from the validator
     *  with index payload_attestation_message.validate_index
     */
    final ValidatorIndexAndSlot key =
        new ValidatorIndexAndSlot(payloadAttestationMessage.getValidatorIndex(), data.getSlot());
    if (seenPayloadAttestations.contains(key)) {
      LOG.trace(
          "Payload attestation for slot {} and validator index {} already seen",
          key.slot(),
          key.validatorIndex());
      return completedFuture(
          ignore(
              "Payload attestation for slot %s and validator index %s already seen",
              key.slot(), key.validatorIndex()));
    }

    /*
     * [IGNORE] The message's block data.beacon_block_root has been seen (via gossip or non-gossip sources)
     * (a client MAY queue attestation for processing once the block is retrieved.
     * Note a client might want to request payload after).
     */
    if (!gossipValidationHelper.isBlockAvailable(data.getBeaconBlockRoot())) {
      LOG.trace(
          "Payload attestations's block with root {} is not available. Saving for future processing",
          data.getBeaconBlockRoot());
      return completedFuture(SAVE_FOR_FUTURE);
    }

    /*
     * [REJECT] The message's block data.beacon_block_root passes validation.
     */
    if (invalidBlockRoots.containsKey(data.getBeaconBlockRoot())) {
      LOG.trace("Payload attestations's block with root {} is invalid", data.getBeaconBlockRoot());
      return completedFuture(
          reject(
              "Payload attestations's block with root %s is invalid", data.getBeaconBlockRoot()));
    }

    return gossipValidationHelper
        .getStateAtSlotAndBlockRoot(new SlotAndBlockRoot(data.getSlot(), data.getBeaconBlockRoot()))
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.trace(
                    "State for block root {} and slot {} is unavailable",
                    data.getBeaconBlockRoot(),
                    data.getSlot());
                return SAVE_FOR_FUTURE;
              }
              final BeaconState state = maybeState.get();
              /*
               * [REJECT] The message's validator index is within the payload committee in get_ptc(state, data.slot).
               * The state is the head state corresponding to processing the block up to the current slot as determined
               * by the fork choice.
               */
              if (!gossipValidationHelper.isValidatorInPayloadTimelinessCommittee(
                  payloadAttestationMessage.getValidatorIndex(), state, data.getSlot())) {
                LOG.trace(
                    "Payload attestation's validator index {} is not in the payload committee for slot {}",
                    payloadAttestationMessage.getValidatorIndex(),
                    data.getSlot());
                return reject(
                    "Payload attestation's validator index %s is not in the payload committee",
                    payloadAttestationMessage.getValidatorIndex());
              }

              /*
               * [REJECT] payload_attestation_message.signature is valid with respect to the validator's public key.
               */
              if (!isSignatureValid(payloadAttestationMessage, state)) {
                return reject("Invalid payload attestation signature");
              }
              seenPayloadAttestations.add(key);
              return ACCEPT;
            });
  }

  private boolean isSignatureValid(
      final PayloadAttestationMessage payloadAttestationMessage, final BeaconState state) {
    final Bytes signingRoot =
        signingRootUtil.signingRootForSignPayloadAttestationData(
            payloadAttestationMessage.getData(), state.getForkInfo());
    return gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
        signingRoot,
        payloadAttestationMessage.getValidatorIndex(),
        payloadAttestationMessage.getSignature(),
        state);
  }

  record ValidatorIndexAndSlot(UInt64 validatorIndex, UInt64 slot) {}
}
