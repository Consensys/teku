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

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class BeaconChainConstantsBuilder {
  // Misc
  private UInt64 eth1FollowDistance = UInt64.valueOf(1024);
  private int maxCommitteesPerSlot;
  private int targetCommitteeSize;
  private int maxValidatorsPerCommittee;
  private int minPerEpochChurnLimit;
  private int churnLimitQuotient;
  private int shuffleRoundCount;
  private int minGenesisActiveValidatorCount;
  private UInt64 minGenesisTime;
  private UInt64 hysteresisQuotient;
  private UInt64 hysteresisDownwardMultiplier;
  private UInt64 hysteresisUpwardMultiplier;
  private int proportionalSlashingMultiplier;

  // Gwei values
  private UInt64 minDepositAmount;
  private UInt64 maxEffectiveBalance;
  private UInt64 ejectionBalance;
  private UInt64 effectiveBalanceIncrement;

  // Initial values
  private Bytes4 genesisForkVersion = Bytes4.fromHexString("0x00000000");
  private Bytes blsWithdrawalPrefix;

  // Time parameters
  private UInt64 genesisDelay;
  private int secondsPerSlot = 12;
  private int minAttestationInclusionDelay;
  private int slotsPerEpoch;
  private int minSeedLookahead;
  private int maxSeedLookahead;
  private UInt64 minEpochsToInactivityPenalty;
  private int epochsPerEth1VotingPeriod;
  private int slotsPerHistoricalRoot;
  private int minValidatorWithdrawabilityDelay;
  private UInt64 shardCommitteePeriod;

  // State list lengths
  private int epochsPerHistoricalVector;
  private int epochsPerSlashingsVector;
  private int historicalRootsLimit;
  private long validatorRegistryLimit;

  // Reward and penalty quotients
  private int baseRewardFactor;
  private int whistleblowerRewardQuotient;
  private UInt64 proposerRewardQuotient;
  private UInt64 inactivityPenaltyQuotient;
  private int minSlashingPenaltyQuotient;

  // Max transactions per block
  private int maxProposerSlashings;
  private int maxAttesterSlashings;
  private int maxAttestations;
  private int maxDeposits;
  private int maxVoluntaryExits = 16;

  // Signature domains
  private Bytes4 domainBeaconProposer = new Bytes4(Bytes.fromHexString("0x00000000"));
  private Bytes4 domainBeaconAttester = new Bytes4(Bytes.fromHexString("0x01000000"));
  private Bytes4 domainRandao = new Bytes4(Bytes.fromHexString("0x02000000"));
  private Bytes4 domainDeposit = new Bytes4(Bytes.fromHexString("0x03000000"));
  private Bytes4 domainVoluntaryExit = new Bytes4(Bytes.fromHexString("0x04000000"));
  private Bytes4 domainSelectionProof;
  private Bytes4 domainAggregateAndProof;

  // Validator
  private int targetAggregatorsPerCommittee = 16;
  private UInt64 secondsPerEth1Block = UInt64.valueOf(14L);
  private int randomSubnetsPerValidator = 1;
  private int epochsPerRandomSubnetSubscription = 256;

  // Fork Choice
  private int safeSlotsToUpdateJustified = 8;

  // Deposit Contract
  private int depositChainId;
  private int depositNetworkId;
  private Bytes depositContractAddress;

  public BeaconChainConstants build() {
    return new BeaconChainConstants(
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
        blsWithdrawalPrefix,
        genesisDelay,
        secondsPerSlot,
        minAttestationInclusionDelay,
        slotsPerEpoch,
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
        domainBeaconProposer,
        domainBeaconAttester,
        domainRandao,
        domainDeposit,
        domainVoluntaryExit,
        domainSelectionProof,
        domainAggregateAndProof,
        targetAggregatorsPerCommittee,
        secondsPerEth1Block,
        randomSubnetsPerValidator,
        epochsPerRandomSubnetSubscription,
        safeSlotsToUpdateJustified,
        depositChainId,
        depositNetworkId,
        depositContractAddress);
  }

  public BeaconChainConstantsBuilder eth1FollowDistance(final UInt64 eth1FollowDistance) {
    checkNotNull(eth1FollowDistance);
    this.eth1FollowDistance = eth1FollowDistance;
    return this;
  }

  public BeaconChainConstantsBuilder maxCommitteesPerSlot(final int maxCommitteesPerSlot) {
    checkNotNull(maxCommitteesPerSlot);
    this.maxCommitteesPerSlot = maxCommitteesPerSlot;
    return this;
  }

  public BeaconChainConstantsBuilder targetCommitteeSize(final int targetCommitteeSize) {
    checkNotNull(targetCommitteeSize);
    this.targetCommitteeSize = targetCommitteeSize;
    return this;
  }

  public BeaconChainConstantsBuilder maxValidatorsPerCommittee(
      final int maxValidatorsPerCommittee) {
    checkNotNull(maxValidatorsPerCommittee);
    this.maxValidatorsPerCommittee = maxValidatorsPerCommittee;
    return this;
  }

  public BeaconChainConstantsBuilder minPerEpochChurnLimit(final int minPerEpochChurnLimit) {
    checkNotNull(minPerEpochChurnLimit);
    this.minPerEpochChurnLimit = minPerEpochChurnLimit;
    return this;
  }

  public BeaconChainConstantsBuilder churnLimitQuotient(final int churnLimitQuotient) {
    checkNotNull(churnLimitQuotient);
    this.churnLimitQuotient = churnLimitQuotient;
    return this;
  }

  public BeaconChainConstantsBuilder shuffleRoundCount(final int shuffleRoundCount) {
    checkNotNull(shuffleRoundCount);
    this.shuffleRoundCount = shuffleRoundCount;
    return this;
  }

  public BeaconChainConstantsBuilder minGenesisActiveValidatorCount(
      final int minGenesisActiveValidatorCount) {
    checkNotNull(minGenesisActiveValidatorCount);
    this.minGenesisActiveValidatorCount = minGenesisActiveValidatorCount;
    return this;
  }

  public BeaconChainConstantsBuilder minGenesisTime(final UInt64 minGenesisTime) {
    checkNotNull(minGenesisTime);
    this.minGenesisTime = minGenesisTime;
    return this;
  }

  public BeaconChainConstantsBuilder hysteresisQuotient(final UInt64 hysteresisQuotient) {
    checkNotNull(hysteresisQuotient);
    this.hysteresisQuotient = hysteresisQuotient;
    return this;
  }

  public BeaconChainConstantsBuilder hysteresisDownwardMultiplier(
      final UInt64 hysteresisDownwardMultiplier) {
    checkNotNull(hysteresisDownwardMultiplier);
    this.hysteresisDownwardMultiplier = hysteresisDownwardMultiplier;
    return this;
  }

  public BeaconChainConstantsBuilder hysteresisUpwardMultiplier(
      final UInt64 hysteresisUpwardMultiplier) {
    checkNotNull(hysteresisUpwardMultiplier);
    this.hysteresisUpwardMultiplier = hysteresisUpwardMultiplier;
    return this;
  }

  public BeaconChainConstantsBuilder proportionalSlashingMultiplier(
      final int proportionalSlashingMultiplier) {
    checkNotNull(proportionalSlashingMultiplier);
    this.proportionalSlashingMultiplier = proportionalSlashingMultiplier;
    return this;
  }

  public BeaconChainConstantsBuilder minDepositAmount(final UInt64 minDepositAmount) {
    checkNotNull(minDepositAmount);
    this.minDepositAmount = minDepositAmount;
    return this;
  }

  public BeaconChainConstantsBuilder maxEffectiveBalance(final UInt64 maxEffectiveBalance) {
    checkNotNull(maxEffectiveBalance);
    this.maxEffectiveBalance = maxEffectiveBalance;
    return this;
  }

  public BeaconChainConstantsBuilder ejectionBalance(final UInt64 ejectionBalance) {
    checkNotNull(ejectionBalance);
    this.ejectionBalance = ejectionBalance;
    return this;
  }

  public BeaconChainConstantsBuilder effectiveBalanceIncrement(
      final UInt64 effectiveBalanceIncrement) {
    checkNotNull(effectiveBalanceIncrement);
    this.effectiveBalanceIncrement = effectiveBalanceIncrement;
    return this;
  }

  public BeaconChainConstantsBuilder genesisForkVersion(final Bytes4 genesisForkVersion) {
    checkNotNull(genesisForkVersion);
    this.genesisForkVersion = genesisForkVersion;
    return this;
  }

  public BeaconChainConstantsBuilder blsWithdrawalPrefix(final Bytes blsWithdrawalPrefix) {
    checkNotNull(blsWithdrawalPrefix);
    this.blsWithdrawalPrefix = blsWithdrawalPrefix;
    return this;
  }

  public BeaconChainConstantsBuilder genesisDelay(final UInt64 genesisDelay) {
    checkNotNull(genesisDelay);
    this.genesisDelay = genesisDelay;
    return this;
  }

  public BeaconChainConstantsBuilder secondsPerSlot(final int secondsPerSlot) {
    checkNotNull(secondsPerSlot);
    this.secondsPerSlot = secondsPerSlot;
    return this;
  }

  public BeaconChainConstantsBuilder minAttestationInclusionDelay(
      final int minAttestationInclusionDelay) {
    checkNotNull(minAttestationInclusionDelay);
    this.minAttestationInclusionDelay = minAttestationInclusionDelay;
    return this;
  }

  public BeaconChainConstantsBuilder slotsPerEpoch(final int slotsPerEpoch) {
    checkNotNull(slotsPerEpoch);
    this.slotsPerEpoch = slotsPerEpoch;
    return this;
  }

  public BeaconChainConstantsBuilder minSeedLookahead(final int minSeedLookahead) {
    checkNotNull(minSeedLookahead);
    this.minSeedLookahead = minSeedLookahead;
    return this;
  }

  public BeaconChainConstantsBuilder maxSeedLookahead(final int maxSeedLookahead) {
    checkNotNull(maxSeedLookahead);
    this.maxSeedLookahead = maxSeedLookahead;
    return this;
  }

  public BeaconChainConstantsBuilder minEpochsToInactivityPenalty(
      final UInt64 minEpochsToInactivityPenalty) {
    checkNotNull(minEpochsToInactivityPenalty);
    this.minEpochsToInactivityPenalty = minEpochsToInactivityPenalty;
    return this;
  }

  public BeaconChainConstantsBuilder epochsPerEth1VotingPeriod(
      final int epochsPerEth1VotingPeriod) {
    checkNotNull(epochsPerEth1VotingPeriod);
    this.epochsPerEth1VotingPeriod = epochsPerEth1VotingPeriod;
    return this;
  }

  public BeaconChainConstantsBuilder slotsPerHistoricalRoot(final int slotsPerHistoricalRoot) {
    checkNotNull(slotsPerHistoricalRoot);
    this.slotsPerHistoricalRoot = slotsPerHistoricalRoot;
    return this;
  }

  public BeaconChainConstantsBuilder minValidatorWithdrawabilityDelay(
      final int minValidatorWithdrawabilityDelay) {
    checkNotNull(minValidatorWithdrawabilityDelay);
    this.minValidatorWithdrawabilityDelay = minValidatorWithdrawabilityDelay;
    return this;
  }

  public BeaconChainConstantsBuilder shardCommitteePeriod(final UInt64 shardCommitteePeriod) {
    checkNotNull(shardCommitteePeriod);
    this.shardCommitteePeriod = shardCommitteePeriod;
    return this;
  }

  public BeaconChainConstantsBuilder epochsPerHistoricalVector(
      final int epochsPerHistoricalVector) {
    checkNotNull(epochsPerHistoricalVector);
    this.epochsPerHistoricalVector = epochsPerHistoricalVector;
    return this;
  }

  public BeaconChainConstantsBuilder epochsPerSlashingsVector(final int epochsPerSlashingsVector) {
    checkNotNull(epochsPerSlashingsVector);
    this.epochsPerSlashingsVector = epochsPerSlashingsVector;
    return this;
  }

  public BeaconChainConstantsBuilder historicalRootsLimit(final int historicalRootsLimit) {
    checkNotNull(historicalRootsLimit);
    this.historicalRootsLimit = historicalRootsLimit;
    return this;
  }

  public BeaconChainConstantsBuilder validatorRegistryLimit(final long validatorRegistryLimit) {
    checkNotNull(validatorRegistryLimit);
    this.validatorRegistryLimit = validatorRegistryLimit;
    return this;
  }

  public BeaconChainConstantsBuilder baseRewardFactor(final int baseRewardFactor) {
    checkNotNull(baseRewardFactor);
    this.baseRewardFactor = baseRewardFactor;
    return this;
  }

  public BeaconChainConstantsBuilder whistleblowerRewardQuotient(
      final int whistleblowerRewardQuotient) {
    checkNotNull(whistleblowerRewardQuotient);
    this.whistleblowerRewardQuotient = whistleblowerRewardQuotient;
    return this;
  }

  public BeaconChainConstantsBuilder proposerRewardQuotient(final UInt64 proposerRewardQuotient) {
    checkNotNull(proposerRewardQuotient);
    this.proposerRewardQuotient = proposerRewardQuotient;
    return this;
  }

  public BeaconChainConstantsBuilder inactivityPenaltyQuotient(
      final UInt64 inactivityPenaltyQuotient) {
    checkNotNull(inactivityPenaltyQuotient);
    this.inactivityPenaltyQuotient = inactivityPenaltyQuotient;
    return this;
  }

  public BeaconChainConstantsBuilder minSlashingPenaltyQuotient(
      final int minSlashingPenaltyQuotient) {
    checkNotNull(minSlashingPenaltyQuotient);
    this.minSlashingPenaltyQuotient = minSlashingPenaltyQuotient;
    return this;
  }

  public BeaconChainConstantsBuilder maxProposerSlashings(final int maxProposerSlashings) {
    checkNotNull(maxProposerSlashings);
    this.maxProposerSlashings = maxProposerSlashings;
    return this;
  }

  public BeaconChainConstantsBuilder maxAttesterSlashings(final int maxAttesterSlashings) {
    checkNotNull(maxAttesterSlashings);
    this.maxAttesterSlashings = maxAttesterSlashings;
    return this;
  }

  public BeaconChainConstantsBuilder maxAttestations(final int maxAttestations) {
    checkNotNull(maxAttestations);
    this.maxAttestations = maxAttestations;
    return this;
  }

  public BeaconChainConstantsBuilder maxDeposits(final int maxDeposits) {
    checkNotNull(maxDeposits);
    this.maxDeposits = maxDeposits;
    return this;
  }

  public BeaconChainConstantsBuilder maxVoluntaryExits(final int maxVoluntaryExits) {
    checkNotNull(maxVoluntaryExits);
    this.maxVoluntaryExits = maxVoluntaryExits;
    return this;
  }

  public BeaconChainConstantsBuilder domainBeaconProposer(final Bytes4 domainBeaconProposer) {
    checkNotNull(domainBeaconProposer);
    this.domainBeaconProposer = domainBeaconProposer;
    return this;
  }

  public BeaconChainConstantsBuilder domainBeaconAttester(final Bytes4 domainBeaconAttester) {
    checkNotNull(domainBeaconAttester);
    this.domainBeaconAttester = domainBeaconAttester;
    return this;
  }

  public BeaconChainConstantsBuilder domainRandao(final Bytes4 domainRandao) {
    checkNotNull(domainRandao);
    this.domainRandao = domainRandao;
    return this;
  }

  public BeaconChainConstantsBuilder domainDeposit(final Bytes4 domainDeposit) {
    checkNotNull(domainDeposit);
    this.domainDeposit = domainDeposit;
    return this;
  }

  public BeaconChainConstantsBuilder domainVoluntaryExit(final Bytes4 domainVoluntaryExit) {
    checkNotNull(domainVoluntaryExit);
    this.domainVoluntaryExit = domainVoluntaryExit;
    return this;
  }

  public BeaconChainConstantsBuilder domainSelectionProof(final Bytes4 domainSelectionProof) {
    checkNotNull(domainSelectionProof);
    this.domainSelectionProof = domainSelectionProof;
    return this;
  }

  public BeaconChainConstantsBuilder domainAggregateAndProof(final Bytes4 domainAggregateAndProof) {
    checkNotNull(domainAggregateAndProof);
    this.domainAggregateAndProof = domainAggregateAndProof;
    return this;
  }

  public BeaconChainConstantsBuilder targetAggregatorsPerCommittee(
      final int targetAggregatorsPerCommittee) {
    checkNotNull(targetAggregatorsPerCommittee);
    this.targetAggregatorsPerCommittee = targetAggregatorsPerCommittee;
    return this;
  }

  public BeaconChainConstantsBuilder secondsPerEth1Block(final UInt64 secondsPerEth1Block) {
    checkNotNull(secondsPerEth1Block);
    this.secondsPerEth1Block = secondsPerEth1Block;
    return this;
  }

  public BeaconChainConstantsBuilder randomSubnetsPerValidator(
      final int randomSubnetsPerValidator) {
    checkNotNull(randomSubnetsPerValidator);
    this.randomSubnetsPerValidator = randomSubnetsPerValidator;
    return this;
  }

  public BeaconChainConstantsBuilder epochsPerRandomSubnetSubscription(
      final int epochsPerRandomSubnetSubscription) {
    checkNotNull(epochsPerRandomSubnetSubscription);
    this.epochsPerRandomSubnetSubscription = epochsPerRandomSubnetSubscription;
    return this;
  }

  public BeaconChainConstantsBuilder safeSlotsToUpdateJustified(
      final int safeSlotsToUpdateJustified) {
    checkNotNull(safeSlotsToUpdateJustified);
    this.safeSlotsToUpdateJustified = safeSlotsToUpdateJustified;
    return this;
  }

  public BeaconChainConstantsBuilder depositChainId(final int depositChainId) {
    checkNotNull(depositChainId);
    this.depositChainId = depositChainId;
    return this;
  }

  public BeaconChainConstantsBuilder depositNetworkId(final int depositNetworkId) {
    checkNotNull(depositNetworkId);
    this.depositNetworkId = depositNetworkId;
    return this;
  }

  public BeaconChainConstantsBuilder depositContractAddress(final Bytes depositContractAddress) {
    checkNotNull(depositContractAddress);
    this.depositContractAddress = depositContractAddress;
    return this;
  }
}
