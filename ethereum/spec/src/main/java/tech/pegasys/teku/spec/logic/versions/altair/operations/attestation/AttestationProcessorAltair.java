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

package tech.pegasys.teku.spec.logic.versions.altair.operations.attestation;

import static tech.pegasys.teku.spec.constants.IncentivizationWeights.PROPOSER_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.integerSquareRoot;

import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.ParticipationFlags;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.operations.attestation.AttestationProcessor;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair.FlagIndexAndWeight;
import tech.pegasys.teku.ssz.SszMutableList;
import tech.pegasys.teku.ssz.collections.SszUInt64List;
import tech.pegasys.teku.ssz.primitive.SszByte;
import tech.pegasys.teku.ssz.primitive.SszUInt64;

public class AttestationProcessorAltair extends AttestationProcessor {

  private final SpecConfig specConfig;
  private final BeaconStateAccessorsAltair beaconStateAccessors;
  private final MiscHelpersAltair miscHelpersAltair;
  private final BeaconStateMutators beaconStateMutators;

  public AttestationProcessorAltair(
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final AttestationDataStateTransitionValidator attestationValidator,
      final SpecConfig specConfig,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final MiscHelpersAltair miscHelpersAltair,
      final BeaconStateMutators beaconStateMutators) {
    super(beaconStateUtil, attestationUtil, attestationValidator);
    this.specConfig = specConfig;
    this.beaconStateAccessors = beaconStateAccessors;
    this.miscHelpersAltair = miscHelpersAltair;
    this.beaconStateMutators = beaconStateMutators;
  }

  @Override
  protected void processAttestation(
      final MutableBeaconState genericState,
      final Attestation attestation,
      final IndexedAttestationProvider indexedAttestationProvider) {
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
        && stateSlot.equals(dataSlot.plus(specConfig.getMinAttestationInclusionDelay()))) {
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
    final SszUInt64List attestingIndices =
        indexedAttestationProvider.getIndexedAttestation(attestation).getAttesting_indices();
    for (SszUInt64 attestingIndex : attestingIndices) {
      final int index = attestingIndex.get().intValue();
      byte participationFlags = epochParticipation.get(index).get();
      final UInt64 baseReward = beaconStateAccessors.getBaseReward(state, index);
      boolean shouldUpdate = false;
      for (FlagIndexAndWeight flagIndicesAndWeight : miscHelpersAltair.getFlagIndicesAndWeights()) {
        final int flagIndex = flagIndicesAndWeight.getIndex();
        final UInt64 weight = flagIndicesAndWeight.getWeight();

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
}
