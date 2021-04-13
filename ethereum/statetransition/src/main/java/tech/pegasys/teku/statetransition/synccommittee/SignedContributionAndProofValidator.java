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
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.REJECT;
import static tech.pegasys.teku.util.config.Constants.VALID_CONTRIBUTION_AND_PROOF_SET_SIZE;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.collections.TekuPair;
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
import tech.pegasys.teku.storage.client.RecentChainData;

public class SignedContributionAndProofValidator {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final RecentChainData recentChainData;
  private final Set<TekuPair<UInt64, UInt64>> seenIndices =
      LimitedSet.create(VALID_CONTRIBUTION_AND_PROOF_SET_SIZE);

  public SignedContributionAndProofValidator(
      final Spec spec, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  public SafeFuture<InternalValidationResult> validate(final SignedContributionAndProof proof) {
    final ContributionAndProof contributionAndProof = proof.getMessage();
    final SyncCommitteeContribution contribution = contributionAndProof.getContribution();
    final Optional<SyncCommitteeUtil> maybeSyncCommitteeUtil =
        spec.getSyncCommitteeUtil(contribution.getSlot());
    if (maybeSyncCommitteeUtil.isEmpty()) {
      LOG.trace(
          "Rejecting proof because the fork active at slot {} does not support sync committees",
          contribution.getSlot());
      return SafeFuture.completedFuture(REJECT);
    }
    final SyncCommitteeUtil syncCommitteeUtil = maybeSyncCommitteeUtil.get();

    // [IGNORE] The contribution's slot is for the current slot
    // i.e. contribution.slot == current_slot.
    if (recentChainData.getCurrentSlot().isEmpty()
        || !contribution.getSlot().equals(recentChainData.getCurrentSlot().orElseThrow())) {
      LOG.trace("Ignoring proof because it is not from the current slot");
      return SafeFuture.completedFuture(IGNORE);
    }

    // [IGNORE] The block being signed over (contribution.beacon_block_root) has been seen (via both
    // gossip and non-gossip sources).
    if (!recentChainData.containsBlock(contribution.getBeaconBlockRoot())) {
      LOG.trace("Ignoring proof because beacon block is not known");
      return SafeFuture.completedFuture(IGNORE);
    }

    // [REJECT] The subcommittee index is in the allowed range
    // i.e. contribution.subcommittee_index < SYNC_COMMITTEE_SUBNET_COUNT.
    if (contribution.getSubcommitteeIndex().isGreaterThanOrEqualTo(SYNC_COMMITTEE_SUBNET_COUNT)) {
      LOG.trace("Rejecting proof because subcommittee index is too big");
      return SafeFuture.completedFuture(REJECT);
    }

    // [IGNORE] The sync committee contribution is the first valid contribution received for the
    // aggregator with index contribution_and_proof.aggregator_index for the slot contribution.slot.
    final TekuPair<UInt64, UInt64> uniquenessKey = getUniquenessKey(proof);
    if (seenIndices.contains(uniquenessKey)) {
      return SafeFuture.completedFuture(IGNORE);
    }

    return getState(contribution, syncCommitteeUtil)
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.trace("Ignoring proof because state is not available or not from Altair fork");
                return IGNORE;
              }
              final BeaconStateAltair state = maybeState.get();
              if (state.getSlot().isGreaterThan(contribution.getSlot())) {
                LOG.trace(
                    "Rejecting proof because referenced beacon block {} is after contribution slot {}",
                    state.getSlot(),
                    contribution.getSignature());
                return REJECT;
              }

              final BeaconStateAccessors beaconStateAccessors =
                  spec.atSlot(contribution.getSlot()).beaconStateAccessors();

              final Optional<BLSPublicKey> aggregatorPublicKey =
                  beaconStateAccessors.getValidatorPubKey(
                      state, contributionAndProof.getAggregatorIndex());
              if (aggregatorPublicKey.isEmpty()) {
                LOG.trace(
                    "Rejecting proof because aggregator index {} is an unknown validator",
                    contributionAndProof.getAggregatorIndex());
                return REJECT;
              }
              final UInt64 contributionEpoch = spec.computeEpochAtSlot(contribution.getSlot());

              // [REJECT] The aggregator's validator index is within the current sync subcommittee
              // i.e. state.validators[aggregate_and_proof.aggregator_index].pubkey in
              // state.current_sync_committee.pubkeys.
              if (!isInSyncSubcommittee(
                  syncCommitteeUtil,
                  contribution,
                  state,
                  contributionEpoch,
                  contributionAndProof.getAggregatorIndex())) {
                LOG.trace(
                    "Rejecting proof because aggregator is not in the current sync subcommittee");
                return REJECT;
              }

              // [REJECT] contribution_and_proof.selection_proof selects the validator as an
              // aggregator for the slot -- i.e. is_sync_committee_aggregator(state,
              // contribution.slot, contribution_and_proof.selection_proof) returns True.
              if (!syncCommitteeUtil.isSyncCommitteeAggregator(
                  contributionAndProof.getSelectionProof())) {
                LOG.trace("Rejecting proof because selection proof is not an aggregator");
                return REJECT;
              }

              final BatchSignatureVerifier signatureVerifier = new BatchSignatureVerifier();

              // [REJECT] The contribution_and_proof.selection_proof is a valid signature of the
              // contribution.slot by the validator with index
              // contribution_and_proof.aggregator_index.
              final Bytes signingRoot =
                  syncCommitteeUtil.getSyncCommitteeSigningDataSigningRoot(
                      syncCommitteeUtil.createSyncCommitteeSigningData(
                          contribution.getSlot(), contribution.getSubcommitteeIndex()),
                      state.getForkInfo());
              if (!signatureVerifier.verify(
                  aggregatorPublicKey.get(),
                  signingRoot,
                  contributionAndProof.getSelectionProof())) {
                LOG.trace("Rejecting proof because selection proof is invalid");
                return REJECT;
              }

              // [REJECT] The aggregator signature, signed_contribution_and_proof.signature, is
              // valid.
              if (!signatureVerifier.verify(
                  aggregatorPublicKey.get(),
                  syncCommitteeUtil.getContributionAndProofSigningRoot(state, contributionAndProof),
                  proof.getSignature())) {
                LOG.trace("Rejecting proof because aggregator signature is invalid");
                return REJECT;
              }

              final SpecConfigAltair config =
                  SpecConfigAltair.required(spec.getSpecConfig(contributionEpoch));
              final SyncCommittee currentSyncCommittee =
                  syncCommitteeUtil.getSyncCommittee(state, contributionEpoch);
              final int subcommitteeSize =
                  config.getSyncCommitteeSize() / SYNC_COMMITTEE_SUBNET_COUNT;

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
                                  currentSyncCommittee,
                                  contribution,
                                  subcommitteeSize,
                                  participantIndex))
                      .collect(Collectors.toList());

              if (!signatureVerifier.verify(
                  contributorPublicKeys,
                  syncCommitteeUtil.getSyncCommitteeSignatureSigningRoot(
                      contribution.getBeaconBlockRoot(), contributionEpoch, state.getForkInfo()),
                  contribution.getSignature())) {
                LOG.trace("Rejecting proof because aggregate signature is invalid");
                return REJECT;
              }

              if (!signatureVerifier.batchVerify()) {
                LOG.trace("Rejecting proof because batch signature check failed");
                return REJECT;
              }

              if (!seenIndices.add(uniquenessKey)) {
                // Got added by another thread while we were validating it
                return IGNORE;
              }

              return ACCEPT;
            });
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
        .computeSubnetsForSyncCommittee(state, contributionEpoch, aggregatorIndex)
        .contains(contribution.getSubcommitteeIndex().intValue());
  }

  private SafeFuture<Optional<BeaconStateAltair>> getState(
      final SyncCommitteeContribution contribution, final SyncCommitteeUtil syncCommitteeUtil) {
    return recentChainData
        .retrieveBlockState(contribution.getBeaconBlockRoot())
        // If the block is from an earlier epoch we need to process slots to the current epoch
        .<Optional<BeaconState>>thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                return Optional.empty();
              }
              final UInt64 contributionEpoch = spec.computeEpochAtSlot(contribution.getSlot());
              final UInt64 minEpoch =
                  syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(contributionEpoch);
              final UInt64 stateEpoch = spec.getCurrentEpoch(maybeState.get());
              if (stateEpoch.isLessThan(minEpoch)) {
                LOG.warn(
                    "Ignoring {} because it refers to a block that is too old. Block root {} from epoch {} is more than two sync committee periods before current epoch {}",
                    SignedContributionAndProof.class.getName(),
                    contribution.getBeaconBlockRoot(),
                    stateEpoch,
                    contributionEpoch);
                return Optional.empty();
              } else {
                return maybeState;
              }
            })
        .thenApply(maybeState -> maybeState.flatMap(BeaconState::toVersionAltair));
  }

  private TekuPair<UInt64, UInt64> getUniquenessKey(final SignedContributionAndProof proof) {
    return TekuPair.of(
        proof.getMessage().getAggregatorIndex(), proof.getMessage().getContribution().getSlot());
  }
}
