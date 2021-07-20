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

import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.util.config.Constants.VALID_CONTRIBUTION_AND_PROOF_SET_SIZE;

import com.google.errorprone.annotations.FormatMethod;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BatchSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SignedContributionAndProofValidator {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final Set<UniquenessKey> seenIndices =
      LimitedSet.create(VALID_CONTRIBUTION_AND_PROOF_SET_SIZE);
  private final SyncCommitteeStateUtils syncCommitteeStateUtils;
  private final SyncCommitteeCurrentSlotUtil slotUtil;

  public SignedContributionAndProofValidator(
      final Spec spec,
      final RecentChainData recentChainData,
      final SyncCommitteeStateUtils syncCommitteeStateUtils,
      final TimeProvider timeProvider) {
    this.spec = spec;
    this.syncCommitteeStateUtils = syncCommitteeStateUtils;
    slotUtil = new SyncCommitteeCurrentSlotUtil(recentChainData, spec, timeProvider);
  }

  public SafeFuture<InternalValidationResult> validate(final SignedContributionAndProof proof) {
    final ContributionAndProof contributionAndProof = proof.getMessage();
    final SyncCommitteeContribution contribution = contributionAndProof.getContribution();

    // [IGNORE] The sync committee contribution is the first valid contribution received for the
    // aggregator with index contribution_and_proof.aggregator_index for the slot contribution.slot.
    // (this requires maintaining a cache of size `SYNC_COMMITTEE_SIZE` for this topic that can be
    // flushed after each slot).
    final UniquenessKey uniquenessKey = getUniquenessKey(contributionAndProof, contribution);
    if (seenIndices.contains(uniquenessKey)) {
      return SafeFuture.completedFuture(IGNORE);
    }

    final Optional<SyncCommitteeUtil> maybeSyncCommitteeUtil =
        spec.getSyncCommitteeUtil(contribution.getSlot());
    if (maybeSyncCommitteeUtil.isEmpty()) {
      return futureFailureResult(
          "Rejecting proof because the fork active at slot %s does not support sync committees",
          contribution.getSlot());
    }
    final SyncCommitteeUtil syncCommitteeUtil = maybeSyncCommitteeUtil.get();

    if (proof.getMessage().getContribution().getAggregationBits().getBitCount() == 0) {
      return SafeFuture.completedFuture(
          failureResult("Rejecting proof because participant set is empty"));
    }

    // [IGNORE] The contribution's slot is for the current slot (with a
    // `MAXIMUM_GOSSIP_CLOCK_DISPARITY` allowance), i.e. `contribution.slot == current_slot`.
    if (!slotUtil.isForCurrentSlot(contribution.getSlot())) {
      LOG.trace("Ignoring proof because it is not from the current slot");
      return SafeFuture.completedFuture(IGNORE);
    }

    // [REJECT] The subcommittee index is in the allowed range
    // i.e. contribution.subcommittee_index < SYNC_COMMITTEE_SUBNET_COUNT.
    if (contribution.getSubcommitteeIndex().isGreaterThanOrEqualTo(SYNC_COMMITTEE_SUBNET_COUNT)) {
      return futureFailureResult(
          "Rejecting proof because subcommittee index %s is too big",
          contribution.getSubcommitteeIndex());
    }

    return syncCommitteeStateUtils
        .getStateForSyncCommittee(contribution.getSlot())
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.trace("Ignoring proof because state is not available or not from Altair fork");
                return IGNORE;
              }
              return validateWithState(
                  proof,
                  contributionAndProof,
                  contribution,
                  syncCommitteeUtil,
                  uniquenessKey,
                  maybeState.get());
            });
  }

  @FormatMethod
  private SafeFuture<InternalValidationResult> futureFailureResult(
      final String message, Object... args) {
    return SafeFuture.completedFuture(failureResult(message, args));
  }

  @FormatMethod
  private InternalValidationResult failureResult(final String message, Object... args) {
    final String contextMessage = String.format(message, args);
    LOG.trace(contextMessage);
    return InternalValidationResult.create(ValidationResultCode.REJECT, contextMessage);
  }

  private InternalValidationResult validateWithState(
      final SignedContributionAndProof proof,
      final ContributionAndProof contributionAndProof,
      final SyncCommitteeContribution contribution,
      final SyncCommitteeUtil syncCommitteeUtil,
      final UniquenessKey uniquenessKey,
      final BeaconStateAltair state) {
    if (state.getSlot().isGreaterThan(contribution.getSlot())) {
      return failureResult(
          "Rejecting proof because referenced beacon block %s is after contribution slot %s",
          contribution.getBeaconBlockRoot(), contribution.getSlot());
    }

    final BeaconStateAccessors beaconStateAccessors =
        spec.atSlot(contribution.getSlot()).beaconStateAccessors();

    final Optional<BLSPublicKey> aggregatorPublicKey =
        beaconStateAccessors.getValidatorPubKey(state, contributionAndProof.getAggregatorIndex());
    if (aggregatorPublicKey.isEmpty()) {
      return failureResult(
          "Rejecting proof because aggregator index %s is an unknown validator",
          contributionAndProof.getAggregatorIndex());
    }
    final UInt64 contributionEpoch =
        syncCommitteeUtil.getEpochForDutiesAtSlot(contribution.getSlot());

    // [REJECT] The aggregator's validator index is within the current sync subcommittee
    // i.e. state.validators[aggregate_and_proof.aggregator_index].pubkey in
    // state.current_sync_committee.pubkeys.
    if (!isInSyncSubcommittee(
        syncCommitteeUtil,
        contribution,
        state,
        contributionEpoch,
        contributionAndProof.getAggregatorIndex())) {
      return failureResult(
          "Rejecting proof because aggregator index %s is not in the current sync subcommittee",
          contributionAndProof.getAggregatorIndex());
    }

    // [REJECT] contribution_and_proof.selection_proof selects the validator as an
    // aggregator for the slot -- i.e. is_sync_committee_aggregator(state,
    // contribution.slot, contribution_and_proof.selection_proof) returns True.
    if (!syncCommitteeUtil.isSyncCommitteeAggregator(contributionAndProof.getSelectionProof())) {
      return failureResult(
          "Rejecting proof because selection proof %s is not an aggregator",
          contributionAndProof.getSelectionProof());
    }

    final BatchSignatureVerifier signatureVerifier = new BatchSignatureVerifier();

    // [REJECT] The contribution_and_proof.selection_proof is a valid signature of the
    // contribution.slot by the validator with index
    // contribution_and_proof.aggregator_index.
    final Bytes signingRoot =
        syncCommitteeUtil.getSyncAggregatorSelectionDataSigningRoot(
            syncCommitteeUtil.createSyncAggregatorSelectionData(
                contribution.getSlot(), contribution.getSubcommitteeIndex()),
            state.getForkInfo());
    if (!signatureVerifier.verify(
        aggregatorPublicKey.get(), signingRoot, contributionAndProof.getSelectionProof())) {
      return failureResult(
          "Rejecting proof at slot %s for subcommittee index %s because selection proof is invalid",
          contribution.getSlot(), contribution.getSubcommitteeIndex());
    }

    // [REJECT] The aggregator signature, signed_contribution_and_proof.signature, is
    // valid.
    if (!signatureVerifier.verify(
        aggregatorPublicKey.get(),
        syncCommitteeUtil.getContributionAndProofSigningRoot(state, contributionAndProof),
        proof.getSignature())) {
      return failureResult(
          "Rejecting proof %s because aggregator signature is invalid", proof.getSignature());
    }

    final SpecConfigAltair config =
        SpecConfigAltair.required(spec.getSpecConfig(contributionEpoch));
    final SyncCommittee currentSyncCommittee =
        syncCommitteeUtil.getSyncCommittee(state, contributionEpoch);
    final int subcommitteeSize = config.getSyncCommitteeSize() / SYNC_COMMITTEE_SUBNET_COUNT;

    // [REJECT] The aggregate signature is valid for the message beacon_block_root and
    // aggregate pubkey derived from the participation info in aggregation_bits for the
    // subcommittee specified by the subcommittee_index.
    final List<BLSPublicKey> contributorPublicKeys =
        contribution
            .getAggregationBits()
            .streamAllSetBits()
            .mapToObj(
                participantIndex ->
                    getParticipantPublicKey(
                        currentSyncCommittee, contribution, subcommitteeSize, participantIndex))
            .collect(Collectors.toList());

    if (!signatureVerifier.verify(
        contributorPublicKeys,
        syncCommitteeUtil.getSyncCommitteeMessageSigningRoot(
            contribution.getBeaconBlockRoot(), contributionEpoch, state.getForkInfo()),
        contribution.getSignature())) {
      return failureResult(
          "Rejecting proof because aggregate signature %s is invalid", contribution.getSignature());
    }

    if (!signatureVerifier.batchVerify()) {
      return failureResult(
          "Rejecting proof with signature %s because batch signature check failed",
          contribution.getSignature());
    }

    if (!seenIndices.add(uniquenessKey)) {
      // Got added by another thread while we were validating it
      return IGNORE;
    }

    return ACCEPT;
  }

  private BLSPublicKey getParticipantPublicKey(
      final SyncCommittee currentSyncCommittee,
      final SyncCommitteeContribution contribution,
      final int subcommitteeSize,
      final int participantIndex) {
    return currentSyncCommittee
        .getPubkeys()
        .get(contribution.getSubcommitteeIndex().intValue() * subcommitteeSize + participantIndex)
        .getBLSPublicKey();
  }

  private boolean isInSyncSubcommittee(
      final SyncCommitteeUtil syncCommitteeUtil,
      final SyncCommitteeContribution contribution,
      final BeaconState state,
      final UInt64 contributionEpoch,
      final UInt64 aggregatorIndex) {
    return syncCommitteeUtil
        .getSubcommitteeAssignments(state, contributionEpoch, aggregatorIndex)
        .getAssignedSubcommittees()
        .contains(contribution.getSubcommitteeIndex().intValue());
  }

  private UniquenessKey getUniquenessKey(
      final ContributionAndProof contributionAndProof, SyncCommitteeContribution contribution) {
    return new UniquenessKey(
        contributionAndProof.getAggregatorIndex(),
        contribution.getSlot(),
        contribution.getSubcommitteeIndex());
  }

  private static class UniquenessKey {
    private final UInt64 aggregatorIndex;
    private final UInt64 slot;
    private final UInt64 subcommitteeIndex;

    private UniquenessKey(
        final UInt64 aggregatorIndex, final UInt64 slot, final UInt64 subcommitteeIndex) {
      this.aggregatorIndex = aggregatorIndex;
      this.slot = slot;
      this.subcommitteeIndex = subcommitteeIndex;
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
      return Objects.equals(aggregatorIndex, that.aggregatorIndex)
          && Objects.equals(slot, that.slot)
          && Objects.equals(subcommitteeIndex, that.subcommitteeIndex);
    }

    @Override
    public int hashCode() {
      return Objects.hash(aggregatorIndex, slot, subcommitteeIndex);
    }
  }
}
