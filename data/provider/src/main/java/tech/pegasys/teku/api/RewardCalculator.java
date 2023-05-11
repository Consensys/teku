/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.api;

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.PROPOSER_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.migrated.BlockRewardData;
import tech.pegasys.teku.api.migrated.SyncCommitteeRewardData;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class RewardCalculator {
  private final Spec spec;

  RewardCalculator(Spec spec) {
    this.spec = spec;
  }

  ObjectAndMetaData<BlockRewardData> getBlockRewardData(
      final BlockAndMetaData blockAndMetaData, final BeaconState state) {
    final BeaconBlock block = blockAndMetaData.getData().getMessage();
    if (!spec.atSlot(block.getSlot()).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)) {
      throw new BadRequestException(
          "Slot "
              + block.getSlot()
              + " is pre altair, and no sync committee information is available");
    }

    final long proposerReward = getProposerReward(state);
    return blockAndMetaData.map(__ -> calculateBlockRewards(proposerReward, block, state));
  }

  long getProposerReward(final BeaconState state) {
    final UInt64 participantReward = spec.getSyncCommitteeParticipantReward(state);
    return participantReward
        .times(PROPOSER_WEIGHT)
        .dividedBy(WEIGHT_DENOMINATOR.minus(PROPOSER_WEIGHT))
        .longValue();
  }

  Map<Integer, Integer> getCommitteeIndices(
      final List<BLSPublicKey> committeeKeys,
      final Set<String> validators,
      final BeaconState state) {
    if (validators.isEmpty()) {
      final List<Integer> result =
          committeeKeys.stream()
              .flatMap(pubkey -> spec.getValidatorIndex(state, pubkey).stream())
              .collect(toList());

      return IntStream.range(0, result.size())
          .boxed()
          .collect(Collectors.<Integer, Integer, Integer>toMap(Function.identity(), result::get));
    }

    checkValidatorsList(committeeKeys, validators);

    final Map<Integer, Integer> output = new HashMap<>();
    for (int i = 0; i < committeeKeys.size(); i++) {
      final BLSPublicKey key = committeeKeys.get(i);
      final Optional<Integer> validatorIndex = spec.getValidatorIndex(state, key);
      if (validatorIndex.isPresent()
          && (validators.contains(key.toHexString())
              || validators.contains(validatorIndex.get().toString()))) {
        output.put(i, validatorIndex.get());
      }
    }

    return output;
  }

  SyncCommitteeRewardData getSyncCommitteeRewardData(
      Set<String> validators, BlockAndMetaData blockAndMetadata, BeaconState state) {
    final BeaconBlock block = blockAndMetadata.getData().getMessage();
    if (!spec.atSlot(block.getSlot()).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)) {
      throw new BadRequestException(
          "Slot "
              + block.getSlot()
              + " is pre altair, and no sync committee information is available");
    }

    final UInt64 epoch = spec.computeEpochAtSlot(block.getSlot());
    final SyncCommittee committee =
        spec.getSyncCommitteeUtil(block.getSlot()).orElseThrow().getSyncCommittee(state, epoch);
    final List<BLSPublicKey> committeeKeys =
        committee.getPubkeys().stream().map(SszPublicKey::getBLSPublicKey).collect(toList());
    final Map<Integer, Integer> committeeIndices =
        getCommitteeIndices(committeeKeys, validators, state);
    final UInt64 participantReward = spec.getSyncCommitteeParticipantReward(state);

    final SyncCommitteeRewardData rewardData =
        new SyncCommitteeRewardData(
            blockAndMetadata.isExecutionOptimistic(), blockAndMetadata.isFinalized());
    return calculateSyncCommitteeRewards(
        committeeIndices,
        participantReward.longValue(),
        block.getBody().getOptionalSyncAggregate(),
        rewardData);
  }

  private BlockRewardData calculateBlockRewards(
      final long proposerReward, final BeaconBlock block, final BeaconState state) {
    final SyncAggregate aggregate = block.getBody().getOptionalSyncAggregate().orElseThrow();

    final UInt64 proposerIndex = block.getProposerIndex();
    final long attestationsBlockRewards = calculateAttestationRewards();
    final long syncAggregateBlockRewards =
        calculateProposerSyncAggregateBlockRewards(proposerReward, aggregate);
    final long proposerSlashingsBlockRewards = calculateProposerSlashingsRewards(block, state);
    final long attesterSlashingsBlockRewards = calculateAttesterSlashingsRewards(block, state);

    return new BlockRewardData(
        proposerIndex,
        attestationsBlockRewards,
        syncAggregateBlockRewards,
        proposerSlashingsBlockRewards,
        attesterSlashingsBlockRewards);
  }

  @VisibleForTesting
  long calculateAttestationRewards() {
    return 0L;
  }

  @VisibleForTesting
  long calculateProposerSyncAggregateBlockRewards(long proposerReward, SyncAggregate aggregate) {
    final SszBitvector syncCommitteeBits = aggregate.getSyncCommitteeBits();
    return proposerReward * syncCommitteeBits.getBitCount();
  }

  @VisibleForTesting
  long calculateProposerSlashingsRewards(final BeaconBlock beaconBlock, final BeaconState state) {
    final SszList<ProposerSlashing> proposerSlashings =
        beaconBlock.getBody().getProposerSlashings();

    final UInt64 epoch = spec.computeEpochAtSlot(state.getSlot());
    final SpecConfig specConfig = spec.getSpecConfig(epoch);

    long proposerSlashingsRewards = 0;
    for (ProposerSlashing slashing : proposerSlashings) {
      final int slashedIndex = slashing.getHeader1().getMessage().getProposerIndex().intValue();
      proposerSlashingsRewards =
          calculateSlashingRewards(specConfig, state, slashedIndex, proposerSlashingsRewards);
    }

    return proposerSlashingsRewards;
  }

  @VisibleForTesting
  long calculateAttesterSlashingsRewards(final BeaconBlock beaconBlock, final BeaconState state) {
    final SszList<AttesterSlashing> attesterSlashings =
        beaconBlock.getBody().getAttesterSlashings();

    final UInt64 epoch = spec.computeEpochAtSlot(state.getSlot());
    final SpecConfig specConfig = spec.getSpecConfig(epoch);

    long attesterSlashingsRewards = 0;
    for (AttesterSlashing slashing : attesterSlashings) {
      for (final UInt64 index : slashing.getIntersectingValidatorIndices()) {
        attesterSlashingsRewards =
            calculateSlashingRewards(specConfig, state, index.intValue(), attesterSlashingsRewards);
      }
    }

    return attesterSlashingsRewards;
  }

  private long calculateSlashingRewards(
      final SpecConfig specConfig,
      final BeaconState state,
      final int slashedIndex,
      final long currentRewards) {
    final Validator validator = state.getValidators().get(slashedIndex);
    final UInt64 whistleblowerReward =
        validator.getEffectiveBalance().dividedBy(specConfig.getWhistleblowerRewardQuotient());
    final UInt64 proposerReward =
        whistleblowerReward.dividedBy(specConfig.getProposerRewardQuotient());
    final UInt64 rewardsAdditions = proposerReward.plus(whistleblowerReward.minus(proposerReward));
    return currentRewards + rewardsAdditions.longValue();
  }

  @VisibleForTesting
  SyncCommitteeRewardData calculateSyncCommitteeRewards(
      final Map<Integer, Integer> committeeIndices,
      final long participantReward,
      final Optional<SyncAggregate> maybeAggregate,
      final SyncCommitteeRewardData data) {
    if (maybeAggregate.isEmpty()) {
      return data;
    }

    final SyncAggregate aggregate = maybeAggregate.get();

    committeeIndices.forEach(
        (i, key) -> {
          if (aggregate.getSyncCommitteeBits().getBit(i)) {
            data.increaseReward(key, participantReward);
          } else {
            data.decreaseReward(key, participantReward);
          }
        });

    return data;
  }

  @VisibleForTesting
  void checkValidatorsList(List<BLSPublicKey> committeeKeys, Set<String> validators) {
    final Set<BLSPublicKey> keysSet = new HashSet<>(committeeKeys);
    for (String v : validators) {
      if (v.startsWith("0x")) {
        if (!keysSet.contains(BLSPublicKey.fromHexString(v))) {
          throw new BadRequestException(String.format("'%s' was not found in the committee", v));
        }
      } else {
        try {
          final int index = Integer.parseInt(v);
          if (index < 0 || index >= committeeKeys.size()) {
            throw new BadRequestException(
                String.format(
                    "index '%s' is not in the expected committee range 0 - %d",
                    v, committeeKeys.size()));
          }
        } catch (NumberFormatException e) {
          throw new BadRequestException(
              String.format(
                  "'%s' was expected to be a committee index but could not be read as a number",
                  v));
        }
      }
    }
  }
}
