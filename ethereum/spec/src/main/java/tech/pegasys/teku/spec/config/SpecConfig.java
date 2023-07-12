/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.config;

import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;

public interface SpecConfig extends NetworkingSpecConfig {
  // Non-configurable constants
  UInt64 GENESIS_SLOT = UInt64.ZERO;
  UInt64 GENESIS_EPOCH = UInt64.ZERO;
  UInt64 FAR_FUTURE_EPOCH = UInt64.MAX_VALUE;

  static SpecConfigBuilder builder() {
    return new SpecConfigBuilder();
  }

  Map<String, Object> getRawConfig();

  // Config: Genesis
  int getMinGenesisActiveValidatorCount();

  UInt64 getMinGenesisTime();

  Bytes4 getGenesisForkVersion();

  UInt64 getGenesisDelay();

  // Config: Time parameters
  int getSecondsPerSlot();

  default int getMillisPerSlot() {
    return getSecondsPerSlot() * 1000;
  }

  int getSecondsPerEth1Block();

  int getMinValidatorWithdrawabilityDelay();

  UInt64 getShardCommitteePeriod();

  UInt64 getEth1FollowDistance();

  // Config: Validator cycle
  UInt64 getEjectionBalance();

  int getMinPerEpochChurnLimit();

  int getChurnLimitQuotient();

  // Config: Fork choice
  int getProposerScoreBoost();

  // Config: Deposit contract
  long getDepositChainId();

  long getDepositNetworkId();

  Eth1Address getDepositContractAddress();

  @Override
  default int getMinEpochsForBlockRequests() {
    return getMinValidatorWithdrawabilityDelay() + getChurnLimitQuotient() / 2;
  }

  // Phase0 non-configurable Misc Constants
  UInt64 getBaseRewardsPerEpoch();

  int getDepositContractTreeDepth();

  int getJustificationBitsLength();

  // Phase0 non-configurable Withdrawal prefixes Constants
  Bytes getBlsWithdrawalPrefix();

  // Phase0 Misc preset
  int getMaxCommitteesPerSlot();

  int getTargetCommitteeSize();

  int getMaxValidatorsPerCommittee();

  int getShuffleRoundCount();

  UInt64 getHysteresisQuotient();

  UInt64 getHysteresisDownwardMultiplier();

  UInt64 getHysteresisUpwardMultiplier();

  // Phase0 Gwei values preset
  UInt64 getMinDepositAmount();

  UInt64 getMaxEffectiveBalance();

  UInt64 getEffectiveBalanceIncrement();

  // Phase0 Time parameters preset
  int getMinAttestationInclusionDelay();

  int getSlotsPerEpoch();

  /** Returns integerSquareRoot(getSlotsPerEpoch()) but with the benefit of precalculating. */
  long getSquareRootSlotsPerEpoch();

  int getMinSeedLookahead();

  int getMaxSeedLookahead();

  UInt64 getMinEpochsToInactivityPenalty();

  int getEpochsPerEth1VotingPeriod();

  int getSlotsPerHistoricalRoot();

  // Phase0 State list lengths preset
  int getEpochsPerHistoricalVector();

  int getEpochsPerSlashingsVector();

  int getHistoricalRootsLimit();

  long getValidatorRegistryLimit();

  // Phase0 Rewards and penalties preset
  int getBaseRewardFactor();

  int getWhistleblowerRewardQuotient();

  UInt64 getProposerRewardQuotient();

  UInt64 getInactivityPenaltyQuotient();

  int getMinSlashingPenaltyQuotient();

  int getProportionalSlashingMultiplier();

  // Phase0 Max operations per block preset
  int getMaxProposerSlashings();

  int getMaxAttesterSlashings();

  int getMaxAttestations();

  int getMaxDeposits();

  int getMaxVoluntaryExits();

  // Misc
  int getSafeSlotsToUpdateJustified();

  // Casters
  default Optional<SpecConfigAltair> toVersionAltair() {
    return Optional.empty();
  }

  default Optional<SpecConfigBellatrix> toVersionBellatrix() {
    return Optional.empty();
  }

  default Optional<SpecConfigCapella> toVersionCapella() {
    return Optional.empty();
  }

  default Optional<SpecConfigDeneb> toVersionDeneb() {
    return Optional.empty();
  }
}
