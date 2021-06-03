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

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.integerSquareRoot;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.ByteUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.constants.ParticipationFlags;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
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

  /**
   * Return the base reward for the validator defined by index with respect to the current state.
   *
   * <p>Note: An optimally performing validator can earn one base reward per epoch over a long time
   * horizon. This takes into account both per-epoch (e.g. attestation) and intermittent duties
   * (e.g. block proposal and sync committees).
   *
   * @param state the current state
   * @param validatorIndex the index of the validator to calculate the base reward for
   * @return the calculated base reward
   */
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
   * Return the sequence of sync committee indices (which may include duplicate indices) for the
   * next sync committee, given a state at a sync committee period boundary.
   *
   * <p>Note: Committee can contain duplicate indices for small validator sets (less than
   * SYNC_COMMITTEE_SIZE + 128)
   *
   * @param state the state to calculate committees from
   * @return the sequence of sync committee indices
   */
  public List<Integer> getNextSyncCommitteeIndices(final BeaconState state) {
    final UInt64 epoch = getCurrentEpoch(state).plus(1);
    final List<Integer> activeValidatorIndices = getActiveValidatorIndices(state, epoch);
    final int activeValidatorCount = activeValidatorIndices.size();
    final Bytes32 seed = getSeed(state, epoch, Domain.SYNC_COMMITTEE);
    int i = 0;
    final SszList<Validator> validators = state.getValidators();
    final List<Integer> syncCommitteeIndices = new ArrayList<>();
    while (syncCommitteeIndices.size() < altairConfig.getSyncCommitteeSize()) {
      final int shuffledIndex =
          miscHelpers.computeShuffledIndex(i % activeValidatorCount, activeValidatorCount, seed);
      final Integer candidateIndex = activeValidatorIndices.get(shuffledIndex);
      final int randomByte =
          ByteUtil.toUnsignedInt(
              Hash.sha2_256(Bytes.wrap(seed, uint64ToBytes(i / 32))).get(i % 32));
      final UInt64 effectiveBalance = validators.get(candidateIndex).getEffective_balance();
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
   * Return the *next* sync committee for a given state.
   *
   * <p>SyncCommittee contains an aggregate pubkey that enables resource-constrained clients to save
   * some computation when verifying the sync committee's signature.
   *
   * <p>SyncCommittee can also contain duplicate pubkeys, when {@link
   * #getNextSyncCommitteeIndices(BeaconState)} returns duplicate indices. Implementations must take
   * care when handling optimizations relating to aggregation and verification in the presence of
   * duplicates.
   *
   * <p>Note: This function should only be called at sync committee period boundaries by {@link
   * tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor#processSyncCommitteeUpdates(MutableBeaconState)}
   * as {@link #getNextSyncCommitteeIndices(BeaconState)} is not stable within a given period.
   *
   * @param state the state to get the sync committee for
   * @return the SyncCommittee
   */
  public SyncCommittee getNextSyncCommittee(final BeaconState state) {
    final List<Integer> indices = getNextSyncCommitteeIndices(state);
    final List<BLSPublicKey> pubkeys =
        indices.stream()
            .map(index -> getValidatorPubKey(state, UInt64.valueOf(index)).orElseThrow())
            .collect(toList());
    final BLSPublicKey aggregatePubkey = BLSPublicKey.aggregate(pubkeys);

    return ((BeaconStateSchemaAltair) state.getSchema())
        .getNextSyncCommitteeSchema()
        .create(
            pubkeys.stream().map(SszPublicKey::new).collect(toList()),
            new SszPublicKey(aggregatePubkey));
  }

  /**
   * Return the flag indices that are satisifed by an attestation.
   *
   * @param state the current state
   * @param data the attestation to check
   * @param inclusionDelay the inclusion delay of the attestation
   * @return the flag indices that are satisfied by data with respect to state.
   */
  public List<Integer> getAttestationParticipationFlagIndices(
      final BeaconState state, final AttestationData data, final UInt64 inclusionDelay) {
    final Checkpoint justifiedCheckpoint;
    if (data.getTarget().getEpoch().equals(getCurrentEpoch(state))) {
      justifiedCheckpoint = state.getCurrent_justified_checkpoint();
    } else {
      justifiedCheckpoint = state.getPrevious_justified_checkpoint();
    }

    // Matching roots
    final boolean isMatchingSource = data.getSource().equals(justifiedCheckpoint);
    final boolean isMatchingTarget =
        isMatchingSource
            && data.getTarget().getRoot().equals(getBlockRoot(state, data.getTarget().getEpoch()));
    final boolean isMatchingHead =
        isMatchingTarget
            && data.getBeacon_block_root().equals(getBlockRootAtSlot(state, data.getSlot()));

    // Participation flag indices
    final List<Integer> participationFlagIndices = new ArrayList<>();
    if (isMatchingSource
        && inclusionDelay.isLessThanOrEqualTo(integerSquareRoot(config.getSlotsPerEpoch()))) {
      participationFlagIndices.add(ParticipationFlags.TIMELY_SOURCE_FLAG_INDEX);
    }
    if (isMatchingTarget && inclusionDelay.isLessThanOrEqualTo(config.getSlotsPerEpoch())) {
      participationFlagIndices.add(ParticipationFlags.TIMELY_TARGET_FLAG_INDEX);
    }
    if (isMatchingHead
        && inclusionDelay.equals(UInt64.valueOf(config.getMinAttestationInclusionDelay()))) {
      participationFlagIndices.add(ParticipationFlags.TIMELY_HEAD_FLAG_INDEX);
    }
    return participationFlagIndices;
  }
}
