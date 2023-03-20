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

package tech.pegasys.teku.spec.config.builder;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.ProgressiveBalancesMode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigPhase0;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public class SpecConfigBuilder {

  private final Map<String, Object> rawConfig = new HashMap<>();

  // Misc
  private UInt64 eth1FollowDistance;
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
  private Bytes4 genesisForkVersion;

  // Time parameters
  private UInt64 genesisDelay;
  private Integer secondsPerSlot;
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
  private Integer maxVoluntaryExits;

  // Validator
  private Integer secondsPerEth1Block;

  // Fork Choice
  private Integer safeSlotsToUpdateJustified;
  // Added after Phase0 was live, so default to 0 which disables proposer score boosting.
  private int proposerScoreBoost = 0;

  // Deposit Contract
  private Long depositChainId;
  private Long depositNetworkId;
  private Eth1Address depositContractAddress;

  private ProgressiveBalancesMode progressiveBalancesMode = ProgressiveBalancesMode.FULL;

  private final BuilderChain<SpecConfig, SpecConfigDeneb> builderChain =
      BuilderChain.create(new AltairBuilder())
          .appendBuilder(new BellatrixBuilder())
          .appendBuilder(new CapellaBuilder())
          .appendBuilder(new DenebBuilder());

  public SpecConfig build() {
    builderChain.addOverridableItemsToRawConfig(
        (key, value) -> {
          if (value != null) {
            rawConfig.put(key, value);
          }
        });
    validate();
    SpecConfig config =
        new SpecConfigPhase0(
            rawConfig,
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
            progressiveBalancesMode);

    return builderChain.build(config);
  }

  private void validate() {
    checkArgument(rawConfig.size() > 0, "Raw spec config must be provided");
    SpecBuilderUtil.validateConstant("eth1FollowDistance", eth1FollowDistance);
    SpecBuilderUtil.validateConstant("maxCommitteesPerSlot", maxCommitteesPerSlot);
    SpecBuilderUtil.validateConstant("targetCommitteeSize", targetCommitteeSize);
    SpecBuilderUtil.validateConstant("maxValidatorsPerCommittee", maxValidatorsPerCommittee);
    SpecBuilderUtil.validateConstant("minPerEpochChurnLimit", minPerEpochChurnLimit);
    SpecBuilderUtil.validateConstant("churnLimitQuotient", churnLimitQuotient);
    SpecBuilderUtil.validateConstant("shuffleRoundCount", shuffleRoundCount);
    SpecBuilderUtil.validateConstant(
        "minGenesisActiveValidatorCount", minGenesisActiveValidatorCount);
    SpecBuilderUtil.validateConstant("minGenesisTime", minGenesisTime);
    SpecBuilderUtil.validateConstant("hysteresisQuotient", hysteresisQuotient);
    SpecBuilderUtil.validateConstant("hysteresisDownwardMultiplier", hysteresisDownwardMultiplier);
    SpecBuilderUtil.validateConstant("hysteresisUpwardMultiplier", hysteresisUpwardMultiplier);
    SpecBuilderUtil.validateConstant(
        "proportionalSlashingMultiplier", proportionalSlashingMultiplier);
    SpecBuilderUtil.validateConstant("minDepositAmount", minDepositAmount);
    SpecBuilderUtil.validateConstant("maxEffectiveBalance", maxEffectiveBalance);
    SpecBuilderUtil.validateConstant("ejectionBalance", ejectionBalance);
    SpecBuilderUtil.validateConstant("effectiveBalanceIncrement", effectiveBalanceIncrement);
    SpecBuilderUtil.validateConstant("genesisForkVersion", genesisForkVersion);
    SpecBuilderUtil.validateConstant("genesisDelay", genesisDelay);
    SpecBuilderUtil.validateConstant("secondsPerSlot", secondsPerSlot);
    SpecBuilderUtil.validateConstant("minAttestationInclusionDelay", minAttestationInclusionDelay);
    SpecBuilderUtil.validateConstant("slotsPerEpoch", slotsPerEpoch);
    SpecBuilderUtil.validateConstant("minSeedLookahead", minSeedLookahead);
    SpecBuilderUtil.validateConstant("maxSeedLookahead", maxSeedLookahead);
    SpecBuilderUtil.validateConstant("minEpochsToInactivityPenalty", minEpochsToInactivityPenalty);
    SpecBuilderUtil.validateConstant("epochsPerEth1VotingPeriod", epochsPerEth1VotingPeriod);
    SpecBuilderUtil.validateConstant("slotsPerHistoricalRoot", slotsPerHistoricalRoot);
    SpecBuilderUtil.validateConstant(
        "minValidatorWithdrawabilityDelay", minValidatorWithdrawabilityDelay);
    SpecBuilderUtil.validateConstant("shardCommitteePeriod", shardCommitteePeriod);
    SpecBuilderUtil.validateConstant("epochsPerHistoricalVector", epochsPerHistoricalVector);
    SpecBuilderUtil.validateConstant("epochsPerSlashingsVector", epochsPerSlashingsVector);
    SpecBuilderUtil.validateConstant("historicalRootsLimit", historicalRootsLimit);
    SpecBuilderUtil.validateConstant("validatorRegistryLimit", validatorRegistryLimit);
    SpecBuilderUtil.validateConstant("baseRewardFactor", baseRewardFactor);
    SpecBuilderUtil.validateConstant("whistleblowerRewardQuotient", whistleblowerRewardQuotient);
    SpecBuilderUtil.validateConstant("proposerRewardQuotient", proposerRewardQuotient);
    SpecBuilderUtil.validateConstant("inactivityPenaltyQuotient", inactivityPenaltyQuotient);
    SpecBuilderUtil.validateConstant("minSlashingPenaltyQuotient", minSlashingPenaltyQuotient);
    SpecBuilderUtil.validateConstant("maxProposerSlashings", maxProposerSlashings);
    SpecBuilderUtil.validateConstant("maxAttesterSlashings", maxAttesterSlashings);
    SpecBuilderUtil.validateConstant("maxAttestations", maxAttestations);
    SpecBuilderUtil.validateConstant("maxDeposits", maxDeposits);
    SpecBuilderUtil.validateConstant("maxVoluntaryExits", maxVoluntaryExits);
    SpecBuilderUtil.validateConstant("secondsPerEth1Block", secondsPerEth1Block);
    SpecBuilderUtil.validateConstant("safeSlotsToUpdateJustified", safeSlotsToUpdateJustified);
    SpecBuilderUtil.validateConstant("depositChainId", depositChainId);
    SpecBuilderUtil.validateConstant("depositNetworkId", depositNetworkId);
    SpecBuilderUtil.validateConstant("depositContractAddress", depositContractAddress);

    builderChain.validate();
  }

  public SpecConfigBuilder rawConfig(final Map<String, ?> rawConfig) {
    checkNotNull(rawConfig);
    this.rawConfig.putAll(rawConfig);
    return this;
  }

  public SpecConfigBuilder eth1FollowDistance(final UInt64 eth1FollowDistance) {
    checkNotNull(eth1FollowDistance);
    this.eth1FollowDistance = eth1FollowDistance;
    return this;
  }

  public SpecConfigBuilder maxCommitteesPerSlot(final Integer maxCommitteesPerSlot) {
    checkNotNull(maxCommitteesPerSlot);
    this.maxCommitteesPerSlot = maxCommitteesPerSlot;
    return this;
  }

  public SpecConfigBuilder targetCommitteeSize(final Integer targetCommitteeSize) {
    checkNotNull(targetCommitteeSize);
    this.targetCommitteeSize = targetCommitteeSize;
    return this;
  }

  public SpecConfigBuilder maxValidatorsPerCommittee(final Integer maxValidatorsPerCommittee) {
    checkNotNull(maxValidatorsPerCommittee);
    this.maxValidatorsPerCommittee = maxValidatorsPerCommittee;
    return this;
  }

  public SpecConfigBuilder minPerEpochChurnLimit(final Integer minPerEpochChurnLimit) {
    checkNotNull(minPerEpochChurnLimit);
    this.minPerEpochChurnLimit = minPerEpochChurnLimit;
    return this;
  }

  public SpecConfigBuilder churnLimitQuotient(final Integer churnLimitQuotient) {
    checkNotNull(churnLimitQuotient);
    this.churnLimitQuotient = churnLimitQuotient;
    return this;
  }

  public SpecConfigBuilder shuffleRoundCount(final Integer shuffleRoundCount) {
    checkNotNull(shuffleRoundCount);
    this.shuffleRoundCount = shuffleRoundCount;
    return this;
  }

  public SpecConfigBuilder minGenesisActiveValidatorCount(
      final Integer minGenesisActiveValidatorCount) {
    checkNotNull(minGenesisActiveValidatorCount);
    this.minGenesisActiveValidatorCount = minGenesisActiveValidatorCount;
    return this;
  }

  public SpecConfigBuilder minGenesisTime(final UInt64 minGenesisTime) {
    checkNotNull(minGenesisTime);
    this.minGenesisTime = minGenesisTime;
    return this;
  }

  public SpecConfigBuilder hysteresisQuotient(final UInt64 hysteresisQuotient) {
    checkNotNull(hysteresisQuotient);
    this.hysteresisQuotient = hysteresisQuotient;
    return this;
  }

  public SpecConfigBuilder hysteresisDownwardMultiplier(final UInt64 hysteresisDownwardMultiplier) {
    checkNotNull(hysteresisDownwardMultiplier);
    this.hysteresisDownwardMultiplier = hysteresisDownwardMultiplier;
    return this;
  }

  public SpecConfigBuilder hysteresisUpwardMultiplier(final UInt64 hysteresisUpwardMultiplier) {
    checkNotNull(hysteresisUpwardMultiplier);
    this.hysteresisUpwardMultiplier = hysteresisUpwardMultiplier;
    return this;
  }

  public SpecConfigBuilder proportionalSlashingMultiplier(
      final Integer proportionalSlashingMultiplier) {
    checkNotNull(proportionalSlashingMultiplier);
    this.proportionalSlashingMultiplier = proportionalSlashingMultiplier;
    return this;
  }

  public SpecConfigBuilder minDepositAmount(final UInt64 minDepositAmount) {
    checkNotNull(minDepositAmount);
    this.minDepositAmount = minDepositAmount;
    return this;
  }

  public SpecConfigBuilder maxEffectiveBalance(final UInt64 maxEffectiveBalance) {
    checkNotNull(maxEffectiveBalance);
    this.maxEffectiveBalance = maxEffectiveBalance;
    return this;
  }

  public SpecConfigBuilder ejectionBalance(final UInt64 ejectionBalance) {
    checkNotNull(ejectionBalance);
    this.ejectionBalance = ejectionBalance;
    return this;
  }

  public SpecConfigBuilder effectiveBalanceIncrement(final UInt64 effectiveBalanceIncrement) {
    checkNotNull(effectiveBalanceIncrement);
    this.effectiveBalanceIncrement = effectiveBalanceIncrement;
    return this;
  }

  public SpecConfigBuilder genesisForkVersion(final Bytes4 genesisForkVersion) {
    checkNotNull(genesisForkVersion);
    this.genesisForkVersion = genesisForkVersion;
    return this;
  }

  public SpecConfigBuilder genesisDelay(final UInt64 genesisDelay) {
    checkNotNull(genesisDelay);
    this.genesisDelay = genesisDelay;
    return this;
  }

  public SpecConfigBuilder secondsPerSlot(final Integer secondsPerSlot) {
    checkNotNull(secondsPerSlot);
    this.secondsPerSlot = secondsPerSlot;
    return this;
  }

  public SpecConfigBuilder minAttestationInclusionDelay(
      final Integer minAttestationInclusionDelay) {
    checkNotNull(minAttestationInclusionDelay);
    this.minAttestationInclusionDelay = minAttestationInclusionDelay;
    return this;
  }

  public SpecConfigBuilder slotsPerEpoch(final Integer slotsPerEpoch) {
    checkNotNull(slotsPerEpoch);
    this.slotsPerEpoch = slotsPerEpoch;
    return this;
  }

  public SpecConfigBuilder minSeedLookahead(final Integer minSeedLookahead) {
    checkNotNull(minSeedLookahead);
    this.minSeedLookahead = minSeedLookahead;
    return this;
  }

  public SpecConfigBuilder maxSeedLookahead(final Integer maxSeedLookahead) {
    checkNotNull(maxSeedLookahead);
    this.maxSeedLookahead = maxSeedLookahead;
    return this;
  }

  public SpecConfigBuilder minEpochsToInactivityPenalty(final UInt64 minEpochsToInactivityPenalty) {
    checkNotNull(minEpochsToInactivityPenalty);
    this.minEpochsToInactivityPenalty = minEpochsToInactivityPenalty;
    return this;
  }

  public SpecConfigBuilder epochsPerEth1VotingPeriod(final Integer epochsPerEth1VotingPeriod) {
    checkNotNull(epochsPerEth1VotingPeriod);
    this.epochsPerEth1VotingPeriod = epochsPerEth1VotingPeriod;
    return this;
  }

  public SpecConfigBuilder slotsPerHistoricalRoot(final Integer slotsPerHistoricalRoot) {
    checkNotNull(slotsPerHistoricalRoot);
    this.slotsPerHistoricalRoot = slotsPerHistoricalRoot;
    return this;
  }

  public SpecConfigBuilder minValidatorWithdrawabilityDelay(
      final Integer minValidatorWithdrawabilityDelay) {
    checkNotNull(minValidatorWithdrawabilityDelay);
    this.minValidatorWithdrawabilityDelay = minValidatorWithdrawabilityDelay;
    return this;
  }

  public SpecConfigBuilder shardCommitteePeriod(final UInt64 shardCommitteePeriod) {
    checkNotNull(shardCommitteePeriod);
    this.shardCommitteePeriod = shardCommitteePeriod;
    return this;
  }

  public SpecConfigBuilder epochsPerHistoricalVector(final Integer epochsPerHistoricalVector) {
    checkNotNull(epochsPerHistoricalVector);
    this.epochsPerHistoricalVector = epochsPerHistoricalVector;
    return this;
  }

  public SpecConfigBuilder epochsPerSlashingsVector(final Integer epochsPerSlashingsVector) {
    checkNotNull(epochsPerSlashingsVector);
    this.epochsPerSlashingsVector = epochsPerSlashingsVector;
    return this;
  }

  public SpecConfigBuilder historicalRootsLimit(final Integer historicalRootsLimit) {
    checkNotNull(historicalRootsLimit);
    this.historicalRootsLimit = historicalRootsLimit;
    return this;
  }

  public SpecConfigBuilder validatorRegistryLimit(final Long validatorRegistryLimit) {
    checkNotNull(validatorRegistryLimit);
    this.validatorRegistryLimit = validatorRegistryLimit;
    return this;
  }

  public SpecConfigBuilder baseRewardFactor(final Integer baseRewardFactor) {
    checkNotNull(baseRewardFactor);
    this.baseRewardFactor = baseRewardFactor;
    return this;
  }

  public SpecConfigBuilder whistleblowerRewardQuotient(final Integer whistleblowerRewardQuotient) {
    checkNotNull(whistleblowerRewardQuotient);
    this.whistleblowerRewardQuotient = whistleblowerRewardQuotient;
    return this;
  }

  public SpecConfigBuilder proposerRewardQuotient(final UInt64 proposerRewardQuotient) {
    checkNotNull(proposerRewardQuotient);
    this.proposerRewardQuotient = proposerRewardQuotient;
    return this;
  }

  public SpecConfigBuilder inactivityPenaltyQuotient(final UInt64 inactivityPenaltyQuotient) {
    checkNotNull(inactivityPenaltyQuotient);
    this.inactivityPenaltyQuotient = inactivityPenaltyQuotient;
    return this;
  }

  public SpecConfigBuilder minSlashingPenaltyQuotient(final Integer minSlashingPenaltyQuotient) {
    checkNotNull(minSlashingPenaltyQuotient);
    this.minSlashingPenaltyQuotient = minSlashingPenaltyQuotient;
    return this;
  }

  public SpecConfigBuilder maxProposerSlashings(final Integer maxProposerSlashings) {
    checkNotNull(maxProposerSlashings);
    this.maxProposerSlashings = maxProposerSlashings;
    return this;
  }

  public SpecConfigBuilder maxAttesterSlashings(final Integer maxAttesterSlashings) {
    checkNotNull(maxAttesterSlashings);
    this.maxAttesterSlashings = maxAttesterSlashings;
    return this;
  }

  public SpecConfigBuilder maxAttestations(final Integer maxAttestations) {
    checkNotNull(maxAttestations);
    this.maxAttestations = maxAttestations;
    return this;
  }

  public SpecConfigBuilder maxDeposits(final Integer maxDeposits) {
    checkNotNull(maxDeposits);
    this.maxDeposits = maxDeposits;
    return this;
  }

  public SpecConfigBuilder maxVoluntaryExits(final Integer maxVoluntaryExits) {
    checkNotNull(maxVoluntaryExits);
    this.maxVoluntaryExits = maxVoluntaryExits;
    return this;
  }

  public SpecConfigBuilder secondsPerEth1Block(final Integer secondsPerEth1Block) {
    checkNotNull(secondsPerEth1Block);
    this.secondsPerEth1Block = secondsPerEth1Block;
    return this;
  }

  public SpecConfigBuilder safeSlotsToUpdateJustified(final Integer safeSlotsToUpdateJustified) {
    checkNotNull(safeSlotsToUpdateJustified);
    this.safeSlotsToUpdateJustified = safeSlotsToUpdateJustified;
    return this;
  }

  public SpecConfigBuilder proposerScoreBoost(final Integer proposerScoreBoost) {
    checkNotNull(proposerScoreBoost);
    this.proposerScoreBoost = proposerScoreBoost;
    return this;
  }

  public SpecConfigBuilder depositChainId(final Long depositChainId) {
    checkNotNull(depositChainId);
    this.depositChainId = depositChainId;
    return this;
  }

  public SpecConfigBuilder depositNetworkId(final Long depositNetworkId) {
    checkNotNull(depositNetworkId);
    this.depositNetworkId = depositNetworkId;
    return this;
  }

  public SpecConfigBuilder depositContractAddress(final Eth1Address depositContractAddress) {
    checkNotNull(depositContractAddress);
    this.depositContractAddress = depositContractAddress;
    return this;
  }

  public SpecConfigBuilder progressiveBalancesMode(
      final ProgressiveBalancesMode progressiveBalancesMode) {
    this.progressiveBalancesMode = progressiveBalancesMode;
    return this;
  }

  public SpecConfigBuilder altairBuilder(final Consumer<AltairBuilder> consumer) {
    builderChain.withBuilder(AltairBuilder.class, consumer);
    return this;
  }

  public SpecConfigBuilder bellatrixBuilder(final Consumer<BellatrixBuilder> consumer) {
    builderChain.withBuilder(BellatrixBuilder.class, consumer);
    return this;
  }

  public SpecConfigBuilder capellaBuilder(final Consumer<CapellaBuilder> consumer) {
    builderChain.withBuilder(CapellaBuilder.class, consumer);
    return this;
  }

  public SpecConfigBuilder denebBuilder(final Consumer<DenebBuilder> consumer) {
    builderChain.withBuilder(DenebBuilder.class, consumer);
    return this;
  }
}
