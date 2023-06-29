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
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.WithdrawalPrefixes;
import tech.pegasys.teku.spec.logic.common.helpers.MathHelpers;

public class SpecConfigPhase0 implements SpecConfig {
  private final Map<String, Object> rawConfig;

  // Constants
  private static final UInt64 BASE_REWARDS_PER_EPOCH = UInt64.valueOf(4);
  private static final int DEPOSIT_CONTRACT_TREE_DEPTH = 32;
  private static final int JUSTIFICATION_BITS_LENGTH = 4;

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

  // Time parameters
  private final UInt64 genesisDelay;
  private final int secondsPerSlot;
  private final int minAttestationInclusionDelay;
  private final int slotsPerEpoch;
  private final long squareRootSlotsPerEpoch;
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

  // Validator
  private final int secondsPerEth1Block;

  // Fork Choice
  private final int safeSlotsToUpdateJustified;
  private final int proposerScoreBoost;

  // Deposit Contract
  private final long depositChainId;
  private final long depositNetworkId;
  private final Eth1Address depositContractAddress;

  // Networking
  private final int gossipMaxSize;
  private final int maxChunkSize;
  private final int maxRequestBlocks;
  private final int epochsPerSubnetSubscription;
  private final int ttfbTimeout;
  private final int respTimeout;
  private final int attestationPropagationSlotRange;
  private final int maximumGossipClockDisparity;
  private final Bytes4 messageDomainInvalidSnappy;
  private final Bytes4 messageDomainValidSnappy;
  private final int subnetsPerNode;
  private final int attestationSubnetCount;
  private final int attestationSubnetExtraBits;
  private final int attestationSubnetPrefixBits;

  public SpecConfigPhase0(
      final Map<String, Object> rawConfig,
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
      final int secondsPerEth1Block,
      final int safeSlotsToUpdateJustified,
      final int proposerScoreBoost,
      final long depositChainId,
      final long depositNetworkId,
      final Eth1Address depositContractAddress,
      final int gossipMaxSize,
      final int maxChunkSize,
      final int maxRequestBlocks,
      final int epochsPerSubnetSubscription,
      final int ttfbTimeout,
      final int respTimeout,
      final int attestationPropagationSlotRange,
      final int maximumGossipClockDisparity,
      final Bytes4 messageDomainInvalidSnappy,
      final Bytes4 messageDomainValidSnappy,
      final int subnetsPerNode,
      final int attestationSubnetCount,
      final int attestationSubnetExtraBits,
      final int attestationSubnetPrefixBits) {
    this.rawConfig = rawConfig;
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
    this.secondsPerEth1Block = secondsPerEth1Block;
    this.safeSlotsToUpdateJustified = safeSlotsToUpdateJustified;
    this.proposerScoreBoost = proposerScoreBoost;
    this.depositChainId = depositChainId;
    this.depositNetworkId = depositNetworkId;
    this.depositContractAddress = depositContractAddress;
    this.squareRootSlotsPerEpoch = MathHelpers.integerSquareRoot(slotsPerEpoch);
    this.gossipMaxSize = gossipMaxSize;
    this.maxChunkSize = maxChunkSize;
    this.maxRequestBlocks = maxRequestBlocks;
    this.epochsPerSubnetSubscription = epochsPerSubnetSubscription;
    this.ttfbTimeout = ttfbTimeout;
    this.respTimeout = respTimeout;
    this.attestationPropagationSlotRange = attestationPropagationSlotRange;
    this.maximumGossipClockDisparity = maximumGossipClockDisparity;
    this.messageDomainInvalidSnappy = messageDomainInvalidSnappy;
    this.messageDomainValidSnappy = messageDomainValidSnappy;
    this.subnetsPerNode = subnetsPerNode;
    this.attestationSubnetCount = attestationSubnetCount;
    this.attestationSubnetExtraBits = attestationSubnetExtraBits;
    this.attestationSubnetPrefixBits = attestationSubnetPrefixBits;
  }

  @Override
  public Map<String, Object> getRawConfig() {
    return rawConfig;
  }

  @Override
  public UInt64 getBaseRewardsPerEpoch() {
    return BASE_REWARDS_PER_EPOCH;
  }

  @Override
  public int getDepositContractTreeDepth() {
    return DEPOSIT_CONTRACT_TREE_DEPTH;
  }

  @Override
  public int getJustificationBitsLength() {
    return JUSTIFICATION_BITS_LENGTH;
  }

  @Override
  public UInt64 getEth1FollowDistance() {
    return eth1FollowDistance;
  }

  @Override
  public int getMaxCommitteesPerSlot() {
    return maxCommitteesPerSlot;
  }

  @Override
  public int getTargetCommitteeSize() {
    return targetCommitteeSize;
  }

  @Override
  public int getMaxValidatorsPerCommittee() {
    return maxValidatorsPerCommittee;
  }

  @Override
  public int getMinPerEpochChurnLimit() {
    return minPerEpochChurnLimit;
  }

  @Override
  public int getChurnLimitQuotient() {
    return churnLimitQuotient;
  }

  @Override
  public int getShuffleRoundCount() {
    return shuffleRoundCount;
  }

  @Override
  public int getMinGenesisActiveValidatorCount() {
    return minGenesisActiveValidatorCount;
  }

  @Override
  public UInt64 getMinGenesisTime() {
    return minGenesisTime;
  }

  @Override
  public UInt64 getHysteresisQuotient() {
    return hysteresisQuotient;
  }

  @Override
  public UInt64 getHysteresisDownwardMultiplier() {
    return hysteresisDownwardMultiplier;
  }

  @Override
  public UInt64 getHysteresisUpwardMultiplier() {
    return hysteresisUpwardMultiplier;
  }

  @Override
  public int getProportionalSlashingMultiplier() {
    return proportionalSlashingMultiplier;
  }

  @Override
  public UInt64 getMinDepositAmount() {
    return minDepositAmount;
  }

  @Override
  public UInt64 getMaxEffectiveBalance() {
    return maxEffectiveBalance;
  }

  @Override
  public UInt64 getEjectionBalance() {
    return ejectionBalance;
  }

  @Override
  public UInt64 getEffectiveBalanceIncrement() {
    return effectiveBalanceIncrement;
  }

  @Override
  public Bytes4 getGenesisForkVersion() {
    return genesisForkVersion;
  }

  @Override
  public Bytes getBlsWithdrawalPrefix() {
    return WithdrawalPrefixes.BLS_WITHDRAWAL_PREFIX;
  }

  @Override
  public UInt64 getGenesisDelay() {
    return genesisDelay;
  }

  @Override
  public int getSecondsPerSlot() {
    return secondsPerSlot;
  }

  @Override
  public int getMinAttestationInclusionDelay() {
    return minAttestationInclusionDelay;
  }

  @Override
  public int getSlotsPerEpoch() {
    return slotsPerEpoch;
  }

  @Override
  public long getSquareRootSlotsPerEpoch() {
    return squareRootSlotsPerEpoch;
  }

  @Override
  public int getMinSeedLookahead() {
    return minSeedLookahead;
  }

  @Override
  public int getMaxSeedLookahead() {
    return maxSeedLookahead;
  }

  @Override
  public UInt64 getMinEpochsToInactivityPenalty() {
    return minEpochsToInactivityPenalty;
  }

  @Override
  public int getEpochsPerEth1VotingPeriod() {
    return epochsPerEth1VotingPeriod;
  }

  @Override
  public int getSlotsPerHistoricalRoot() {
    return slotsPerHistoricalRoot;
  }

  @Override
  public int getMinValidatorWithdrawabilityDelay() {
    return minValidatorWithdrawabilityDelay;
  }

  @Override
  public UInt64 getShardCommitteePeriod() {
    return shardCommitteePeriod;
  }

  @Override
  public int getEpochsPerHistoricalVector() {
    return epochsPerHistoricalVector;
  }

  @Override
  public int getEpochsPerSlashingsVector() {
    return epochsPerSlashingsVector;
  }

  @Override
  public int getHistoricalRootsLimit() {
    return historicalRootsLimit;
  }

  @Override
  public long getValidatorRegistryLimit() {
    return validatorRegistryLimit;
  }

  @Override
  public int getBaseRewardFactor() {
    return baseRewardFactor;
  }

  @Override
  public int getWhistleblowerRewardQuotient() {
    return whistleblowerRewardQuotient;
  }

  @Override
  public UInt64 getProposerRewardQuotient() {
    return proposerRewardQuotient;
  }

  @Override
  public UInt64 getInactivityPenaltyQuotient() {
    return inactivityPenaltyQuotient;
  }

  @Override
  public int getMinSlashingPenaltyQuotient() {
    return minSlashingPenaltyQuotient;
  }

  @Override
  public int getMaxProposerSlashings() {
    return maxProposerSlashings;
  }

  @Override
  public int getMaxAttesterSlashings() {
    return maxAttesterSlashings;
  }

  @Override
  public int getMaxAttestations() {
    return maxAttestations;
  }

  @Override
  public int getMaxDeposits() {
    return maxDeposits;
  }

  @Override
  public int getMaxVoluntaryExits() {
    return maxVoluntaryExits;
  }

  @Override
  public int getSecondsPerEth1Block() {
    return secondsPerEth1Block;
  }

  @Override
  public int getSafeSlotsToUpdateJustified() {
    return safeSlotsToUpdateJustified;
  }

  @Override
  public int getProposerScoreBoost() {
    return proposerScoreBoost;
  }

  @Override
  public long getDepositChainId() {
    return depositChainId;
  }

  @Override
  public long getDepositNetworkId() {
    return depositNetworkId;
  }

  @Override
  public Eth1Address getDepositContractAddress() {
    return depositContractAddress;
  }

  @Override
  public int getGossipMaxSize() {
    return gossipMaxSize;
  }

  @Override
  public int getMaxChunkSize() {
    return maxChunkSize;
  }

  @Override
  public int getMaxRequestBlocks() {
    return maxRequestBlocks;
  }

  @Override
  public int getEpochsPerSubnetSubscription() {
    return epochsPerSubnetSubscription;
  }

  @Override
  public int getTtfbTimeout() {
    return ttfbTimeout;
  }

  @Override
  public int getRespTimeout() {
    return respTimeout;
  }

  @Override
  public int getAttestationPropagationSlotRange() {
    return attestationPropagationSlotRange;
  }

  @Override
  public int getMaximumGossipClockDisparity() {
    return maximumGossipClockDisparity;
  }

  @Override
  public Bytes4 getMessageDomainInvalidSnappy() {
    return messageDomainInvalidSnappy;
  }

  @Override
  public Bytes4 getMessageDomainValidSnappy() {
    return messageDomainValidSnappy;
  }

  @Override
  public int getSubnetsPerNode() {
    return subnetsPerNode;
  }

  @Override
  public int getAttestationSubnetCount() {
    return attestationSubnetCount;
  }

  @Override
  public int getAttestationSubnetExtraBits() {
    return attestationSubnetExtraBits;
  }

  @Override
  public int getAttestationSubnetPrefixBits() {
    return attestationSubnetPrefixBits;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SpecConfigPhase0 that = (SpecConfigPhase0) o;
    return maxCommitteesPerSlot == that.maxCommitteesPerSlot
        && targetCommitteeSize == that.targetCommitteeSize
        && maxValidatorsPerCommittee == that.maxValidatorsPerCommittee
        && minPerEpochChurnLimit == that.minPerEpochChurnLimit
        && churnLimitQuotient == that.churnLimitQuotient
        && shuffleRoundCount == that.shuffleRoundCount
        && minGenesisActiveValidatorCount == that.minGenesisActiveValidatorCount
        && proportionalSlashingMultiplier == that.proportionalSlashingMultiplier
        && secondsPerSlot == that.secondsPerSlot
        && minAttestationInclusionDelay == that.minAttestationInclusionDelay
        && slotsPerEpoch == that.slotsPerEpoch
        && squareRootSlotsPerEpoch == that.squareRootSlotsPerEpoch
        && minSeedLookahead == that.minSeedLookahead
        && maxSeedLookahead == that.maxSeedLookahead
        && epochsPerEth1VotingPeriod == that.epochsPerEth1VotingPeriod
        && slotsPerHistoricalRoot == that.slotsPerHistoricalRoot
        && minValidatorWithdrawabilityDelay == that.minValidatorWithdrawabilityDelay
        && epochsPerHistoricalVector == that.epochsPerHistoricalVector
        && epochsPerSlashingsVector == that.epochsPerSlashingsVector
        && historicalRootsLimit == that.historicalRootsLimit
        && validatorRegistryLimit == that.validatorRegistryLimit
        && baseRewardFactor == that.baseRewardFactor
        && whistleblowerRewardQuotient == that.whistleblowerRewardQuotient
        && minSlashingPenaltyQuotient == that.minSlashingPenaltyQuotient
        && maxProposerSlashings == that.maxProposerSlashings
        && maxAttesterSlashings == that.maxAttesterSlashings
        && maxAttestations == that.maxAttestations
        && maxDeposits == that.maxDeposits
        && maxVoluntaryExits == that.maxVoluntaryExits
        && secondsPerEth1Block == that.secondsPerEth1Block
        && safeSlotsToUpdateJustified == that.safeSlotsToUpdateJustified
        && proposerScoreBoost == that.proposerScoreBoost
        && depositChainId == that.depositChainId
        && depositNetworkId == that.depositNetworkId
        && gossipMaxSize == that.gossipMaxSize
        && maxChunkSize == that.maxChunkSize
        && maxRequestBlocks == that.maxRequestBlocks
        && epochsPerSubnetSubscription == that.epochsPerSubnetSubscription
        && subnetsPerNode == that.subnetsPerNode
        && attestationSubnetCount == that.attestationSubnetCount
        && attestationSubnetExtraBits == that.attestationSubnetExtraBits
        && attestationSubnetPrefixBits == that.attestationSubnetPrefixBits
        && ttfbTimeout == that.ttfbTimeout
        && respTimeout == that.respTimeout
        && attestationPropagationSlotRange == that.attestationPropagationSlotRange
        && maximumGossipClockDisparity == that.maximumGossipClockDisparity
        && Objects.equals(eth1FollowDistance, that.eth1FollowDistance)
        && Objects.equals(minGenesisTime, that.minGenesisTime)
        && Objects.equals(hysteresisQuotient, that.hysteresisQuotient)
        && Objects.equals(hysteresisDownwardMultiplier, that.hysteresisDownwardMultiplier)
        && Objects.equals(hysteresisUpwardMultiplier, that.hysteresisUpwardMultiplier)
        && Objects.equals(minDepositAmount, that.minDepositAmount)
        && Objects.equals(maxEffectiveBalance, that.maxEffectiveBalance)
        && Objects.equals(ejectionBalance, that.ejectionBalance)
        && Objects.equals(effectiveBalanceIncrement, that.effectiveBalanceIncrement)
        && Objects.equals(genesisForkVersion, that.genesisForkVersion)
        && Objects.equals(genesisDelay, that.genesisDelay)
        && Objects.equals(minEpochsToInactivityPenalty, that.minEpochsToInactivityPenalty)
        && Objects.equals(shardCommitteePeriod, that.shardCommitteePeriod)
        && Objects.equals(proposerRewardQuotient, that.proposerRewardQuotient)
        && Objects.equals(inactivityPenaltyQuotient, that.inactivityPenaltyQuotient)
        && Objects.equals(depositContractAddress, that.depositContractAddress)
        && Objects.equals(messageDomainInvalidSnappy, that.messageDomainInvalidSnappy)
        && Objects.equals(messageDomainValidSnappy, that.messageDomainValidSnappy);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        eth1FollowDistance,
        maxCommitteesPerSlot,
        targetCommitteeSize,
        maxValidatorsPerCommittee,
        minPerEpochChurnLimit,
        churnLimitQuotient,
        shuffleRoundCount,
        minGenesisActiveValidatorCount,
        minGenesisTime,
        hysteresisQuotient,
        hysteresisDownwardMultiplier,
        hysteresisUpwardMultiplier,
        proportionalSlashingMultiplier,
        minDepositAmount,
        maxEffectiveBalance,
        ejectionBalance,
        effectiveBalanceIncrement,
        genesisForkVersion,
        genesisDelay,
        secondsPerSlot,
        minAttestationInclusionDelay,
        slotsPerEpoch,
        squareRootSlotsPerEpoch,
        minSeedLookahead,
        maxSeedLookahead,
        minEpochsToInactivityPenalty,
        epochsPerEth1VotingPeriod,
        slotsPerHistoricalRoot,
        minValidatorWithdrawabilityDelay,
        shardCommitteePeriod,
        epochsPerHistoricalVector,
        epochsPerSlashingsVector,
        historicalRootsLimit,
        validatorRegistryLimit,
        baseRewardFactor,
        whistleblowerRewardQuotient,
        proposerRewardQuotient,
        inactivityPenaltyQuotient,
        minSlashingPenaltyQuotient,
        maxProposerSlashings,
        maxAttesterSlashings,
        maxAttestations,
        maxDeposits,
        maxVoluntaryExits,
        secondsPerEth1Block,
        safeSlotsToUpdateJustified,
        proposerScoreBoost,
        depositChainId,
        depositNetworkId,
        depositContractAddress,
        gossipMaxSize,
        maxChunkSize,
        maxRequestBlocks,
        epochsPerSubnetSubscription,
        ttfbTimeout,
        respTimeout,
        attestationPropagationSlotRange,
        maximumGossipClockDisparity,
        messageDomainInvalidSnappy,
        messageDomainValidSnappy,
        subnetsPerNode,
        attestationSubnetCount,
        attestationSubnetExtraBits,
        attestationSubnetPrefixBits);
  }
}
