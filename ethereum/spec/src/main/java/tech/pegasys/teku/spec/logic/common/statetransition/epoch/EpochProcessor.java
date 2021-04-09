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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch;

import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.TotalBalances;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;

public interface EpochProcessor {
  RewardAndPenaltyDeltas getRewardAndPenaltyDeltas(
      BeaconState state, ValidatorStatuses validatorStatuses);

  BeaconState processEpoch(BeaconState preState) throws EpochProcessingException;

  void processJustificationAndFinalization(MutableBeaconState state, TotalBalances totalBalances)
      throws EpochProcessingException;

  void processInactivityUpdates(MutableBeaconState state, ValidatorStatuses validatorStatuses);

  void processRewardsAndPenalties(MutableBeaconState state, ValidatorStatuses validatorStatuses)
      throws EpochProcessingException;

  void processRegistryUpdates(MutableBeaconState state, List<ValidatorStatus> statuses)
      throws EpochProcessingException;

  void processSlashings(MutableBeaconState state, UInt64 totalBalance);

  void processParticipationUpdates(MutableBeaconState genericState);

  void processEth1DataReset(MutableBeaconState state);

  void processEffectiveBalanceUpdates(MutableBeaconState state);

  void processSlashingsReset(MutableBeaconState state);

  void processRandaoMixesReset(MutableBeaconState state);

  void processHistoricalRootsUpdate(MutableBeaconState state);

  void processSyncCommitteeUpdates(MutableBeaconState state);
}
