/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.core;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class BeaconChainConstants {
  // Non-configurable constants
  private final long genesisSlot = 0;
  private final long genesisEpoch = 0;
  private final UInt64 farFutureEpoch = UInt64.MAX_VALUE;
  private final UInt64 baseRewardsPerEpoch = UInt64.valueOf(4);
  private final int depositContractTreeDepth = 32;
  private final int justificationBitsLength = 4;

  // Misc
  private final UInt64 eth1FollowDistance;
  private final int maxCommitteesPerSlot;
  private final int targetCommitteeSize;
  private final int maxValidatorsPerCommittee;
  private final int minPerEpochChurnLimit;
  private final int churnLimitQuotient;
  private final int shuffleRoundCount;
  private final int minGenesisActiveValidatorCount;
  private final UInt64 minGenesisTime;
  private final UInt64 hysteresisQuotient;
  private final UInt64 hysteresisDownwardMultiplier;
  private final UInt64 hysteresisUpwardMultiplier;
  private final int proportionalSlashingMultiplier;

  // Gwei values
  private final UInt64 minDepositAmount;
  private final UInt64 maxEffectiveBalance;
  private final UInt64 ejectionBalance;
  private final UInt64 effectiveBalanceIncrement;

  // Initial values
  private final Bytes4 genesisForkVersion;
  private final Bytes blsWithdrawalPrefix;

  // Time parameters
  private final UInt64 genesisDelay;
  private final int secondsPerSlot;
  private final int minAttestationInclusionDelay;
  private final int slotsPerEpoch;
  private final int minSeedLookahead;
  private final int maxSeedLookahead;
  private final UInt64 minEpochsToInactivityPenalty;
  private final int epochsPerEth1VotingPeriod;
  private final int slotsPerHistoricalRoot;
  private final int minValidatorWithdrawabilityDelay;
  private final UInt64 shardCommitteePeriod;

  // State list lengths
  private final int epochsPerHistoricalVector;
  private final int epochsPerSlashingsVector;
  private final int historicalRootsLimit;
  private final long validatorRegistryLimit;

  // Reward and penalty quotients
  private final int baseRewardFactor;
  private final int whistleblowerRewardQuotient;
  private final UInt64 proposerRewardQuotient;
  private final UInt64 inactivityPenaltyQuotient;
  private final int minSlashingPenaltyQuotient;

  // Max transactions per block
  private final int maxProposerSlashings;
  private final int maxAttesterSlashings;
  private final int maxAttestations;
  private final int maxDeposits;
  private final int maxVoluntaryExits;

  // Signature domains
  private final Bytes4 domainBeaconProposer;
  private final Bytes4 domainBeaconAttester;
  private final Bytes4 domainRandao;
  private final Bytes4 domainDeposit;
  private final Bytes4 domainVoluntaryExit;
  private final Bytes4 domainSelectionProof;
  private final Bytes4 domainAggregateAndProof;

  // Validator
  private final int targetAggregatorsPerCommittee;
  private final UInt64 secondsPerEth1Block;
  private final int randomSubnetsPerValidator;
  private final int epochsPerRandomSubnetSubscription;

  // Fork Choice
  private final int safeSlotsToUpdateJustified;

  // Deposit Contract
  private final int depositChainId;
  private final int depositNetworkId;
  private final Bytes depositContractAddress;

  BeaconChainConstants(
      final UInt64 eth1FollowDistance,
      final int maxCommitteesPerSlot,
      final int targetCommitteeSize,
      final int maxValidatorsPerCommittee,
      final int minPerEpochChurnLimit,
      final int churnLimitQuotient,
      final int shuffleRoundCount,
      final int minGenesisActiveValidatorCount,
      final UInt64 minGenesisTime,
      final UInt64 hysteresisQuotient,
      final UInt64 hysteresisDownwardMultiplier,
      final UInt64 hysteresisUpwardMultiplier,
      final int proportionalSlashingMultiplier,
      final UInt64 minDepositAmount,
      final UInt64 maxEffectiveBalance,
      final UInt64 ejectionBalance,
      final UInt64 effectiveBalanceIncrement,
      final Bytes4 genesisForkVersion,
      final Bytes blsWithdrawalPrefix,
      final UInt64 genesisDelay,
      final int secondsPerSlot,
      final int minAttestationInclusionDelay,
      final int slotsPerEpoch,
      final int minSeedLookahead,
      final int maxSeedLookahead,
      final UInt64 minEpochsToInactivityPenalty,
      final int epochsPerEth1VotingPeriod,
      final int slotsPerHistoricalRoot,
      final int minValidatorWithdrawabilityDelay,
      final UInt64 shardCommitteePeriod,
      final int epochsPerHistoricalVector,
      final int epochsPerSlashingsVector,
      final int historicalRootsLimit,
      final long validatorRegistryLimit,
      final int baseRewardFactor,
      final int whistleblowerRewardQuotient,
      final UInt64 proposerRewardQuotient,
      final UInt64 inactivityPenaltyQuotient,
      final int minSlashingPenaltyQuotient,
      final int maxProposerSlashings,
      final int maxAttesterSlashings,
      final int maxAttestations,
      final int maxDeposits,
      final int maxVoluntaryExits,
      final Bytes4 domainBeaconProposer,
      final Bytes4 domainBeaconAttester,
      final Bytes4 domainRandao,
      final Bytes4 domainDeposit,
      final Bytes4 domainVoluntaryExit,
      final Bytes4 domainSelectionProof,
      final Bytes4 domainAggregateAndProof,
      final int targetAggregatorsPerCommittee,
      final UInt64 secondsPerEth1Block,
      final int randomSubnetsPerValidator,
      final int epochsPerRandomSubnetSubscription,
      final int safeSlotsToUpdateJustified,
      final int depositChainId,
      final int depositNetworkId,
      final Bytes depositContractAddress) {
    this.eth1FollowDistance = eth1FollowDistance;
    this.maxCommitteesPerSlot = maxCommitteesPerSlot;
    this.targetCommitteeSize = targetCommitteeSize;
    this.maxValidatorsPerCommittee = maxValidatorsPerCommittee;
    this.minPerEpochChurnLimit = minPerEpochChurnLimit;
    this.churnLimitQuotient = churnLimitQuotient;
    this.shuffleRoundCount = shuffleRoundCount;
    this.minGenesisActiveValidatorCount = minGenesisActiveValidatorCount;
    this.minGenesisTime = minGenesisTime;
    this.hysteresisQuotient = hysteresisQuotient;
    this.hysteresisDownwardMultiplier = hysteresisDownwardMultiplier;
    this.hysteresisUpwardMultiplier = hysteresisUpwardMultiplier;
    this.proportionalSlashingMultiplier = proportionalSlashingMultiplier;
    this.minDepositAmount = minDepositAmount;
    this.maxEffectiveBalance = maxEffectiveBalance;
    this.ejectionBalance = ejectionBalance;
    this.effectiveBalanceIncrement = effectiveBalanceIncrement;
    this.genesisForkVersion = genesisForkVersion;
    this.blsWithdrawalPrefix = blsWithdrawalPrefix;
    this.genesisDelay = genesisDelay;
    this.secondsPerSlot = secondsPerSlot;
    this.minAttestationInclusionDelay = minAttestationInclusionDelay;
    this.slotsPerEpoch = slotsPerEpoch;
    this.minSeedLookahead = minSeedLookahead;
    this.maxSeedLookahead = maxSeedLookahead;
    this.minEpochsToInactivityPenalty = minEpochsToInactivityPenalty;
    this.epochsPerEth1VotingPeriod = epochsPerEth1VotingPeriod;
    this.slotsPerHistoricalRoot = slotsPerHistoricalRoot;
    this.minValidatorWithdrawabilityDelay = minValidatorWithdrawabilityDelay;
    this.shardCommitteePeriod = shardCommitteePeriod;
    this.epochsPerHistoricalVector = epochsPerHistoricalVector;
    this.epochsPerSlashingsVector = epochsPerSlashingsVector;
    this.historicalRootsLimit = historicalRootsLimit;
    this.validatorRegistryLimit = validatorRegistryLimit;
    this.baseRewardFactor = baseRewardFactor;
    this.whistleblowerRewardQuotient = whistleblowerRewardQuotient;
    this.proposerRewardQuotient = proposerRewardQuotient;
    this.inactivityPenaltyQuotient = inactivityPenaltyQuotient;
    this.minSlashingPenaltyQuotient = minSlashingPenaltyQuotient;
    this.maxProposerSlashings = maxProposerSlashings;
    this.maxAttesterSlashings = maxAttesterSlashings;
    this.maxAttestations = maxAttestations;
    this.maxDeposits = maxDeposits;
    this.maxVoluntaryExits = maxVoluntaryExits;
    this.domainBeaconProposer = domainBeaconProposer;
    this.domainBeaconAttester = domainBeaconAttester;
    this.domainRandao = domainRandao;
    this.domainDeposit = domainDeposit;
    this.domainVoluntaryExit = domainVoluntaryExit;
    this.domainSelectionProof = domainSelectionProof;
    this.domainAggregateAndProof = domainAggregateAndProof;
    this.targetAggregatorsPerCommittee = targetAggregatorsPerCommittee;
    this.secondsPerEth1Block = secondsPerEth1Block;
    this.randomSubnetsPerValidator = randomSubnetsPerValidator;
    this.epochsPerRandomSubnetSubscription = epochsPerRandomSubnetSubscription;
    this.safeSlotsToUpdateJustified = safeSlotsToUpdateJustified;
    this.depositChainId = depositChainId;
    this.depositNetworkId = depositNetworkId;
    this.depositContractAddress = depositContractAddress;
  }

  public static BeaconChainConstantsBuilder builder() {
    return new BeaconChainConstantsBuilder();
  }

  public long getGenesisSlot() {
    return genesisSlot;
  }

  public long getGenesisEpoch() {
    return genesisEpoch;
  }

  public UInt64 getFarFutureEpoch() {
    return farFutureEpoch;
  }

  public UInt64 getBaseRewardsPerEpoch() {
    return baseRewardsPerEpoch;
  }

  public int getDepositContractTreeDepth() {
    return depositContractTreeDepth;
  }

  public int getJustificationBitsLength() {
    return justificationBitsLength;
  }

  public UInt64 getEth1FollowDistance() {
    return eth1FollowDistance;
  }

  public int getMaxCommitteesPerSlot() {
    return maxCommitteesPerSlot;
  }

  public int getTargetCommitteeSize() {
    return targetCommitteeSize;
  }

  public int getMaxValidatorsPerCommittee() {
    return maxValidatorsPerCommittee;
  }

  public int getMinPerEpochChurnLimit() {
    return minPerEpochChurnLimit;
  }

  public int getChurnLimitQuotient() {
    return churnLimitQuotient;
  }

  public int getShuffleRoundCount() {
    return shuffleRoundCount;
  }

  public int getMinGenesisActiveValidatorCount() {
    return minGenesisActiveValidatorCount;
  }

  public UInt64 getMinGenesisTime() {
    return minGenesisTime;
  }

  public UInt64 getHysteresisQuotient() {
    return hysteresisQuotient;
  }

  public UInt64 getHysteresisDownwardMultiplier() {
    return hysteresisDownwardMultiplier;
  }

  public UInt64 getHysteresisUpwardMultiplier() {
    return hysteresisUpwardMultiplier;
  }

  public int getProportionalSlashingMultiplier() {
    return proportionalSlashingMultiplier;
  }

  public UInt64 getMinDepositAmount() {
    return minDepositAmount;
  }

  public UInt64 getMaxEffectiveBalance() {
    return maxEffectiveBalance;
  }

  public UInt64 getEjectionBalance() {
    return ejectionBalance;
  }

  public UInt64 getEffectiveBalanceIncrement() {
    return effectiveBalanceIncrement;
  }

  public Bytes4 getGenesisForkVersion() {
    return genesisForkVersion;
  }

  public Bytes getBlsWithdrawalPrefix() {
    return blsWithdrawalPrefix;
  }

  public UInt64 getGenesisDelay() {
    return genesisDelay;
  }

  public int getSecondsPerSlot() {
    return secondsPerSlot;
  }

  public int getMinAttestationInclusionDelay() {
    return minAttestationInclusionDelay;
  }

  public int getSlotsPerEpoch() {
    return slotsPerEpoch;
  }

  public int getMinSeedLookahead() {
    return minSeedLookahead;
  }

  public int getMaxSeedLookahead() {
    return maxSeedLookahead;
  }

  public UInt64 getMinEpochsToInactivityPenalty() {
    return minEpochsToInactivityPenalty;
  }

  public int getEpochsPerEth1VotingPeriod() {
    return epochsPerEth1VotingPeriod;
  }

  public int getSlotsPerHistoricalRoot() {
    return slotsPerHistoricalRoot;
  }

  public int getMinValidatorWithdrawabilityDelay() {
    return minValidatorWithdrawabilityDelay;
  }

  public UInt64 getShardCommitteePeriod() {
    return shardCommitteePeriod;
  }

  public int getEpochsPerHistoricalVector() {
    return epochsPerHistoricalVector;
  }

  public int getEpochsPerSlashingsVector() {
    return epochsPerSlashingsVector;
  }

  public int getHistoricalRootsLimit() {
    return historicalRootsLimit;
  }

  public long getValidatorRegistryLimit() {
    return validatorRegistryLimit;
  }

  public int getBaseRewardFactor() {
    return baseRewardFactor;
  }

  public int getWhistleblowerRewardQuotient() {
    return whistleblowerRewardQuotient;
  }

  public UInt64 getProposerRewardQuotient() {
    return proposerRewardQuotient;
  }

  public UInt64 getInactivityPenaltyQuotient() {
    return inactivityPenaltyQuotient;
  }

  public int getMinSlashingPenaltyQuotient() {
    return minSlashingPenaltyQuotient;
  }

  public int getMaxProposerSlashings() {
    return maxProposerSlashings;
  }

  public int getMaxAttesterSlashings() {
    return maxAttesterSlashings;
  }

  public int getMaxAttestations() {
    return maxAttestations;
  }

  public int getMaxDeposits() {
    return maxDeposits;
  }

  public int getMaxVoluntaryExits() {
    return maxVoluntaryExits;
  }

  public Bytes4 getDomainBeaconProposer() {
    return domainBeaconProposer;
  }

  public Bytes4 getDomainBeaconAttester() {
    return domainBeaconAttester;
  }

  public Bytes4 getDomainRandao() {
    return domainRandao;
  }

  public Bytes4 getDomainDeposit() {
    return domainDeposit;
  }

  public Bytes4 getDomainVoluntaryExit() {
    return domainVoluntaryExit;
  }

  public Bytes4 getDomainSelectionProof() {
    return domainSelectionProof;
  }

  public Bytes4 getDomainAggregateAndProof() {
    return domainAggregateAndProof;
  }

  public int getTargetAggregatorsPerCommittee() {
    return targetAggregatorsPerCommittee;
  }

  public UInt64 getSecondsPerEth1Block() {
    return secondsPerEth1Block;
  }

  public int getRandomSubnetsPerValidator() {
    return randomSubnetsPerValidator;
  }

  public int getEpochsPerRandomSubnetSubscription() {
    return epochsPerRandomSubnetSubscription;
  }

  public int getSafeSlotsToUpdateJustified() {
    return safeSlotsToUpdateJustified;
  }

  public int getDepositChainId() {
    return depositChainId;
  }

  public int getDepositNetworkId() {
    return depositNetworkId;
  }

  public Bytes getDepositContractAddress() {
    return depositContractAddress;
  }
}
