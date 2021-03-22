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

import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.integerSquareRoot;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.bls.BLSPublicKey;
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

  public List<Integer> getSyncCommitteeIndices(final BeaconState state, final UInt64 epoch) {
    // Implemented in https://github.com/ConsenSys/teku/pull/3754/
    return null;
  }

  /**
   * Return the sync comittee for a given state and epoch.
   *
   * @param state the state to get the sync committee for
   * @param epoch the epoch to get the sync committee for
   * @return the SyncCommittee
   */
  public SyncCommittee getSyncCommittee(final BeaconState state, final UInt64 epoch) {
    final SszList<Validator> validators = state.getValidators();
    final List<Integer> indices = getSyncCommitteeIndices(state, epoch);
    final List<SszPublicKey> pubkeys =
        indices.stream()
            .map(index -> validators.get(index).getSszPublicKey())
            .collect(Collectors.toList());

    final int syncSubcommitteeSize = altairConfig.getSyncSubcommitteeSize();
    final List<List<SszPublicKey>> subcommittees = new ArrayList<>();
    Preconditions.checkState(
        pubkeys.size() % syncSubcommitteeSize == 0,
        "SYNC_COMMITTEE_SIZE must be a multiple of SYNC_SUBCOMMITTEE_SIZE");
    for (int i = 0; i < pubkeys.size(); i += syncSubcommitteeSize) {
      subcommittees.add(pubkeys.subList(i, i + syncSubcommitteeSize));
    }
    final List<SszPublicKey> pubkeyAggregates =
        subcommittees.stream().map(this::aggregatePublicKeys).collect(Collectors.toList());

    return ((BeaconStateSchemaAltair) state.getSchema())
        .getNextSyncCommitteeSchema()
        .create(pubkeys, pubkeyAggregates);
  }

  private SszPublicKey aggregatePublicKeys(final List<SszPublicKey> publicKeys) {
    return new SszPublicKey(
        BLSPublicKey.aggregate(
            publicKeys.stream().map(SszPublicKey::getBLSPublicKey).collect(Collectors.toList())));
  }
}
