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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.constants.IncentivizationWeights;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.SszMutableList;
import tech.pegasys.teku.ssz.SszVector;

public class BlockProcessorAltair extends AbstractBlockProcessor {

  private final SpecConfigAltair altairSpecConfig;
  private final BeaconStateAccessorsAltair altairBeaconStateAccessors;

  public BlockProcessorAltair(
      final SpecConfigAltair altairSpecConfig,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateAccessorsAltair altairBeaconStateAccessors,
      final MiscHelpersAltair miscHelpers) {
    super(
        altairSpecConfig,
        beaconStateUtil,
        attestationUtil,
        validatorsUtil,
        miscHelpers,
        altairBeaconStateAccessors);
    this.altairSpecConfig = altairSpecConfig;
    this.altairBeaconStateAccessors = altairBeaconStateAccessors;
  }

  @Override
  public void processAttestationsNoValidation(
      final MutableBeaconState state, final SszList<Attestation> attestations)
      throws BlockProcessingException {
    throw new NotImplementedException("TODO");
  }

  public void processSyncCommittee(
      final MutableBeaconStateAltair state, final SyncAggregate aggregate)
      throws BlockProcessingException {
    final UInt64 previousSlot = state.getSlot().minusMinZero(1);
    final List<Integer> committeeIndices =
        altairBeaconStateAccessors.getSyncCommitteeIndices(
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
            altairSpecConfig.getDomainSyncCommittee(),
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
        altairBeaconStateAccessors.getBaseRewardPerIncrement(state).times(totalActiveIncrements);
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

    final int beaconProposerIndex = beaconStateUtil.getBeaconProposerIndex(state);
    includedIndices.forEach(
        includedIndex -> {
          final UInt64 effectiveBalance = validators.get(includedIndex).getEffective_balance();
          final UInt64 inclusionReward =
              maxSlotRewards.times(effectiveBalance).dividedBy(committeeEffectiveBalance);
          final UInt64 proposerReward =
              inclusionReward.dividedBy(specConfig.getProposerRewardQuotient());
          validatorsUtil.increaseBalance(state, beaconProposerIndex, proposerReward);
          validatorsUtil.increaseBalance(
              state, includedIndex, inclusionReward.minus(proposerReward));
        });
  }

  private boolean eth2FastAggregateVerify(
      List<BLSPublicKey> pubkeys, Bytes32 message, BLSSignature signature) {
    if (pubkeys.isEmpty() && signature.isInfinity()) {
      return true;
    }
    return BLS.fastAggregateVerify(pubkeys, message, signature);
  }
}
