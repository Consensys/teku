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

package tech.pegasys.teku.spec.config;

import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.type.Bytes4;

public class DelegatingSpecConfig implements SpecConfig {
  protected final SpecConfig specConfig;

  public DelegatingSpecConfig(final SpecConfig specConfig) {
    this.specConfig = specConfig;
  }

  @Override
  public Map<String, Object> getRawConfig() {
    return specConfig.getRawConfig();
  }

  @Override
  public UInt64 getBaseRewardsPerEpoch() {
    return specConfig.getBaseRewardsPerEpoch();
  }

  @Override
  public int getDepositContractTreeDepth() {
    return specConfig.getDepositContractTreeDepth();
  }

  @Override
  public int getJustificationBitsLength() {
    return specConfig.getJustificationBitsLength();
  }

  @Override
  public UInt64 getEth1FollowDistance() {
    return specConfig.getEth1FollowDistance();
  }

  @Override
  public int getMaxCommitteesPerSlot() {
    return specConfig.getMaxCommitteesPerSlot();
  }

  @Override
  public int getTargetCommitteeSize() {
    return specConfig.getTargetCommitteeSize();
  }

  @Override
  public int getMaxValidatorsPerCommittee() {
    return specConfig.getMaxValidatorsPerCommittee();
  }

  @Override
  public int getMinPerEpochChurnLimit() {
    return specConfig.getMinPerEpochChurnLimit();
  }

  @Override
  public int getChurnLimitQuotient() {
    return specConfig.getChurnLimitQuotient();
  }

  @Override
  public int getShuffleRoundCount() {
    return specConfig.getShuffleRoundCount();
  }

  @Override
  public int getMinGenesisActiveValidatorCount() {
    return specConfig.getMinGenesisActiveValidatorCount();
  }

  @Override
  public UInt64 getMinGenesisTime() {
    return specConfig.getMinGenesisTime();
  }

  @Override
  public UInt64 getHysteresisQuotient() {
    return specConfig.getHysteresisQuotient();
  }

  @Override
  public UInt64 getHysteresisDownwardMultiplier() {
    return specConfig.getHysteresisDownwardMultiplier();
  }

  @Override
  public UInt64 getHysteresisUpwardMultiplier() {
    return specConfig.getHysteresisUpwardMultiplier();
  }

  @Override
  public int getProportionalSlashingMultiplier() {
    return specConfig.getProportionalSlashingMultiplier();
  }

  @Override
  public UInt64 getMinDepositAmount() {
    return specConfig.getMinDepositAmount();
  }

  @Override
  public UInt64 getMaxEffectiveBalance() {
    return specConfig.getMaxEffectiveBalance();
  }

  @Override
  public UInt64 getEjectionBalance() {
    return specConfig.getEjectionBalance();
  }

  @Override
  public UInt64 getEffectiveBalanceIncrement() {
    return specConfig.getEffectiveBalanceIncrement();
  }

  @Override
  public Bytes4 getGenesisForkVersion() {
    return specConfig.getGenesisForkVersion();
  }

  @Override
  public Bytes getBlsWithdrawalPrefix() {
    return specConfig.getBlsWithdrawalPrefix();
  }

  @Override
  public UInt64 getGenesisDelay() {
    return specConfig.getGenesisDelay();
  }

  @Override
  public int getSecondsPerSlot() {
    return specConfig.getSecondsPerSlot();
  }

  @Override
  public int getMinAttestationInclusionDelay() {
    return specConfig.getMinAttestationInclusionDelay();
  }

  @Override
  public int getSlotsPerEpoch() {
    return specConfig.getSlotsPerEpoch();
  }

  @Override
  public int getMinSeedLookahead() {
    return specConfig.getMinSeedLookahead();
  }

  @Override
  public int getMaxSeedLookahead() {
    return specConfig.getMaxSeedLookahead();
  }

  @Override
  public UInt64 getMinEpochsToInactivityPenalty() {
    return specConfig.getMinEpochsToInactivityPenalty();
  }

  @Override
  public int getEpochsPerEth1VotingPeriod() {
    return specConfig.getEpochsPerEth1VotingPeriod();
  }

  @Override
  public int getSlotsPerHistoricalRoot() {
    return specConfig.getSlotsPerHistoricalRoot();
  }

  @Override
  public int getMinValidatorWithdrawabilityDelay() {
    return specConfig.getMinValidatorWithdrawabilityDelay();
  }

  @Override
  public UInt64 getShardCommitteePeriod() {
    return specConfig.getShardCommitteePeriod();
  }

  @Override
  public int getEpochsPerHistoricalVector() {
    return specConfig.getEpochsPerHistoricalVector();
  }

  @Override
  public int getEpochsPerSlashingsVector() {
    return specConfig.getEpochsPerSlashingsVector();
  }

  @Override
  public int getHistoricalRootsLimit() {
    return specConfig.getHistoricalRootsLimit();
  }

  @Override
  public long getValidatorRegistryLimit() {
    return specConfig.getValidatorRegistryLimit();
  }

  @Override
  public int getBaseRewardFactor() {
    return specConfig.getBaseRewardFactor();
  }

  @Override
  public int getWhistleblowerRewardQuotient() {
    return specConfig.getWhistleblowerRewardQuotient();
  }

  @Override
  public UInt64 getProposerRewardQuotient() {
    return specConfig.getProposerRewardQuotient();
  }

  @Override
  public UInt64 getInactivityPenaltyQuotient() {
    return specConfig.getInactivityPenaltyQuotient();
  }

  @Override
  public int getMinSlashingPenaltyQuotient() {
    return specConfig.getMinSlashingPenaltyQuotient();
  }

  @Override
  public int getMaxProposerSlashings() {
    return specConfig.getMaxProposerSlashings();
  }

  @Override
  public int getMaxAttesterSlashings() {
    return specConfig.getMaxAttesterSlashings();
  }

  @Override
  public int getMaxAttestations() {
    return specConfig.getMaxAttestations();
  }

  @Override
  public int getMaxDeposits() {
    return specConfig.getMaxDeposits();
  }

  @Override
  public int getMaxVoluntaryExits() {
    return specConfig.getMaxVoluntaryExits();
  }

  @Override
  public int getTargetAggregatorsPerCommittee() {
    return specConfig.getTargetAggregatorsPerCommittee();
  }

  @Override
  public UInt64 getSecondsPerEth1Block() {
    return specConfig.getSecondsPerEth1Block();
  }

  @Override
  public int getRandomSubnetsPerValidator() {
    return specConfig.getRandomSubnetsPerValidator();
  }

  @Override
  public int getEpochsPerRandomSubnetSubscription() {
    return specConfig.getEpochsPerRandomSubnetSubscription();
  }

  @Override
  public int getSafeSlotsToUpdateJustified() {
    return specConfig.getSafeSlotsToUpdateJustified();
  }

  @Override
  public int getDepositChainId() {
    return specConfig.getDepositChainId();
  }

  @Override
  public int getDepositNetworkId() {
    return specConfig.getDepositNetworkId();
  }

  @Override
  public Bytes getDepositContractAddress() {
    return specConfig.getDepositContractAddress();
  }
}
