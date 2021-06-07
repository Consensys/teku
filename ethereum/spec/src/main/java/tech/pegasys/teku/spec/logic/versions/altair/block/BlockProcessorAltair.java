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

package tech.pegasys.teku.spec.logic.versions.altair.block;

import static tech.pegasys.teku.spec.constants.IncentivizationWeights.PROPOSER_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.SYNC_REWARD_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;
import static tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair.PARTICIPATION_FLAG_WEIGHTS;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.ssz.SszMutableList;
import tech.pegasys.teku.ssz.SszVector;
import tech.pegasys.teku.ssz.collections.SszUInt64List;
import tech.pegasys.teku.ssz.primitive.SszByte;
import tech.pegasys.teku.ssz.primitive.SszUInt64;

public class BlockProcessorAltair extends AbstractBlockProcessor {
  private final SpecConfigAltair specConfigAltair;
  private final MiscHelpersAltair miscHelpersAltair;
  private final BeaconStateAccessorsAltair beaconStateAccessorsAltair;

  public BlockProcessorAltair(
      final SpecConfigAltair specConfig,
      final Predicates predicates,
      final MiscHelpersAltair miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator) {
    super(
        specConfig,
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        beaconStateUtil,
        attestationUtil,
        validatorsUtil,
        operationValidator);

    this.specConfigAltair = specConfig;
    this.miscHelpersAltair = miscHelpers;
    this.beaconStateAccessorsAltair = beaconStateAccessors;
  }

  @Override
  public void processBlock(
      final MutableBeaconState genericState,
      final BeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(genericState);
    final BeaconBlockBodyAltair blockBody = BeaconBlockBodyAltair.required(block.getBody());

    super.processBlock(state, block, indexedAttestationCache, signatureVerifier);
    processSyncCommittee(state, blockBody.getSyncAggregate(), signatureVerifier);
  }

  @Override
  protected void processAttestation(
      final MutableBeaconState genericState,
      final Attestation attestation,
      final IndexedAttestationProvider indexedAttestationProvider) {
    final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(genericState);
    final AttestationData data = attestation.getData();

    final List<Integer> participationFlagIndices =
        beaconStateAccessorsAltair.getAttestationParticipationFlagIndices(
            state, data, state.getSlot().minus(data.getSlot()));

    // Update epoch participation flags
    final SszMutableList<SszByte> epochParticipation;
    if (data.getTarget().getEpoch().equals(beaconStateAccessors.getCurrentEpoch(state))) {
      epochParticipation = state.getCurrentEpochParticipation();
    } else {
      epochParticipation = state.getPreviousEpochParticipation();
    }

    UInt64 proposerRewardNumerator = UInt64.ZERO;
    final SszUInt64List attestingIndices =
        indexedAttestationProvider.getIndexedAttestation(attestation).getAttesting_indices();
    for (SszUInt64 attestingIndex : attestingIndices) {
      final int index = attestingIndex.get().intValue();
      byte participationFlags = epochParticipation.get(index).get();
      final UInt64 baseReward = beaconStateAccessorsAltair.getBaseReward(state, index);
      boolean shouldUpdate = false;
      for (int flagIndex = 0; flagIndex < PARTICIPATION_FLAG_WEIGHTS.size(); flagIndex++) {
        final UInt64 weight = PARTICIPATION_FLAG_WEIGHTS.get(flagIndex);

        if (participationFlagIndices.contains(flagIndex)
            && !miscHelpersAltair.hasFlag(participationFlags, flagIndex)) {
          shouldUpdate = true;
          participationFlags = miscHelpersAltair.addFlag(participationFlags, flagIndex);
          proposerRewardNumerator = proposerRewardNumerator.plus(baseReward.times(weight));
        }
      }

      if (shouldUpdate) {
        epochParticipation.set(index, SszByte.of(participationFlags));
      }
    }

    if (!proposerRewardNumerator.isZero()) {
      final int proposerIndex = beaconStateAccessors.getBeaconProposerIndex(state);
      final UInt64 proposerRewardDenominator =
          WEIGHT_DENOMINATOR
              .minus(PROPOSER_WEIGHT)
              .times(WEIGHT_DENOMINATOR)
              .dividedBy(PROPOSER_WEIGHT);
      final UInt64 proposerReward = proposerRewardNumerator.dividedBy(proposerRewardDenominator);
      beaconStateMutators.increaseBalance(state, proposerIndex, proposerReward);
    }
  }

  @Override
  protected void processNewValidator(final MutableBeaconState genericState, final Deposit deposit) {
    super.processNewValidator(genericState, deposit);
    final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(genericState);

    state.getPreviousEpochParticipation().append(SszByte.ZERO);
    state.getCurrentEpochParticipation().append(SszByte.ZERO);
    state.getInactivityScores().append(SszUInt64.ZERO);
  }

  @Override
  public void processSyncCommittee(
      final MutableBeaconState baseState,
      final SyncAggregate aggregate,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(baseState);
    final SszVector<SszPublicKey> committeePubkeys = state.getCurrentSyncCommittee().getPubkeys();
    final List<BLSPublicKey> participantPubkeys = new ArrayList<>();
    aggregate
        .getSyncCommitteeBits()
        .streamAllSetBits()
        .forEach(index -> participantPubkeys.add(committeePubkeys.get(index).getBLSPublicKey()));
    final UInt64 previousSlot = state.getSlot().minusMinZero(1);
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            state, Domain.SYNC_COMMITTEE, miscHelpers.computeEpochAtSlot(previousSlot));
    final Bytes32 signingRoot =
        miscHelpersAltair.computeSigningRoot(
            beaconStateAccessors.getBlockRootAtSlot(state, previousSlot), domain);

    if (!eth2FastAggregateVerify(
        signatureVerifier,
        participantPubkeys,
        signingRoot,
        aggregate.getSyncCommitteeSignature().getSignature())) {
      throw new BlockProcessingException("Invalid sync committee signature in " + aggregate);
    }

    // Compute participant and proposer rewards
    final UInt64 totalActiveIncrements =
        beaconStateAccessors
            .getTotalActiveBalance(state)
            .dividedBy(specConfig.getEffectiveBalanceIncrement());
    final UInt64 totalBaseRewards =
        beaconStateAccessorsAltair.getBaseRewardPerIncrement(state).times(totalActiveIncrements);
    final UInt64 maxParticipantRewards =
        totalBaseRewards
            .times(SYNC_REWARD_WEIGHT)
            .dividedBy(WEIGHT_DENOMINATOR)
            .dividedBy(specConfig.getSlotsPerEpoch());
    final UInt64 participantReward =
        maxParticipantRewards.dividedBy(specConfigAltair.getSyncCommitteeSize());
    final UInt64 proposerReward =
        participantReward
            .times(PROPOSER_WEIGHT)
            .dividedBy(WEIGHT_DENOMINATOR.minus(PROPOSER_WEIGHT));

    // Apply participant and proposer rewards
    participantPubkeys.stream()
        .map(pubkey -> validatorsUtil.getValidatorIndex(state, pubkey).orElseThrow())
        .forEach(
            participantIndex ->
                beaconStateMutators.increaseBalance(state, participantIndex, participantReward));
    UInt64 totalProposerReward = proposerReward.times(participantPubkeys.size());
    beaconStateMutators.increaseBalance(
        state, beaconStateAccessors.getBeaconProposerIndex(state), totalProposerReward);
  }

  private boolean eth2FastAggregateVerify(
      final BLSSignatureVerifier signatureVerifier,
      List<BLSPublicKey> pubkeys,
      Bytes32 message,
      BLSSignature signature) {
    if (pubkeys.isEmpty() && signature.isInfinity()) {
      return true;
    }
    return signatureVerifier.verify(pubkeys, message, signature);
  }
}
