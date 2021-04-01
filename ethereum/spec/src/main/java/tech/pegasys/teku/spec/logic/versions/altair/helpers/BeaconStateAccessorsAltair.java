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

package tech.pegasys.teku.spec.logic.versions.altair.helpers;

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.integerSquareRoot;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uintToBytes32;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.ByteUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.ssz.SszList;

public class BeaconStateAccessorsAltair extends BeaconStateAccessors {

  private static final int MAX_RANDOM_BYTE = 255; // 2**8 - 1
  private final SpecConfigAltair altairConfig;

  public BeaconStateAccessorsAltair(
      final SpecConfigAltair config,
      final Predicates predicates,
      final MiscHelpersAltair miscHelpers) {
    super(config, predicates, miscHelpers);
    this.altairConfig = config;
  }

  public UInt64 getBaseRewardPerIncrement(final BeaconState state) {
    return config
        .getEffectiveBalanceIncrement()
        .times(config.getBaseRewardFactor())
        .dividedBy(integerSquareRoot(getTotalActiveBalance(state)));
  }

  public UInt64 getBaseReward(final BeaconState state, final int validatorIndex) {
    final UInt64 increments =
        state
            .getValidators()
            .get(validatorIndex)
            .getEffective_balance()
            .dividedBy(config.getEffectiveBalanceIncrement());
    return increments.times(getBaseRewardPerIncrement(state));
  }

  /**
   * Return the sequence of sync committee indices (which may include uplicate indices) for a given
   * state and epoch.
   *
   * @param state the state to calculate committees from
   * @param epoch the epoch to calcualte committees for
   * @return the sequence of sync committee indices
   */
  public List<Integer> getSyncCommitteeIndices(final BeaconState state, final UInt64 epoch) {
    final int epochsPerSyncCommitteePeriod = altairConfig.getEpochsPerSyncCommitteePeriod();
    final UInt64 baseEpoch =
        epoch
            .dividedBy(epochsPerSyncCommitteePeriod)
            .minusMinZero(1)
            .times(epochsPerSyncCommitteePeriod);
    final List<Integer> activeValidatorIndices = getActiveValidatorIndices(state, baseEpoch);
    final int activeValidatorCount = activeValidatorIndices.size();
    final Bytes32 seed = getSeed(state, baseEpoch, altairConfig.getDomainSyncCommittee());
    int i = 0;
    final SszList<Validator> validators = state.getValidators();
    final List<Integer> syncCommitteeIndices = new ArrayList<>();
    while (syncCommitteeIndices.size() < altairConfig.getSyncCommitteeSize()) {
      final int shuffledIndex =
          miscHelpers.computeShuffledIndex(i % activeValidatorCount, activeValidatorCount, seed);
      final Integer candidateIndex = activeValidatorIndices.get(shuffledIndex);
      final int randomByte =
          ByteUtil.toUnsignedInt(
              Hash.sha2_256(Bytes.wrap(seed, uintToBytes32(i / 32))).get(i % 32));
      final UInt64 effectiveBalance = validators.get(candidateIndex).getEffective_balance();
      // Sample with replacement
      if (effectiveBalance
          .times(MAX_RANDOM_BYTE)
          .isGreaterThanOrEqualTo(config.getMaxEffectiveBalance().times(randomByte))) {
        syncCommitteeIndices.add(candidateIndex);
      }
      i++;
    }

    return syncCommitteeIndices;
  }

  /**
   * Return the sync committee for a given state and epoch.
   *
   * @param state the state to get the sync committee for
   * @param epoch the epoch to get the sync committee for
   * @return the SyncCommittee
   */
  public SyncCommittee getSyncCommittee(final BeaconState state, final UInt64 epoch) {
    final List<Integer> indices = getSyncCommitteeIndices(state, epoch);
    final List<BLSPublicKey> pubkeys =
        indices.stream()
            .map(index -> getValidatorPubKey(state, UInt64.valueOf(index)).orElseThrow())
            .collect(toList());

    final int syncSubcommitteeSize = altairConfig.getSyncSubcommitteeSize();
    final List<SszPublicKey> pubkeyAggregates = new ArrayList<>();
    checkState(
        pubkeys.size() % syncSubcommitteeSize == 0,
        "SYNC_COMMITTEE_SIZE must be a multiple of SYNC_SUBCOMMITTEE_SIZE");
    for (int i = 0; i < pubkeys.size(); i += syncSubcommitteeSize) {
      final List<BLSPublicKey> subcommitteePubkeys = pubkeys.subList(i, i + syncSubcommitteeSize);
      pubkeyAggregates.add(new SszPublicKey(BLSPublicKey.aggregate(subcommitteePubkeys)));
    }

    return ((BeaconStateSchemaAltair) state.getSchema())
        .getNextSyncCommitteeSchema()
        .create(pubkeys.stream().map(SszPublicKey::new).collect(toList()), pubkeyAggregates);
  }
}
