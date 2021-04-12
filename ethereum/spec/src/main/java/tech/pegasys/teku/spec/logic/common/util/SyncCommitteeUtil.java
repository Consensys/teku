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

package tech.pegasys.teku.spec.logic.common.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContributionSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSigningData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.ssz.SszVector;
import tech.pegasys.teku.ssz.type.Bytes4;

public class SyncCommitteeUtil {

  // TODO: Should this be in constants file? https://github.com/ethereum/eth2.0-specs/issues/2317
  private static final int TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE = 4;

  private final BeaconStateAccessors beaconStateAccessors;
  private final BeaconStateUtil beaconStateUtil;
  private final ValidatorsUtil validatorsUtil;
  private final SpecConfigAltair specConfig;
  private final MiscHelpers miscHelpers;
  private final SchemaDefinitionsAltair schemaDefinitionsAltair;

  public SyncCommitteeUtil(
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorsUtil validatorsUtil,
      final SpecConfigAltair specConfig,
      final MiscHelpers miscHelpers,
      final SchemaDefinitionsAltair schemaDefinitionsAltair) {
    this.beaconStateAccessors = beaconStateAccessors;
    this.beaconStateUtil = beaconStateUtil;
    this.validatorsUtil = validatorsUtil;
    this.specConfig = specConfig;
    this.miscHelpers = miscHelpers;
    this.schemaDefinitionsAltair = schemaDefinitionsAltair;
  }

  public Map<UInt64, SyncSubcommitteeAssignments> getSyncSubcommittees(
      final BeaconState state, final UInt64 epoch) {
    final UInt64 syncCommitteePeriod = computeSyncCommitteePeriod(epoch);
    final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(state);
    final UInt64 currentSyncCommitteePeriod = computeSyncCommitteePeriod(currentEpoch);
    final UInt64 nextSyncCommitteePeriod = currentSyncCommitteePeriod.increment();
    checkArgument(
        syncCommitteePeriod.equals(currentSyncCommitteePeriod)
            || syncCommitteePeriod.equals(nextSyncCommitteePeriod),
        "State must be in the same or previous sync committee period");
    final BeaconStateAltair altairState = BeaconStateAltair.required(state);
    return BeaconStateCache.getTransitionCaches(altairState)
        .getSyncCommitteeCache()
        .get(
            syncCommitteePeriod,
            period -> {
              final SyncCommittee syncCommittee;
              if (syncCommitteePeriod.equals(currentSyncCommitteePeriod)) {
                syncCommittee = altairState.getCurrentSyncCommittee();
              } else {
                syncCommittee = altairState.getNextSyncCommittee();
              }
              final SszVector<SszPublicKey> pubkeys = syncCommittee.getPubkeys();
              final Map<UInt64, Map<Integer, Set<Integer>>> subcommitteeAssignments =
                  new HashMap<>();
              for (int index = 0; index < pubkeys.size(); index++) {
                final BLSPublicKey pubkey = pubkeys.get(index).getBLSPublicKey();
                final UInt64 validatorIndex =
                    UInt64.valueOf(
                        validatorsUtil
                            .getValidatorIndex(altairState, pubkey)
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "Unknown validator assigned to sync committee: "
                                            + pubkey)));
                final int subcommitteeSize =
                    specConfig.getSyncCommitteeSize() / SYNC_COMMITTEE_SUBNET_COUNT;
                final int subcommitteeIndex = index / subcommitteeSize;
                final int subcommitteeParticipationIndex =
                    index - (subcommitteeIndex * subcommitteeSize);

                subcommitteeAssignments
                    .computeIfAbsent(validatorIndex, __ -> new HashMap<>())
                    .computeIfAbsent(subcommitteeIndex, __ -> new HashSet<>())
                    .add(subcommitteeParticipationIndex);
              }
              return subcommitteeAssignments.entrySet().stream()
                  .collect(
                      toUnmodifiableMap(
                          Map.Entry::getKey,
                          entry -> new SyncSubcommitteeAssignments(entry.getValue())));
            });
  }

  public boolean isSyncCommitteeAggregator(final BLSSignature signature) {
    final int modulo =
        Math.max(
            1,
            specConfig.getSyncCommitteeSize()
                / SYNC_COMMITTEE_SUBNET_COUNT
                / TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE);
    return bytesToUInt64(Hash.sha2_256(signature.toSSZBytes()).slice(0, 8)).mod(modulo).isZero();
  }

  public boolean isAssignedToSyncCommittee(
      final BeaconStateAltair state, final UInt64 epoch, final UInt64 validatorIndex) {
    return getSyncSubcommittees(state, epoch).containsKey(validatorIndex);
  }

  public Set<Integer> computeSubnetsForSyncCommittee(
      final BeaconStateAltair state, final UInt64 validatorIndex) {
    final SyncSubcommitteeAssignments assignments =
        getSyncSubcommittees(state, beaconStateAccessors.getCurrentEpoch(state))
            .get(validatorIndex);
    return assignments == null ? Collections.emptySet() : assignments.getAssignedSubcommittees();
  }

  public Bytes32 getSyncCommitteeSignatureSigningRoot(
      final Bytes32 blockRoot, final UInt64 epoch, final ForkInfo forkInfo) {
    final Bytes32 domain =
        beaconStateUtil.getDomain(
            specConfig.getDomainSyncCommittee(),
            epoch,
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return beaconStateUtil.computeSigningRoot(blockRoot, domain);
  }

  public Bytes getSyncCommitteeContributionSigningRoot(
      final BeaconState state, final SyncCommitteeContribution contribution) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(contribution.getSlot());
    final Bytes32 domain =
        beaconStateUtil.getDomain(state, specConfig.getDomainSyncCommittee(), epoch);
    return beaconStateUtil.computeSigningRoot(contribution.getBeaconBlockRoot(), domain);
  }

  public Bytes getContributionAndProofSigningRoot(
      final BeaconState state, final ContributionAndProof contributionAndProof) {
    final ForkInfo forkInfo = state.getForkInfo();
    return getContributionAndProofSigningRoot(contributionAndProof, forkInfo);
  }

  public Bytes getContributionAndProofSigningRoot(
      final ContributionAndProof contributionAndProof, final ForkInfo forkInfo) {
    final SyncCommitteeContribution contribution = contributionAndProof.getContribution();
    final Bytes32 domain =
        beaconStateUtil.getDomain(
            specConfig.getDomainContributionAndProof(),
            miscHelpers.computeEpochAtSlot(contribution.getSlot()),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return beaconStateUtil.computeSigningRoot(contributionAndProof, domain);
  }

  public Bytes getSyncCommitteeSigningDataSigningRoot(
      final SyncCommitteeSigningData signingData, final ForkInfo forkInfo) {
    final Bytes4 domainSyncCommitteeSelectionProof =
        specConfig.getDomainSyncCommitteeSelectionProof();
    final Bytes32 domain =
        beaconStateUtil.getDomain(
            domainSyncCommitteeSelectionProof,
            miscHelpers.computeEpochAtSlot(signingData.getSlot()),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return beaconStateUtil.computeSigningRoot(signingData, domain);
  }

  public SyncCommitteeSigningData createSyncCommitteeSigningData(
      final UInt64 slot, final UInt64 subcommitteeIndex) {
    return schemaDefinitionsAltair
        .getSyncCommitteeSigningDataSchema()
        .create(slot, subcommitteeIndex);
  }

  public SyncCommitteeContribution createSyncCommitteeContribution(
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final UInt64 subcommitteeIndex,
      final Iterable<Integer> setParticipationBits,
      final BLSSignature signature) {
    final SyncCommitteeContributionSchema schema =
        schemaDefinitionsAltair.getSyncCommitteeContributionSchema();
    return schema.create(
        slot,
        beaconBlockRoot,
        subcommitteeIndex,
        schema.getAggregationBitsSchema().ofBits(setParticipationBits),
        signature);
  }

  public ContributionAndProof createContributionAndProof(
      final UInt64 aggregatorIndex,
      final SyncCommitteeContribution contribution,
      final BLSSignature selectionProof) {
    return schemaDefinitionsAltair
        .getContributionAndProofSchema()
        .create(aggregatorIndex, contribution, selectionProof);
  }

  public SignedContributionAndProof createSignedContributionAndProof(
      final ContributionAndProof message, final BLSSignature signature) {
    return schemaDefinitionsAltair.getSignedContributionAndProofSchema().create(message, signature);
  }

  private UInt64 computeSyncCommitteePeriod(final UInt64 epoch) {
    return epoch.dividedBy(specConfig.getEpochsPerSyncCommitteePeriod());
  }
}
