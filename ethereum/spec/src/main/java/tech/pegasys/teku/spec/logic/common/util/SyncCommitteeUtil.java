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

import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContributionSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSigningData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.ssz.type.Bytes4;

public class SyncCommitteeUtil {

  // TODO: Should this be in constants file? https://github.com/ethereum/eth2.0-specs/issues/2317
  private static final int TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE = 4;

  private final BeaconStateAccessors beaconStateAccessors;
  private final BeaconStateUtil beaconStateUtil;
  private final SpecConfigAltair specConfig;
  private final MiscHelpers miscHelpers;
  private final SchemaDefinitionsAltair schemaDefinitionsAltair;

  public SyncCommitteeUtil(
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateUtil beaconStateUtil,
      final SpecConfigAltair specConfig,
      final MiscHelpers miscHelpers,
      final SchemaDefinitionsAltair schemaDefinitionsAltair) {
    this.beaconStateAccessors = beaconStateAccessors;
    this.beaconStateUtil = beaconStateUtil;
    this.specConfig = specConfig;
    this.miscHelpers = miscHelpers;
    this.schemaDefinitionsAltair = schemaDefinitionsAltair;
  }

  public Bytes32 getSyncCommitteeSignatureSigningRoot(
      final BeaconState state, final Bytes32 blockRoot) {
    return getSyncCommitteeSignatureSigningRoot(
        state.getForkInfo(), beaconStateAccessors.getCurrentEpoch(state), blockRoot);
  }

  public Bytes32 getSyncCommitteeSignatureSigningRoot(
      final ForkInfo forkInfo, final UInt64 epoch, final Bytes32 blockRoot) {
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

  public boolean isSyncCommitteeAggregator(final BLSSignature signature) {
    final int modulo =
        Math.max(
            1,
            specConfig.getSyncCommitteeSize()
                / SYNC_COMMITTEE_SUBNET_COUNT
                / TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE);
    return bytesToUInt64(Hash.sha2_256(signature.toSSZBytes()).slice(0, 8)).mod(modulo).isZero();
  }

  public Bytes getSyncCommitteeSigningDataSigningRoot(
      final BeaconState state, final UInt64 slot, final UInt64 subcommitteeIndex) {
    final SyncCommitteeSigningData signingData =
        createSyncCommitteeSigningData(slot, subcommitteeIndex);
    final ForkInfo forkInfo = state.getForkInfo();
    return getSyncCommitteeSigningDataSigningRoot(signingData, forkInfo);
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
