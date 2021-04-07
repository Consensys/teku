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
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;
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
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.collections.TekuPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSigningData;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.ssz.type.Bytes4;
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

    return recentChainData
        .retrieveStateAtSlot(
            new SlotAndBlockRoot(contribution.getSlot(), contribution.getBeaconBlockRoot()))
        .thenApply(maybeState -> maybeState.flatMap(BeaconState::toVersionAltair))
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.trace("Ignoring proof because state is not available or not from Altair fork");
                return IGNORE;
              }
              final BeaconStateAltair state = maybeState.get();
              final SpecVersion specVersion = spec.atSlot(contribution.getSlot());
              final SpecConfigAltair specConfigAltair =
                  SpecConfigAltair.required(specVersion.getConfig());

              // [REJECT] The aggregator's validator index is within the current sync committee
              // i.e. state.validators[aggregate_and_proof.aggregator_index].pubkey in
              // state.current_sync_committee.pubkeys.
              final Optional<BLSPublicKey> aggregatorPublicKey =
                  specVersion
                      .beaconStateAccessors()
                      .getValidatorPubKey(state, contributionAndProof.getAggregatorIndex());
              if (aggregatorPublicKey.isEmpty()) {
                LOG.trace(
                    "Rejecting proof because aggregator index {} is an unknown validator",
                    contributionAndProof.getAggregatorIndex());
                return REJECT;
              }
              // Cheaper to compare keys based on the SSZ serialization than the parsed BLS key.
              final SszPublicKey sszAggregatorPublicKey =
                  new SszPublicKey(aggregatorPublicKey.get());
              final SyncCommittee currentSyncCommittee = state.getCurrentSyncCommittee();
              if (currentSyncCommittee.getPubkeys().stream()
                  .noneMatch(key -> key.equals(sszAggregatorPublicKey))) {
                LOG.trace(
                    "Rejecting proof because aggregator is not in the current sync committee");
                return REJECT;
              }

              //    [REJECT] contribution_and_proof.selection_proof selects the validator as an
              // aggregator for the slot -- i.e. is_sync_committee_aggregator(state,
              // contribution.slot, contribution_and_proof.selection_proof) returns True.
              if (!isSyncCommitteeAggregator(
                  specConfigAltair, contributionAndProof.getSelectionProof())) {
                LOG.trace("Rejecting proof because selection proof is not an aggregator");
                return REJECT;
              }

              // [REJECT] The contribution_and_proof.selection_proof is a valid signature of the
              // contribution.slot by the validator with index
              // contribution_and_proof.aggregator_index.
              final Bytes signingRoot =
                  getSigningRootForSyncCommitteeSlotSignature(
                      specVersion,
                      state,
                      contribution.getSlot(),
                      contribution.getSubcommitteeIndex());
              if (!BLS.verify(
                  aggregatorPublicKey.get(),
                  signingRoot,
                  contributionAndProof.getSelectionProof())) {
                LOG.trace("Rejecting proof because selection proof is invalid");
                return REJECT;
              }

              // [REJECT] The aggregator signature, signed_contribution_and_proof.signature, is
              // valid.
              if (!BLS.verify(
                  aggregatorPublicKey.get(),
                  getContributionAndProofSigningRoot(specVersion, state, contributionAndProof),
                  proof.getSignature())) {
                LOG.trace("Rejecting proof because aggregator signature is invalid");
                return REJECT;
              }

              // [REJECT] The aggregate signature is valid for the message beacon_block_root and
              // aggregate pubkey derived from the participation info in aggregation_bits for the
              // subcommittee specified by the subcommittee_index.
              final List<BLSPublicKey> contributorPublicKeys =
                  contribution
                      .getAggregationBits()
                      .streamAllSetBits()
                      .mapToObj(
                          index -> currentSyncCommittee.getPubkeys().get(index).getBLSPublicKey())
                      .collect(Collectors.toList());

              if (!BLS.fastAggregateVerify(
                  contributorPublicKeys,
                  getSyncCommitteeSignatureSigningRoot(specVersion, state, contribution),
                  contribution.getSignature())) {
                LOG.trace("Reejcting proof because aggregate signature is invalid");
                return REJECT;
              }

              if (!seenIndices.add(uniquenessKey)) {
                // Got added by another thread while we were validating it
                return IGNORE;
              }

              return ACCEPT;
            });
  }

  // TODO: Find this a better home
  private Bytes getSyncCommitteeSignatureSigningRoot(
      final SpecVersion specVersion,
      final BeaconState state,
      final SyncCommitteeContribution contribution) {
    final UInt64 epoch = spec.getCurrentEpoch(state);
    final Bytes32 domain =
        specVersion
            .getBeaconStateUtil()
            .getDomain(
                state,
                SpecConfigAltair.required(specVersion.getConfig()).getDomainSyncCommittee(),
                epoch);
    return specVersion
        .getBeaconStateUtil()
        .computeSigningRoot(contribution.getBeaconBlockRoot(), domain);
  }

  // TODO: Find this a better home
  private Bytes getContributionAndProofSigningRoot(
      final SpecVersion specVersion,
      final BeaconState state,
      final ContributionAndProof contributionAndProof) {
    final SyncCommitteeContribution contribution = contributionAndProof.getContribution();
    final BeaconStateUtil beaconStateUtil = specVersion.getBeaconStateUtil();
    final Bytes32 domain =
        beaconStateUtil.getDomain(
            state,
            SpecConfigAltair.required(specVersion.getConfig()).getDomainContributionAndProof(),
            specVersion.miscHelpers().computeEpochAtSlot(contribution.getSlot()));
    return beaconStateUtil.computeSigningRoot(contributionAndProof, domain);
  }

  // TODO: Should this be in the constants file?
  private static final int TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE = 4;

  // TODO: Find this a better home
  private boolean isSyncCommitteeAggregator(
      final SpecConfigAltair specConfig, final BLSSignature signature) {
    final int modulo =
        Math.max(
            1,
            specConfig.getSyncCommitteeSize()
                / SYNC_COMMITTEE_SUBNET_COUNT
                / TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE);
    return bytesToUInt64(Hash.sha2_256(signature.toSSZBytes().slice(0, 8))).mod(modulo).isZero();
  }

  // TODO: Find this a better home
  private Bytes getSigningRootForSyncCommitteeSlotSignature(
      final SpecVersion specVersion,
      final BeaconState state,
      final UInt64 slot,
      final UInt64 subcommitteeIndex) {
    final Bytes4 domainSyncCommitteeSelectionProof =
        SpecConfigAltair.required(specVersion.getConfig()).getDomainSyncCommitteeSelectionProof();
    final Bytes32 domain =
        specVersion
            .getBeaconStateUtil()
            .getDomain(
                state,
                domainSyncCommitteeSelectionProof,
                specVersion.miscHelpers().computeEpochAtSlot(slot));
    final SyncCommitteeSigningData signingData =
        SchemaDefinitionsAltair.required(specVersion.getSchemaDefinitions())
            .getSyncCommitteeSigningDataSchema()
            .create(slot, subcommitteeIndex);
    return specVersion.getBeaconStateUtil().computeSigningRoot(signingData, domain);
  }

  private TekuPair<UInt64, UInt64> getUniquenessKey(final SignedContributionAndProof proof) {
    return TekuPair.of(
        proof.getMessage().getAggregatorIndex(), proof.getMessage().getContribution().getSlot());
  }
}
