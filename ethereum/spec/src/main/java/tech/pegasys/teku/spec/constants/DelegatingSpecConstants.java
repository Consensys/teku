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
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class DelegatingSpecConstants implements SpecConstants {
  private final SpecConstants specConstants;

  public DelegatingSpecConstants(final SpecConstants specConstants) {
    this.specConstants = specConstants;
  }

  @Override
  public Map<String, Object> getRawConstants() {
    return specConstants.getRawConstants();
  }

  @Override
  public String getConfigName() {
    return specConstants.getConfigName();
  }

  @Override
  public UInt64 getBaseRewardsPerEpoch() {
    return specConstants.getBaseRewardsPerEpoch();
  }

  @Override
  public int getDepositContractTreeDepth() {
    return specConstants.getDepositContractTreeDepth();
  }

  @Override
  public int getJustificationBitsLength() {
    return specConstants.getJustificationBitsLength();
  }

  @Override
  public UInt64 getEth1FollowDistance() {
    return specConstants.getEth1FollowDistance();
  }

  @Override
  public int getMaxCommitteesPerSlot() {
    return specConstants.getMaxCommitteesPerSlot();
  }

  @Override
  public int getTargetCommitteeSize() {
    return specConstants.getTargetCommitteeSize();
  }

  @Override
  public int getMaxValidatorsPerCommittee() {
    return specConstants.getMaxValidatorsPerCommittee();
  }

  @Override
  public int getMinPerEpochChurnLimit() {
    return specConstants.getMinPerEpochChurnLimit();
  }

  @Override
  public int getChurnLimitQuotient() {
    return specConstants.getChurnLimitQuotient();
  }

  @Override
  public int getShuffleRoundCount() {
    return specConstants.getShuffleRoundCount();
  }

  @Override
  public int getMinGenesisActiveValidatorCount() {
    return specConstants.getMinGenesisActiveValidatorCount();
  }

  @Override
  public UInt64 getMinGenesisTime() {
    return specConstants.getMinGenesisTime();
  }

  @Override
  public UInt64 getHysteresisQuotient() {
    return specConstants.getHysteresisQuotient();
  }

  @Override
  public UInt64 getHysteresisDownwardMultiplier() {
    return specConstants.getHysteresisDownwardMultiplier();
  }

  @Override
  public UInt64 getHysteresisUpwardMultiplier() {
    return specConstants.getHysteresisUpwardMultiplier();
  }

  @Override
  public int getProportionalSlashingMultiplier() {
    return specConstants.getProportionalSlashingMultiplier();
  }

  @Override
  public UInt64 getMinDepositAmount() {
    return specConstants.getMinDepositAmount();
  }

  @Override
  public UInt64 getMaxEffectiveBalance() {
    return specConstants.getMaxEffectiveBalance();
  }

  @Override
  public UInt64 getEjectionBalance() {
    return specConstants.getEjectionBalance();
  }

  @Override
  public UInt64 getEffectiveBalanceIncrement() {
    return specConstants.getEffectiveBalanceIncrement();
  }

  @Override
  public Bytes4 getGenesisForkVersion() {
    return specConstants.getGenesisForkVersion();
  }

  @Override
  public Bytes getBlsWithdrawalPrefix() {
    return specConstants.getBlsWithdrawalPrefix();
  }

  @Override
  public UInt64 getGenesisDelay() {
    return specConstants.getGenesisDelay();
  }

  @Override
  public int getSecondsPerSlot() {
    return specConstants.getSecondsPerSlot();
  }

  @Override
  public int getMinAttestationInclusionDelay() {
    return specConstants.getMinAttestationInclusionDelay();
  }

  @Override
  public int getSlotsPerEpoch() {
    return specConstants.getSlotsPerEpoch();
  }

  @Override
  public int getMinSeedLookahead() {
    return specConstants.getMinSeedLookahead();
  }

  @Override
  public int getMaxSeedLookahead() {
    return specConstants.getMaxSeedLookahead();
  }

  @Override
  public UInt64 getMinEpochsToInactivityPenalty() {
    return specConstants.getMinEpochsToInactivityPenalty();
  }

  @Override
  public int getEpochsPerEth1VotingPeriod() {
    return specConstants.getEpochsPerEth1VotingPeriod();
  }

  @Override
  public int getSlotsPerHistoricalRoot() {
    return specConstants.getSlotsPerHistoricalRoot();
  }

  @Override
  public int getMinValidatorWithdrawabilityDelay() {
    return specConstants.getMinValidatorWithdrawabilityDelay();
  }

  @Override
  public UInt64 getShardCommitteePeriod() {
    return specConstants.getShardCommitteePeriod();
  }

  @Override
  public int getEpochsPerHistoricalVector() {
    return specConstants.getEpochsPerHistoricalVector();
  }

  @Override
  public int getEpochsPerSlashingsVector() {
    return specConstants.getEpochsPerSlashingsVector();
  }

  @Override
  public int getHistoricalRootsLimit() {
    return specConstants.getHistoricalRootsLimit();
  }

  @Override
  public long getValidatorRegistryLimit() {
    return specConstants.getValidatorRegistryLimit();
  }

  @Override
  public int getBaseRewardFactor() {
    return specConstants.getBaseRewardFactor();
  }

  @Override
  public int getWhistleblowerRewardQuotient() {
    return specConstants.getWhistleblowerRewardQuotient();
  }

  @Override
  public UInt64 getProposerRewardQuotient() {
    return specConstants.getProposerRewardQuotient();
  }

  @Override
  public UInt64 getInactivityPenaltyQuotient() {
    return specConstants.getInactivityPenaltyQuotient();
  }

  @Override
  public int getMinSlashingPenaltyQuotient() {
    return specConstants.getMinSlashingPenaltyQuotient();
  }

  @Override
  public int getMaxProposerSlashings() {
    return specConstants.getMaxProposerSlashings();
  }

  @Override
  public int getMaxAttesterSlashings() {
    return specConstants.getMaxAttesterSlashings();
  }

  @Override
  public int getMaxAttestations() {
    return specConstants.getMaxAttestations();
  }

  @Override
  public int getMaxDeposits() {
    return specConstants.getMaxDeposits();
  }

  @Override
  public int getMaxVoluntaryExits() {
    return specConstants.getMaxVoluntaryExits();
  }

  @Override
  public Bytes4 getDomainBeaconProposer() {
    return specConstants.getDomainBeaconProposer();
  }

  @Override
  public Bytes4 getDomainBeaconAttester() {
    return specConstants.getDomainBeaconAttester();
  }

  @Override
  public Bytes4 getDomainRandao() {
    return specConstants.getDomainRandao();
  }

  @Override
  public Bytes4 getDomainDeposit() {
    return specConstants.getDomainDeposit();
  }

  @Override
  public Bytes4 getDomainVoluntaryExit() {
    return specConstants.getDomainVoluntaryExit();
  }

  @Override
  public Bytes4 getDomainSelectionProof() {
    return specConstants.getDomainSelectionProof();
  }

  @Override
  public Bytes4 getDomainAggregateAndProof() {
    return specConstants.getDomainAggregateAndProof();
  }

  @Override
  public int getTargetAggregatorsPerCommittee() {
    return specConstants.getTargetAggregatorsPerCommittee();
  }

  @Override
  public UInt64 getSecondsPerEth1Block() {
    return specConstants.getSecondsPerEth1Block();
  }

  @Override
  public int getRandomSubnetsPerValidator() {
    return specConstants.getRandomSubnetsPerValidator();
  }

  @Override
  public int getEpochsPerRandomSubnetSubscription() {
    return specConstants.getEpochsPerRandomSubnetSubscription();
  }

  @Override
  public int getSafeSlotsToUpdateJustified() {
    return specConstants.getSafeSlotsToUpdateJustified();
  }

  @Override
  public int getDepositChainId() {
    return specConstants.getDepositChainId();
  }

  @Override
  public int getDepositNetworkId() {
    return specConstants.getDepositNetworkId();
  }

  @Override
  public Bytes getDepositContractAddress() {
    return specConstants.getDepositContractAddress();
  }
}
