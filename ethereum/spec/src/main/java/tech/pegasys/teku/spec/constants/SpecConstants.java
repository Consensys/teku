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

package tech.pegasys.teku.spec.constants;

import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.type.Bytes4;

public interface SpecConstants {
  // Non-configurable constants
  UInt64 GENESIS_SLOT = UInt64.ZERO;
  UInt64 GENESIS_EPOCH = UInt64.ZERO;
  UInt64 FAR_FUTURE_EPOCH = UInt64.MAX_VALUE;

  static SpecConstantsBuilder builder() {
    return new SpecConstantsBuilder();
  }

  Map<String, Object> getRawConstants();

  String getConfigName();

  UInt64 getBaseRewardsPerEpoch();

  int getDepositContractTreeDepth();

  int getJustificationBitsLength();

  UInt64 getEth1FollowDistance();

  int getMaxCommitteesPerSlot();

  int getTargetCommitteeSize();

  int getMaxValidatorsPerCommittee();

  int getMinPerEpochChurnLimit();

  int getChurnLimitQuotient();

  int getShuffleRoundCount();

  int getMinGenesisActiveValidatorCount();

  UInt64 getMinGenesisTime();

  UInt64 getHysteresisQuotient();

  UInt64 getHysteresisDownwardMultiplier();

  UInt64 getHysteresisUpwardMultiplier();

  int getProportionalSlashingMultiplier();

  UInt64 getMinDepositAmount();

  UInt64 getMaxEffectiveBalance();

  UInt64 getEjectionBalance();

  UInt64 getEffectiveBalanceIncrement();

  Bytes4 getGenesisForkVersion();

  Bytes getBlsWithdrawalPrefix();

  UInt64 getGenesisDelay();

  int getSecondsPerSlot();

  int getMinAttestationInclusionDelay();

  int getSlotsPerEpoch();

  int getMinSeedLookahead();

  int getMaxSeedLookahead();

  UInt64 getMinEpochsToInactivityPenalty();

  int getEpochsPerEth1VotingPeriod();

  int getSlotsPerHistoricalRoot();

  int getMinValidatorWithdrawabilityDelay();

  UInt64 getShardCommitteePeriod();

  int getEpochsPerHistoricalVector();

  int getEpochsPerSlashingsVector();

  int getHistoricalRootsLimit();

  long getValidatorRegistryLimit();

  int getBaseRewardFactor();

  int getWhistleblowerRewardQuotient();

  UInt64 getProposerRewardQuotient();

  UInt64 getInactivityPenaltyQuotient();

  int getMinSlashingPenaltyQuotient();

  int getMaxProposerSlashings();

  int getMaxAttesterSlashings();

  int getMaxAttestations();

  int getMaxDeposits();

  int getMaxVoluntaryExits();

  Bytes4 getDomainBeaconProposer();

  Bytes4 getDomainBeaconAttester();

  Bytes4 getDomainRandao();

  Bytes4 getDomainDeposit();

  Bytes4 getDomainVoluntaryExit();

  Bytes4 getDomainSelectionProof();

  Bytes4 getDomainAggregateAndProof();

  int getTargetAggregatorsPerCommittee();

  UInt64 getSecondsPerEth1Block();

  int getRandomSubnetsPerValidator();

  int getEpochsPerRandomSubnetSubscription();

  int getSafeSlotsToUpdateJustified();

  int getDepositChainId();

  int getDepositNetworkId();

  Bytes getDepositContractAddress();

  default Optional<SpecConstantsAltair> toVersionAltair() {
    return Optional.empty();
  }
}
