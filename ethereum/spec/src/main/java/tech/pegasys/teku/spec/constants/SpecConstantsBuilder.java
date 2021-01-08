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

package tech.pegasys.teku.spec.constants;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class SpecConstantsBuilder {
  private final Map<String, Object> rawConstants = new HashMap<>();
  private String configName = "Custom (unknown)";

  // Misc
  private UInt64 eth1FollowDistance = UInt64.valueOf(1024);
  private Integer maxCommitteesPerSlot;
  private Integer targetCommitteeSize;
  private Integer maxValidatorsPerCommittee;
  private Integer minPerEpochChurnLimit;
  private Integer churnLimitQuotient;
  private Integer shuffleRoundCount;
  private Integer minGenesisActiveValidatorCount;
  private UInt64 minGenesisTime;
  private UInt64 hysteresisQuotient;
  private UInt64 hysteresisDownwardMultiplier;
  private UInt64 hysteresisUpwardMultiplier;
  private Integer proportionalSlashingMultiplier;

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
  private Integer secondsPerSlot = 12;
  private Integer minAttestationInclusionDelay;
  private Integer slotsPerEpoch;
  private Integer minSeedLookahead;
  private Integer maxSeedLookahead;
  private UInt64 minEpochsToInactivityPenalty;
  private Integer epochsPerEth1VotingPeriod;
  private Integer slotsPerHistoricalRoot;
  private Integer minValidatorWithdrawabilityDelay;
  private UInt64 shardCommitteePeriod;

  // State list lengths
  private Integer epochsPerHistoricalVector;
  private Integer epochsPerSlashingsVector;
  private Integer historicalRootsLimit;
  private Long validatorRegistryLimit;

  // Reward and penalty quotients
  private Integer baseRewardFactor;
  private Integer whistleblowerRewardQuotient;
  private UInt64 proposerRewardQuotient;
  private UInt64 inactivityPenaltyQuotient;
  private Integer minSlashingPenaltyQuotient;

  // Max transactions per block
  private Integer maxProposerSlashings;
  private Integer maxAttesterSlashings;
  private Integer maxAttestations;
  private Integer maxDeposits;
  private Integer maxVoluntaryExits = 16;

  // Signature domains
  private Bytes4 domainBeaconProposer = new Bytes4(Bytes.fromHexString("0x00000000"));
  private Bytes4 domainBeaconAttester = new Bytes4(Bytes.fromHexString("0x01000000"));
  private Bytes4 domainRandao = new Bytes4(Bytes.fromHexString("0x02000000"));
  private Bytes4 domainDeposit = new Bytes4(Bytes.fromHexString("0x03000000"));
  private Bytes4 domainVoluntaryExit = new Bytes4(Bytes.fromHexString("0x04000000"));
  private Bytes4 domainSelectionProof;
  private Bytes4 domainAggregateAndProof;

  // Validator
  private Integer targetAggregatorsPerCommittee = 16;
  private UInt64 secondsPerEth1Block = UInt64.valueOf(14L);
  private Integer randomSubnetsPerValidator = 1;
  private Integer epochsPerRandomSubnetSubscription = 256;

  // Fork Choice
  private Integer safeSlotsToUpdateJustified = 8;

  // Deposit Contract
  private Integer depositChainId;
  private Integer depositNetworkId;
  private Bytes depositContractAddress;

  public SpecConstants build() {
    validate();
    return new SpecConstants(
        rawConstants,
        configName,
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

  private void validate() {
    checkArgument(rawConstants.size() > 0, "Raw constants must be provided");
    validateConstant(configName);
    validateConstant(eth1FollowDistance);
    validateConstant(maxCommitteesPerSlot);
    validateConstant(targetCommitteeSize);
    validateConstant(maxValidatorsPerCommittee);
    validateConstant(minPerEpochChurnLimit);
    validateConstant(churnLimitQuotient);
    validateConstant(shuffleRoundCount);
    validateConstant(minGenesisActiveValidatorCount);
    validateConstant(minGenesisTime);
    validateConstant(hysteresisQuotient);
    validateConstant(hysteresisDownwardMultiplier);
    validateConstant(hysteresisUpwardMultiplier);
    validateConstant(proportionalSlashingMultiplier);
    validateConstant(minDepositAmount);
    validateConstant(maxEffectiveBalance);
    validateConstant(ejectionBalance);
    validateConstant(effectiveBalanceIncrement);
    validateConstant(genesisForkVersion);
    validateConstant(blsWithdrawalPrefix);
    validateConstant(genesisDelay);
    validateConstant(secondsPerSlot);
    validateConstant(minAttestationInclusionDelay);
    validateConstant(slotsPerEpoch);
    validateConstant(minSeedLookahead);
    validateConstant(maxSeedLookahead);
    validateConstant(minEpochsToInactivityPenalty);
    validateConstant(epochsPerEth1VotingPeriod);
    validateConstant(slotsPerHistoricalRoot);
    validateConstant(minValidatorWithdrawabilityDelay);
    validateConstant(shardCommitteePeriod);
    validateConstant(epochsPerHistoricalVector);
    validateConstant(epochsPerSlashingsVector);
    validateConstant(historicalRootsLimit);
    validateConstant(validatorRegistryLimit);
    validateConstant(baseRewardFactor);
    validateConstant(whistleblowerRewardQuotient);
    validateConstant(proposerRewardQuotient);
    validateConstant(inactivityPenaltyQuotient);
    validateConstant(minSlashingPenaltyQuotient);
    validateConstant(maxProposerSlashings);
    validateConstant(maxAttesterSlashings);
    validateConstant(maxAttestations);
    validateConstant(maxDeposits);
    validateConstant(maxVoluntaryExits);
    validateConstant(domainBeaconProposer);
    validateConstant(domainBeaconAttester);
    validateConstant(domainRandao);
    validateConstant(domainDeposit);
    validateConstant(domainVoluntaryExit);
    validateConstant(domainSelectionProof);
    validateConstant(domainAggregateAndProof);
    validateConstant(targetAggregatorsPerCommittee);
    validateConstant(secondsPerEth1Block);
    validateConstant(randomSubnetsPerValidator);
    validateConstant(epochsPerRandomSubnetSubscription);
    validateConstant(safeSlotsToUpdateJustified);
    validateConstant(depositChainId);
    validateConstant(depositNetworkId);
    validateConstant(depositContractAddress);
  }

  private void validateConstant(final String value) {
    checkNotNull(value);
  }

  private void validateConstant(final Bytes value) {
    checkNotNull(value);
  }

  private void validateConstant(final Bytes4 value) {
    checkNotNull(value);
  }

  private void validateConstant(final UInt64 value) {
    checkNotNull(value);
  }

  private void validateConstant(final Long value) {
    checkNotNull(value);
    checkArgument(value >= 0, "Long values must be positive");
  }

  private void validateConstant(final Integer value) {
    checkNotNull(value);
    checkArgument(value >= 0, "Integer values must be positive");
  }

  public SpecConstantsBuilder rawConstants(final Map<String, Object> rawConstants) {
    checkNotNull(rawConstants);
    this.rawConstants.putAll(rawConstants);
    return this;
  }

  public SpecConstantsBuilder configName(final String configName) {
    checkNotNull(configName);
    this.configName = configName;
    return this;
  }

  public SpecConstantsBuilder eth1FollowDistance(final UInt64 eth1FollowDistance) {
    checkNotNull(eth1FollowDistance);
    this.eth1FollowDistance = eth1FollowDistance;
    return this;
  }

  public SpecConstantsBuilder maxCommitteesPerSlot(final Integer maxCommitteesPerSlot) {
    checkNotNull(maxCommitteesPerSlot);
    this.maxCommitteesPerSlot = maxCommitteesPerSlot;
    return this;
  }

  public SpecConstantsBuilder targetCommitteeSize(final Integer targetCommitteeSize) {
    checkNotNull(targetCommitteeSize);
    this.targetCommitteeSize = targetCommitteeSize;
    return this;
  }

  public SpecConstantsBuilder maxValidatorsPerCommittee(final Integer maxValidatorsPerCommittee) {
    checkNotNull(maxValidatorsPerCommittee);
    this.maxValidatorsPerCommittee = maxValidatorsPerCommittee;
    return this;
  }

  public SpecConstantsBuilder minPerEpochChurnLimit(final Integer minPerEpochChurnLimit) {
    checkNotNull(minPerEpochChurnLimit);
    this.minPerEpochChurnLimit = minPerEpochChurnLimit;
    return this;
  }

  public SpecConstantsBuilder churnLimitQuotient(final Integer churnLimitQuotient) {
    checkNotNull(churnLimitQuotient);
    this.churnLimitQuotient = churnLimitQuotient;
    return this;
  }

  public SpecConstantsBuilder shuffleRoundCount(final Integer shuffleRoundCount) {
    checkNotNull(shuffleRoundCount);
    this.shuffleRoundCount = shuffleRoundCount;
    return this;
  }

  public SpecConstantsBuilder minGenesisActiveValidatorCount(
      final Integer minGenesisActiveValidatorCount) {
    checkNotNull(minGenesisActiveValidatorCount);
    this.minGenesisActiveValidatorCount = minGenesisActiveValidatorCount;
    return this;
  }

  public SpecConstantsBuilder minGenesisTime(final UInt64 minGenesisTime) {
    checkNotNull(minGenesisTime);
    this.minGenesisTime = minGenesisTime;
    return this;
  }

  public SpecConstantsBuilder hysteresisQuotient(final UInt64 hysteresisQuotient) {
    checkNotNull(hysteresisQuotient);
    this.hysteresisQuotient = hysteresisQuotient;
    return this;
  }

  public SpecConstantsBuilder hysteresisDownwardMultiplier(
      final UInt64 hysteresisDownwardMultiplier) {
    checkNotNull(hysteresisDownwardMultiplier);
    this.hysteresisDownwardMultiplier = hysteresisDownwardMultiplier;
    return this;
  }

  public SpecConstantsBuilder hysteresisUpwardMultiplier(final UInt64 hysteresisUpwardMultiplier) {
    checkNotNull(hysteresisUpwardMultiplier);
    this.hysteresisUpwardMultiplier = hysteresisUpwardMultiplier;
    return this;
  }

  public SpecConstantsBuilder proportionalSlashingMultiplier(
      final Integer proportionalSlashingMultiplier) {
    checkNotNull(proportionalSlashingMultiplier);
    this.proportionalSlashingMultiplier = proportionalSlashingMultiplier;
    return this;
  }

  public SpecConstantsBuilder minDepositAmount(final UInt64 minDepositAmount) {
    checkNotNull(minDepositAmount);
    this.minDepositAmount = minDepositAmount;
    return this;
  }

  public SpecConstantsBuilder maxEffectiveBalance(final UInt64 maxEffectiveBalance) {
    checkNotNull(maxEffectiveBalance);
    this.maxEffectiveBalance = maxEffectiveBalance;
    return this;
  }

  public SpecConstantsBuilder ejectionBalance(final UInt64 ejectionBalance) {
    checkNotNull(ejectionBalance);
    this.ejectionBalance = ejectionBalance;
    return this;
  }

  public SpecConstantsBuilder effectiveBalanceIncrement(final UInt64 effectiveBalanceIncrement) {
    checkNotNull(effectiveBalanceIncrement);
    this.effectiveBalanceIncrement = effectiveBalanceIncrement;
    return this;
  }

  public SpecConstantsBuilder genesisForkVersion(final Bytes4 genesisForkVersion) {
    checkNotNull(genesisForkVersion);
    this.genesisForkVersion = genesisForkVersion;
    return this;
  }

  public SpecConstantsBuilder blsWithdrawalPrefix(final Bytes blsWithdrawalPrefix) {
    checkNotNull(blsWithdrawalPrefix);
    this.blsWithdrawalPrefix = blsWithdrawalPrefix;
    return this;
  }

  public SpecConstantsBuilder genesisDelay(final UInt64 genesisDelay) {
    checkNotNull(genesisDelay);
    this.genesisDelay = genesisDelay;
    return this;
  }

  public SpecConstantsBuilder secondsPerSlot(final Integer secondsPerSlot) {
    checkNotNull(secondsPerSlot);
    this.secondsPerSlot = secondsPerSlot;
    return this;
  }

  public SpecConstantsBuilder minAttestationInclusionDelay(
      final Integer minAttestationInclusionDelay) {
    checkNotNull(minAttestationInclusionDelay);
    this.minAttestationInclusionDelay = minAttestationInclusionDelay;
    return this;
  }

  public SpecConstantsBuilder slotsPerEpoch(final Integer slotsPerEpoch) {
    checkNotNull(slotsPerEpoch);
    this.slotsPerEpoch = slotsPerEpoch;
    return this;
  }

  public SpecConstantsBuilder minSeedLookahead(final Integer minSeedLookahead) {
    checkNotNull(minSeedLookahead);
    this.minSeedLookahead = minSeedLookahead;
    return this;
  }

  public SpecConstantsBuilder maxSeedLookahead(final Integer maxSeedLookahead) {
    checkNotNull(maxSeedLookahead);
    this.maxSeedLookahead = maxSeedLookahead;
    return this;
  }

  public SpecConstantsBuilder minEpochsToInactivityPenalty(
      final UInt64 minEpochsToInactivityPenalty) {
    checkNotNull(minEpochsToInactivityPenalty);
    this.minEpochsToInactivityPenalty = minEpochsToInactivityPenalty;
    return this;
  }

  public SpecConstantsBuilder epochsPerEth1VotingPeriod(final Integer epochsPerEth1VotingPeriod) {
    checkNotNull(epochsPerEth1VotingPeriod);
    this.epochsPerEth1VotingPeriod = epochsPerEth1VotingPeriod;
    return this;
  }

  public SpecConstantsBuilder slotsPerHistoricalRoot(final Integer slotsPerHistoricalRoot) {
    checkNotNull(slotsPerHistoricalRoot);
    this.slotsPerHistoricalRoot = slotsPerHistoricalRoot;
    return this;
  }

  public SpecConstantsBuilder minValidatorWithdrawabilityDelay(
      final Integer minValidatorWithdrawabilityDelay) {
    checkNotNull(minValidatorWithdrawabilityDelay);
    this.minValidatorWithdrawabilityDelay = minValidatorWithdrawabilityDelay;
    return this;
  }

  public SpecConstantsBuilder shardCommitteePeriod(final UInt64 shardCommitteePeriod) {
    checkNotNull(shardCommitteePeriod);
    this.shardCommitteePeriod = shardCommitteePeriod;
    return this;
  }

  public SpecConstantsBuilder epochsPerHistoricalVector(final Integer epochsPerHistoricalVector) {
    checkNotNull(epochsPerHistoricalVector);
    this.epochsPerHistoricalVector = epochsPerHistoricalVector;
    return this;
  }

  public SpecConstantsBuilder epochsPerSlashingsVector(final Integer epochsPerSlashingsVector) {
    checkNotNull(epochsPerSlashingsVector);
    this.epochsPerSlashingsVector = epochsPerSlashingsVector;
    return this;
  }

  public SpecConstantsBuilder historicalRootsLimit(final Integer historicalRootsLimit) {
    checkNotNull(historicalRootsLimit);
    this.historicalRootsLimit = historicalRootsLimit;
    return this;
  }

  public SpecConstantsBuilder validatorRegistryLimit(final Long validatorRegistryLimit) {
    checkNotNull(validatorRegistryLimit);
    this.validatorRegistryLimit = validatorRegistryLimit;
    return this;
  }

  public SpecConstantsBuilder baseRewardFactor(final Integer baseRewardFactor) {
    checkNotNull(baseRewardFactor);
    this.baseRewardFactor = baseRewardFactor;
    return this;
  }

  public SpecConstantsBuilder whistleblowerRewardQuotient(
      final Integer whistleblowerRewardQuotient) {
    checkNotNull(whistleblowerRewardQuotient);
    this.whistleblowerRewardQuotient = whistleblowerRewardQuotient;
    return this;
  }

  public SpecConstantsBuilder proposerRewardQuotient(final UInt64 proposerRewardQuotient) {
    checkNotNull(proposerRewardQuotient);
    this.proposerRewardQuotient = proposerRewardQuotient;
    return this;
  }

  public SpecConstantsBuilder inactivityPenaltyQuotient(final UInt64 inactivityPenaltyQuotient) {
    checkNotNull(inactivityPenaltyQuotient);
    this.inactivityPenaltyQuotient = inactivityPenaltyQuotient;
    return this;
  }

  public SpecConstantsBuilder minSlashingPenaltyQuotient(final Integer minSlashingPenaltyQuotient) {
    checkNotNull(minSlashingPenaltyQuotient);
    this.minSlashingPenaltyQuotient = minSlashingPenaltyQuotient;
    return this;
  }

  public SpecConstantsBuilder maxProposerSlashings(final Integer maxProposerSlashings) {
    checkNotNull(maxProposerSlashings);
    this.maxProposerSlashings = maxProposerSlashings;
    return this;
  }

  public SpecConstantsBuilder maxAttesterSlashings(final Integer maxAttesterSlashings) {
    checkNotNull(maxAttesterSlashings);
    this.maxAttesterSlashings = maxAttesterSlashings;
    return this;
  }

  public SpecConstantsBuilder maxAttestations(final Integer maxAttestations) {
    checkNotNull(maxAttestations);
    this.maxAttestations = maxAttestations;
    return this;
  }

  public SpecConstantsBuilder maxDeposits(final Integer maxDeposits) {
    checkNotNull(maxDeposits);
    this.maxDeposits = maxDeposits;
    return this;
  }

  public SpecConstantsBuilder maxVoluntaryExits(final Integer maxVoluntaryExits) {
    checkNotNull(maxVoluntaryExits);
    this.maxVoluntaryExits = maxVoluntaryExits;
    return this;
  }

  public SpecConstantsBuilder domainBeaconProposer(final Bytes4 domainBeaconProposer) {
    checkNotNull(domainBeaconProposer);
    this.domainBeaconProposer = domainBeaconProposer;
    return this;
  }

  public SpecConstantsBuilder domainBeaconAttester(final Bytes4 domainBeaconAttester) {
    checkNotNull(domainBeaconAttester);
    this.domainBeaconAttester = domainBeaconAttester;
    return this;
  }

  public SpecConstantsBuilder domainRandao(final Bytes4 domainRandao) {
    checkNotNull(domainRandao);
    this.domainRandao = domainRandao;
    return this;
  }

  public SpecConstantsBuilder domainDeposit(final Bytes4 domainDeposit) {
    checkNotNull(domainDeposit);
    this.domainDeposit = domainDeposit;
    return this;
  }

  public SpecConstantsBuilder domainVoluntaryExit(final Bytes4 domainVoluntaryExit) {
    checkNotNull(domainVoluntaryExit);
    this.domainVoluntaryExit = domainVoluntaryExit;
    return this;
  }

  public SpecConstantsBuilder domainSelectionProof(final Bytes4 domainSelectionProof) {
    checkNotNull(domainSelectionProof);
    this.domainSelectionProof = domainSelectionProof;
    return this;
  }

  public SpecConstantsBuilder domainAggregateAndProof(final Bytes4 domainAggregateAndProof) {
    checkNotNull(domainAggregateAndProof);
    this.domainAggregateAndProof = domainAggregateAndProof;
    return this;
  }

  public SpecConstantsBuilder targetAggregatorsPerCommittee(
      final Integer targetAggregatorsPerCommittee) {
    checkNotNull(targetAggregatorsPerCommittee);
    this.targetAggregatorsPerCommittee = targetAggregatorsPerCommittee;
    return this;
  }

  public SpecConstantsBuilder secondsPerEth1Block(final UInt64 secondsPerEth1Block) {
    checkNotNull(secondsPerEth1Block);
    this.secondsPerEth1Block = secondsPerEth1Block;
    return this;
  }

  public SpecConstantsBuilder randomSubnetsPerValidator(final Integer randomSubnetsPerValidator) {
    checkNotNull(randomSubnetsPerValidator);
    this.randomSubnetsPerValidator = randomSubnetsPerValidator;
    return this;
  }

  public SpecConstantsBuilder epochsPerRandomSubnetSubscription(
      final Integer epochsPerRandomSubnetSubscription) {
    checkNotNull(epochsPerRandomSubnetSubscription);
    this.epochsPerRandomSubnetSubscription = epochsPerRandomSubnetSubscription;
    return this;
  }

  public SpecConstantsBuilder safeSlotsToUpdateJustified(final Integer safeSlotsToUpdateJustified) {
    checkNotNull(safeSlotsToUpdateJustified);
    this.safeSlotsToUpdateJustified = safeSlotsToUpdateJustified;
    return this;
  }

  public SpecConstantsBuilder depositChainId(final Integer depositChainId) {
    checkNotNull(depositChainId);
    this.depositChainId = depositChainId;
    return this;
  }

  public SpecConstantsBuilder depositNetworkId(final Integer depositNetworkId) {
    checkNotNull(depositNetworkId);
    this.depositNetworkId = depositNetworkId;
    return this;
  }

  public SpecConstantsBuilder depositContractAddress(final Bytes depositContractAddress) {
    checkNotNull(depositContractAddress);
    this.depositContractAddress = depositContractAddress;
    return this;
  }
}
