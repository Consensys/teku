/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.synccommittee;

import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.REJECT;
import static tech.pegasys.teku.util.config.Constants.VALID_SYNC_COMMITTEE_SIGNATURE_SET_SIZE;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncCommitteeSignatureValidator {
  private static final Logger LOG = LogManager.getLogger();
  private final Set<UniquenessKey> seenIndices =
      LimitedSet.create(VALID_SYNC_COMMITTEE_SIGNATURE_SET_SIZE);
  private final Spec spec;
  private final RecentChainData recentChainData;
  private final SyncCommitteeStateUtils syncCommitteeStateUtils;

  public SyncCommitteeSignatureValidator(
      final Spec spec,
      final RecentChainData recentChainData,
      final SyncCommitteeStateUtils syncCommitteeStateUtils) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.syncCommitteeStateUtils = syncCommitteeStateUtils;
  }

  public SafeFuture<InternalValidationResult> validate(
      final ValidateableSyncCommitteeSignature validateableSignature) {

    final SyncCommitteeSignature signature = validateableSignature.getSignature();

    final Optional<SyncCommitteeUtil> maybeSyncCommitteeUtil =
        spec.getSyncCommitteeUtil(signature.getSlot());
    if (maybeSyncCommitteeUtil.isEmpty()) {
      LOG.trace(
          "Rejecting sync committee signature because the fork active at slot {} does not support sync committees",
          signature.getSlot());
      return SafeFuture.completedFuture(REJECT);
    }
    final SyncCommitteeUtil syncCommitteeUtil = maybeSyncCommitteeUtil.get();

    // [IGNORE] The signature's slot is for the current slot, i.e. sync_committee_signature.slot ==
    // current_slot.
    if (recentChainData.getCurrentSlot().isEmpty()
        || !signature.getSlot().equals(recentChainData.getCurrentSlot().orElseThrow())) {
      LOG.trace("Ignoring sync committee signature because it is not from the current slot");
      return SafeFuture.completedFuture(IGNORE);
    }

    // [IGNORE] There has been no other valid sync committee signature for the declared slot for the
    // validator referenced by sync_committee_signature.validator_index.
    final UniquenessKey uniquenessKey = getUniquenessKey(signature);
    if (seenIndices.contains(uniquenessKey)) {
      return SafeFuture.completedFuture(IGNORE);
    }

    // [IGNORE] The block being signed over (sync_committee_signature.beacon_block_root) has been
    // seen (via both gossip and non-gossip sources).
    if (!recentChainData.containsBlock(signature.getBeaconBlockRoot())) {
      LOG.trace("Ignoring sync committee signature because beacon block is not known");
      return SafeFuture.completedFuture(IGNORE);
    }

    return syncCommitteeStateUtils
        .getStateForSyncCommittee(signature.getSlot(), signature.getBeaconBlockRoot())
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.trace(
                    "Ignoring sync committee signature because state is not available or not from Altair fork");
                return IGNORE;
              }
              final BeaconStateAltair state = maybeState.get();
              return validateWithState(
                  validateableSignature, signature, syncCommitteeUtil, state, uniquenessKey);
            });
  }

  private InternalValidationResult validateWithState(
      final ValidateableSyncCommitteeSignature validateableSignature,
      final SyncCommitteeSignature signature,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAltair state,
      final UniquenessKey uniquenessKey) {
    final UInt64 signatureEpoch = spec.computeEpochAtSlot(signature.getSlot());

    // Always calculate the applicable subcommittees to ensure they are cached and can be used to
    // send the gossip.
    final SyncSubcommitteeAssignments assignedSubcommittees =
        validateableSignature.calculateAssignments(spec, state);

    // [REJECT] The validator producing this sync_committee_signature is in the current sync
    // committee, i.e. state.validators[sync_committee_signature.validator_index].pubkey in
    // state.current_sync_committee.pubkeys.
    if (assignedSubcommittees.isEmpty()) {
      LOG.trace(
          "Rejecting sync committee signature because validator is not in the sync committee");
      return REJECT;
    }

    // [REJECT] The subnet_id is correct, i.e. subnet_id in
    // compute_subnets_for_sync_committee(state, sync_committee_signature.validator_index).
    if (validateableSignature.getReceivedSubnetId().isPresent()
        && !assignedSubcommittees
            .getAssignedSubcommittees()
            .contains(validateableSignature.getReceivedSubnetId().getAsInt())) {
      LOG.trace("Rejecting sync committee signature because subnet id is incorrect");
      return REJECT;
    }

    final Optional<BLSPublicKey> maybeValidatorPublicKey =
        spec.getValidatorPubKey(state, signature.getValidatorIndex());
    if (maybeValidatorPublicKey.isEmpty()) {
      LOG.trace("Rejecting sync committee signature because the validator index is unknown");
      return REJECT;
    }

    // [REJECT] The signature is valid for the message beacon_block_root for the validator
    // referenced by validator_index.
    final Bytes32 signingRoot =
        syncCommitteeUtil.getSyncCommitteeSignatureSigningRoot(
            signature.getBeaconBlockRoot(), signatureEpoch, state.getForkInfo());
    if (!BLS.verify(maybeValidatorPublicKey.get(), signingRoot, signature.getSignature())) {
      LOG.trace("Rejecting sync committee signature because the signature is invalid");
      return REJECT;
    }

    if (!seenIndices.add(uniquenessKey)) {
      LOG.trace("Ignoring sync committee signature as a duplicate was processed during validation");
      return IGNORE;
    }
    return ACCEPT;
  }

  private UniquenessKey getUniquenessKey(final SyncCommitteeSignature signature) {
    return new UniquenessKey(signature.getValidatorIndex(), signature.getSlot());
  }

  private static class UniquenessKey {
    private final UInt64 validatorIndex;
    private final UInt64 slot;

    private UniquenessKey(final UInt64 validatorIndex, final UInt64 slot) {
      this.validatorIndex = validatorIndex;
      this.slot = slot;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final UniquenessKey that = (UniquenessKey) o;
      return Objects.equals(validatorIndex, that.validatorIndex) && Objects.equals(slot, that.slot);
    }

    @Override
    public int hashCode() {
      return Objects.hash(validatorIndex, slot);
    }
  }
}
