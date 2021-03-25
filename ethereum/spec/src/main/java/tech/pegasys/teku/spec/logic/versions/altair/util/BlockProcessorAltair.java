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

package tech.pegasys.teku.spec.logic.versions.altair.util;

import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.integerSquareRoot;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.constants.IncentivizationWeights;
import tech.pegasys.teku.spec.constants.ParticipationFlags;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair.FlagIndexAndWeight;
import tech.pegasys.teku.ssz.SszMutableList;
import tech.pegasys.teku.ssz.SszVector;
import tech.pegasys.teku.ssz.primitive.SszByte;

public class BlockProcessorAltair extends AbstractBlockProcessor {
  private final SpecConfigAltair specConfigAltair;
  private final MiscHelpersAltair miscHelpersAltair;
  private final BeaconStateAccessorsAltair beaconStateAccessorsAltair;

  public BlockProcessorAltair(
      final SpecConfigAltair specConfig,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final MiscHelpersAltair miscHelpers,
      final AttestationDataStateTransitionValidator attestationValidator) {
    super(
        specConfig,
        beaconStateUtil,
        attestationUtil,
        validatorsUtil,
        miscHelpers,
        beaconStateAccessors,
        attestationValidator);

    this.specConfigAltair = specConfig;
    this.miscHelpersAltair = miscHelpers;
    this.beaconStateAccessorsAltair = beaconStateAccessors;
  }

  @Override
  protected void processAttestationNoValidation(
      final MutableBeaconState genericState, final Attestation attestation) {
    final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(genericState);
    final AttestationData data = attestation.getData();

    final SszMutableList<SszByte> epochParticipation;
    final Checkpoint justifiedCheckpoint;
    if (data.getTarget().getEpoch().equals(beaconStateAccessors.getCurrentEpoch(state))) {
      epochParticipation = state.getCurrentEpochParticipation();
      justifiedCheckpoint = state.getCurrent_justified_checkpoint();
    } else {
      epochParticipation = state.getPreviousEpochParticipation();
      justifiedCheckpoint = state.getPrevious_justified_checkpoint();
    }

    // Matching roots
    final boolean isMatchingHead =
        data.getBeacon_block_root()
            .equals(beaconStateUtil.getBlockRootAtSlot(state, data.getSlot()));
    final boolean isMatchingSource = data.getSource().equals(justifiedCheckpoint);
    final boolean isMatchingTarget =
        data.getTarget()
            .getRoot()
            .equals(beaconStateUtil.getBlockRoot(state, data.getTarget().getEpoch()));

    // Participation flag indices
    final List<Integer> participationFlagIndices = new ArrayList<>();
    final UInt64 stateSlot = state.getSlot();
    final UInt64 dataSlot = data.getSlot();
    if (isMatchingHead
        && isMatchingTarget
        && stateSlot.isLessThanOrEqualTo(
            dataSlot.plus(specConfig.getMinAttestationInclusionDelay()))) {
      participationFlagIndices.add(ParticipationFlags.TIMELY_HEAD_FLAG_INDEX);
    }
    if (isMatchingSource
        && stateSlot.isLessThanOrEqualTo(
            dataSlot.plus(integerSquareRoot(specConfig.getSlotsPerEpoch())))) {
      participationFlagIndices.add(ParticipationFlags.TIMELY_SOURCE_FLAG_INDEX);
    }
    if (isMatchingTarget
        && stateSlot.isLessThanOrEqualTo(dataSlot.plus(specConfig.getSlotsPerEpoch()))) {
      participationFlagIndices.add(ParticipationFlags.TIMELY_TARGET_FLAG_INDEX);
    }

    // Update epoch participation flags
    UInt64 proposerRewardNumerator = UInt64.ZERO;
    final List<Integer> attestingIndices =
        attestationUtil.getAttestingIndices(state, data, attestation.getAggregation_bits());
    for (Integer index : attestingIndices) {
      final byte participationFlags = epochParticipation.get(index).get();
      final UInt64 baseReward = beaconStateAccessorsAltair.getBaseReward(state, index);
      boolean shouldUpdate = false;
      for (FlagIndexAndWeight flagIndicesAndWeight : miscHelpersAltair.getFlagIndicesAndWeights()) {
        final int flagIndex = flagIndicesAndWeight.getIndex();
        final UInt64 weight = flagIndicesAndWeight.getWeight();

        if (participationFlagIndices.contains(flagIndex)
            && !miscHelpersAltair.hasFlag(participationFlags, flagIndex)) {
          shouldUpdate = true;
          miscHelpersAltair.addFlag(participationFlags, flagIndex);
          proposerRewardNumerator = proposerRewardNumerator.plus(baseReward.times(weight));
        }
      }

      if (shouldUpdate) {
        epochParticipation.set(index, SszByte.of(participationFlags));

        final int proposerIndex = beaconStateUtil.getBeaconProposerIndex(state);
        final UInt64 proposerReward =
            proposerRewardNumerator.dividedBy(
                specConfig.getProposerRewardQuotient().times(WEIGHT_DENOMINATOR));
        validatorsUtil.increaseBalance(state, proposerIndex, proposerReward);
      }
    }
  }

  public void processSyncCommittee(
      final MutableBeaconStateAltair state, final SyncAggregate aggregate)
      throws BlockProcessingException {
    final UInt64 previousSlot = state.getSlot().minusMinZero(1);
    final List<Integer> committeeIndices =
        beaconStateAccessorsAltair.getSyncCommitteeIndices(
            state, beaconStateAccessors.getCurrentEpoch(state));
    final SszVector<SszPublicKey> committeePubkeys = state.getCurrentSyncCommittee().getPubkeys();
    final List<Integer> includedIndices = new ArrayList<>();
    final List<BLSPublicKey> includedPubkeys = new ArrayList<>();
    aggregate
        .getSyncCommitteeBits()
        .streamAllSetBits()
        .forEach(
            index -> {
              includedIndices.add(committeeIndices.get(index));
              includedPubkeys.add(committeePubkeys.get(index).getBLSPublicKey());
            });
    final Bytes32 domain =
        beaconStateUtil.getDomain(
            state,
            specConfigAltair.getDomainSyncCommittee(),
            miscHelpers.computeEpochAtSlot(previousSlot));
    final Bytes32 signingRoot =
        beaconStateUtil.computeSigningRoot(
            beaconStateUtil.getBlockRootAtSlot(state, previousSlot), domain);

    if (!eth2FastAggregateVerify(
        includedPubkeys, signingRoot, aggregate.getSyncCommitteeSignature().getSignature())) {
      throw new BlockProcessingException("Invalid sync committee signature in " + aggregate);
    }

    // Compute the maximum sync rewards for the slot
    final UInt64 totalActiveIncrements =
        beaconStateAccessors
            .getTotalActiveBalance(state)
            .dividedBy(specConfig.getEffectiveBalanceIncrement());
    final UInt64 totalBaseRewards =
        beaconStateAccessorsAltair.getBaseRewardPerIncrement(state).times(totalActiveIncrements);
    final UInt64 maxEpochRewards =
        totalBaseRewards
            .times(IncentivizationWeights.SYNC_REWARD_WEIGHT)
            .dividedBy(IncentivizationWeights.WEIGHT_DENOMINATOR);
    final UInt64 maxSlotRewards =
        maxEpochRewards
            .times(includedIndices.size())
            .dividedBy(committeeIndices.size())
            .dividedBy(specConfig.getSlotsPerEpoch());

    // Compute the participant and proposer sync rewards
    final SszMutableList<Validator> validators = state.getValidators();
    final UInt64 committeeEffectiveBalance =
        includedIndices.stream()
            .map(includedIndex -> validators.get(includedIndex).getEffective_balance())
            .reduce(UInt64.ZERO, UInt64::plus)
            .max(specConfig.getEffectiveBalanceIncrement());

    UInt64 proposerReward = UInt64.ZERO;
    final int beaconProposerIndex = beaconStateUtil.getBeaconProposerIndex(state);
    for (Integer includedIndex : includedIndices) {
      final UInt64 effectiveBalance = validators.get(includedIndex).getEffective_balance();
      final UInt64 inclusionReward =
          maxSlotRewards.times(effectiveBalance).dividedBy(committeeEffectiveBalance);
      UInt64 proposerShare = inclusionReward.dividedBy(specConfig.getProposerRewardQuotient());
      proposerReward = proposerReward.plus(proposerShare);
      validatorsUtil.increaseBalance(state, includedIndex, inclusionReward.minus(proposerShare));
    }
    validatorsUtil.increaseBalance(state, beaconProposerIndex, proposerReward);
  }

  private boolean eth2FastAggregateVerify(
      List<BLSPublicKey> pubkeys, Bytes32 message, BLSSignature signature) {
    if (pubkeys.isEmpty() && signature.isInfinity()) {
      return true;
    }
    return BLS.fastAggregateVerify(pubkeys, message, signature);
  }
}
