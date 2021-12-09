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

package tech.pegasys.teku.spec.config;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.spec.config.SpecConfigFormatter.camelToSnakeCase;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

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
  private UInt64 secondsPerEth1Block;

  // Fork Choice
  private Integer safeSlotsToUpdateJustified;
  // Added after Phase0 was live, so default to 0 which disables proposer score boosting.
  private int proposerScoreBoost = 0;

  // Deposit Contract
  private Integer depositChainId;
  private Integer depositNetworkId;
  private Bytes depositContractAddress;

  // Altair
  private Optional<AltairBuilder> altairBuilder = Optional.empty();

  // Merge
  private Optional<MergeBuilder> mergeBuilder = Optional.empty();

  public SpecConfig build() {
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
            depositContractAddress);

    if (altairBuilder.isPresent()) {
      final SpecConfigAltair altairConfig = altairBuilder.get().build(config);
      config = altairConfig;
      if (mergeBuilder.isPresent()) {
        final SpecConfigMerge mergeConfig = mergeBuilder.get().build(altairConfig);
        config = mergeBuilder.get().build(mergeConfig);
      }
    }
    return config;
  }

  private void validate() {
    checkArgument(rawConfig.size() > 0, "Raw spec config must be provided");
    validateConstant("eth1FollowDistance", eth1FollowDistance);
    validateConstant("maxCommitteesPerSlot", maxCommitteesPerSlot);
    validateConstant("targetCommitteeSize", targetCommitteeSize);
    validateConstant("maxValidatorsPerCommittee", maxValidatorsPerCommittee);
    validateConstant("minPerEpochChurnLimit", minPerEpochChurnLimit);
    validateConstant("churnLimitQuotient", churnLimitQuotient);
    validateConstant("shuffleRoundCount", shuffleRoundCount);
    validateConstant("minGenesisActiveValidatorCount", minGenesisActiveValidatorCount);
    validateConstant("minGenesisTime", minGenesisTime);
    validateConstant("hysteresisQuotient", hysteresisQuotient);
    validateConstant("hysteresisDownwardMultiplier", hysteresisDownwardMultiplier);
    validateConstant("hysteresisUpwardMultiplier", hysteresisUpwardMultiplier);
    validateConstant("proportionalSlashingMultiplier", proportionalSlashingMultiplier);
    validateConstant("minDepositAmount", minDepositAmount);
    validateConstant("maxEffectiveBalance", maxEffectiveBalance);
    validateConstant("ejectionBalance", ejectionBalance);
    validateConstant("effectiveBalanceIncrement", effectiveBalanceIncrement);
    validateConstant("genesisForkVersion", genesisForkVersion);
    validateConstant("genesisDelay", genesisDelay);
    validateConstant("secondsPerSlot", secondsPerSlot);
    validateConstant("minAttestationInclusionDelay", minAttestationInclusionDelay);
    validateConstant("slotsPerEpoch", slotsPerEpoch);
    validateConstant("minSeedLookahead", minSeedLookahead);
    validateConstant("maxSeedLookahead", maxSeedLookahead);
    validateConstant("minEpochsToInactivityPenalty", minEpochsToInactivityPenalty);
    validateConstant("epochsPerEth1VotingPeriod", epochsPerEth1VotingPeriod);
    validateConstant("slotsPerHistoricalRoot", slotsPerHistoricalRoot);
    validateConstant("minValidatorWithdrawabilityDelay", minValidatorWithdrawabilityDelay);
    validateConstant("shardCommitteePeriod", shardCommitteePeriod);
    validateConstant("epochsPerHistoricalVector", epochsPerHistoricalVector);
    validateConstant("epochsPerSlashingsVector", epochsPerSlashingsVector);
    validateConstant("historicalRootsLimit", historicalRootsLimit);
    validateConstant("validatorRegistryLimit", validatorRegistryLimit);
    validateConstant("baseRewardFactor", baseRewardFactor);
    validateConstant("whistleblowerRewardQuotient", whistleblowerRewardQuotient);
    validateConstant("proposerRewardQuotient", proposerRewardQuotient);
    validateConstant("inactivityPenaltyQuotient", inactivityPenaltyQuotient);
    validateConstant("minSlashingPenaltyQuotient", minSlashingPenaltyQuotient);
    validateConstant("maxProposerSlashings", maxProposerSlashings);
    validateConstant("maxAttesterSlashings", maxAttesterSlashings);
    validateConstant("maxAttestations", maxAttestations);
    validateConstant("maxDeposits", maxDeposits);
    validateConstant("maxVoluntaryExits", maxVoluntaryExits);
    validateConstant("secondsPerEth1Block", secondsPerEth1Block);
    validateConstant("safeSlotsToUpdateJustified", safeSlotsToUpdateJustified);
    validateConstant("depositChainId", depositChainId);
    validateConstant("depositNetworkId", depositNetworkId);
    validateConstant("depositContractAddress", depositContractAddress);

    altairBuilder.ifPresent(AltairBuilder::validate);
    mergeBuilder.ifPresent(MergeBuilder::validate);
  }

  private void validateConstant(final String name, final Object value) {
    validateNotNull(name, value);
  }

  private void validateConstant(final String name, final Long value) {
    validateNotNull(name, value);
    checkArgument(value >= 0, "Long values must be positive");
  }

  private void validateConstant(final String name, final Integer value) {
    validateNotNull(name, value);
    checkArgument(value >= 0, "Integer values must be positive");
  }

  private void validateNotNull(final String name, final Object value) {
    checkArgument(value != null, "Missing value for spec constant '%s'", camelToSnakeCase(name));
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

  public SpecConfigBuilder secondsPerEth1Block(final UInt64 secondsPerEth1Block) {
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

  public SpecConfigBuilder depositChainId(final Integer depositChainId) {
    checkNotNull(depositChainId);
    this.depositChainId = depositChainId;
    return this;
  }

  public SpecConfigBuilder depositNetworkId(final Integer depositNetworkId) {
    checkNotNull(depositNetworkId);
    this.depositNetworkId = depositNetworkId;
    return this;
  }

  public SpecConfigBuilder depositContractAddress(final Bytes depositContractAddress) {
    checkNotNull(depositContractAddress);
    this.depositContractAddress = depositContractAddress;
    return this;
  }

  // Altair
  public SpecConfigBuilder altairBuilder(final Consumer<AltairBuilder> consumer) {
    if (altairBuilder.isEmpty()) {
      altairBuilder = Optional.of(new AltairBuilder());
    }
    consumer.accept(altairBuilder.get());
    return this;
  }

  public class AltairBuilder {
    // Updated penalties
    private UInt64 inactivityPenaltyQuotientAltair;
    private Integer minSlashingPenaltyQuotientAltair;
    private Integer proportionalSlashingMultiplierAltair;

    // Misc
    private Integer syncCommitteeSize;
    private UInt64 inactivityScoreBias;
    private UInt64 inactivityScoreRecoveryRate;

    // Time
    private Integer epochsPerSyncCommitteePeriod;

    // Fork
    private Bytes4 altairForkVersion;
    private UInt64 altairForkEpoch;

    // Sync protocol
    private Integer minSyncCommitteeParticipants;

    private AltairBuilder() {}

    SpecConfigAltair build(final SpecConfig specConfig) {
      return new SpecConfigAltairImpl(
          specConfig,
          inactivityPenaltyQuotientAltair,
          minSlashingPenaltyQuotientAltair,
          proportionalSlashingMultiplierAltair,
          syncCommitteeSize,
          inactivityScoreBias,
          inactivityScoreRecoveryRate,
          epochsPerSyncCommitteePeriod,
          altairForkVersion,
          altairForkEpoch,
          minSyncCommitteeParticipants);
    }

    void validate() {
      validateConstant("inactivityPenaltyQuotientAltair", inactivityPenaltyQuotientAltair);
      validateConstant("minSlashingPenaltyQuotientAltair", minSlashingPenaltyQuotientAltair);
      validateConstant(
          "proportionalSlashingMultiplierAltair", proportionalSlashingMultiplierAltair);
      validateConstant("syncCommitteeSize", syncCommitteeSize);
      validateConstant("inactivityScoreBias", inactivityScoreBias);
      validateConstant("inactivityScoreRecoveryRate", inactivityScoreRecoveryRate);
      validateConstant("epochsPerSyncCommitteePeriod", epochsPerSyncCommitteePeriod);
      validateConstant("altairForkVersion", altairForkVersion);
      validateConstant("altairForkEpoch", altairForkEpoch);
      validateConstant("minSyncCommitteeParticipants", minSyncCommitteeParticipants);
    }

    public AltairBuilder inactivityPenaltyQuotientAltair(
        final UInt64 inactivityPenaltyQuotientAltair) {
      checkNotNull(inactivityPenaltyQuotientAltair);
      this.inactivityPenaltyQuotientAltair = inactivityPenaltyQuotientAltair;
      return this;
    }

    public AltairBuilder minSlashingPenaltyQuotientAltair(
        final Integer minSlashingPenaltyQuotientAltair) {
      checkNotNull(minSlashingPenaltyQuotientAltair);
      this.minSlashingPenaltyQuotientAltair = minSlashingPenaltyQuotientAltair;
      return this;
    }

    public AltairBuilder proportionalSlashingMultiplierAltair(
        final Integer proportionalSlashingMultiplierAltair) {
      checkNotNull(proportionalSlashingMultiplierAltair);
      this.proportionalSlashingMultiplierAltair = proportionalSlashingMultiplierAltair;
      return this;
    }

    public AltairBuilder syncCommitteeSize(final Integer syncCommitteeSize) {
      checkNotNull(syncCommitteeSize);
      this.syncCommitteeSize = syncCommitteeSize;
      return this;
    }

    public AltairBuilder epochsPerSyncCommitteePeriod(final Integer epochsPerSyncCommitteePeriod) {
      checkNotNull(epochsPerSyncCommitteePeriod);
      this.epochsPerSyncCommitteePeriod = epochsPerSyncCommitteePeriod;
      return this;
    }

    public AltairBuilder inactivityScoreBias(final UInt64 inactivityScoreBias) {
      checkNotNull(inactivityScoreBias);
      this.inactivityScoreBias = inactivityScoreBias;
      return this;
    }

    public AltairBuilder inactivityScoreRecoveryRate(final UInt64 inactivityScoreRecoveryRate) {
      checkNotNull(inactivityScoreRecoveryRate);
      this.inactivityScoreRecoveryRate = inactivityScoreRecoveryRate;
      return this;
    }

    public AltairBuilder altairForkVersion(final Bytes4 altairForkVersion) {
      checkNotNull(altairForkVersion);
      this.altairForkVersion = altairForkVersion;
      return this;
    }

    public AltairBuilder altairForkEpoch(final UInt64 altairForkEpoch) {
      checkNotNull(altairForkEpoch);
      this.altairForkEpoch = altairForkEpoch;
      return this;
    }

    public AltairBuilder minSyncCommitteeParticipants(final Integer minSyncCommitteeParticipants) {
      checkNotNull(minSyncCommitteeParticipants);
      this.minSyncCommitteeParticipants = minSyncCommitteeParticipants;
      return this;
    }
  }

  // Merge
  public SpecConfigBuilder mergeBuilder(final Consumer<MergeBuilder> consumer) {
    if (mergeBuilder.isEmpty()) {
      mergeBuilder = Optional.of(new MergeBuilder());
    }
    consumer.accept(mergeBuilder.get());
    return this;
  }

  public class MergeBuilder {
    // Fork
    private Bytes4 mergeForkVersion;
    private UInt64 mergeForkEpoch;
    private UInt64 inactivityPenaltyQuotientMerge;
    private Integer minSlashingPenaltyQuotientMerge;
    private Integer proportionalSlashingMultiplierMerge;
    private Integer maxBytesPerTransaction;
    private Integer maxTransactionsPerPayload;
    private Integer bytesPerLogsBloom;
    private Integer maxExtraDataBytes;

    // Transition
    private UInt256 terminalTotalDifficulty;
    private Bytes32 terminalBlockHash;
    private UInt64 terminalBlockHashActivationEpoch;

    private MergeBuilder() {}

    SpecConfigMerge build(final SpecConfigAltair specConfig) {
      return new SpecConfigMerge(
          specConfig,
          mergeForkVersion,
          mergeForkEpoch,
          inactivityPenaltyQuotientMerge,
          minSlashingPenaltyQuotientMerge,
          proportionalSlashingMultiplierMerge,
          maxBytesPerTransaction,
          maxTransactionsPerPayload,
          bytesPerLogsBloom,
          maxExtraDataBytes,
          terminalTotalDifficulty,
          terminalBlockHash,
          terminalBlockHashActivationEpoch);
    }

    void validate() {
      validateConstant("mergeForkVersion", mergeForkVersion);
      validateConstant("mergeForkEpoch", mergeForkEpoch);
      validateConstant("inactivityPenaltyQuotientMerge", inactivityPenaltyQuotientMerge);
      validateConstant("minSlashingPenaltyQuotientMerge", minSlashingPenaltyQuotientMerge);
      validateConstant("proportionalSlashingMultiplierMerge", proportionalSlashingMultiplierMerge);
      validateConstant("maxBytesPerTransaction", maxBytesPerTransaction);
      validateConstant("maxTransactionsPerPayload", maxTransactionsPerPayload);
      validateConstant("bytesPerLogsBloom", bytesPerLogsBloom);
      validateConstant("maxExtraDataBytes", maxExtraDataBytes);

      // temporary, provide default values for backward compatibility
      if (terminalTotalDifficulty == null) {
        terminalTotalDifficulty =
            UInt256.valueOf(
                new BigInteger(
                    "115792089237316195423570985008687907853269984665640564039457584007913129638912"));
      }
      if (terminalBlockHash == null) {
        terminalBlockHash = Bytes32.fromHexStringLenient("0x00");
      }
      if (terminalBlockHashActivationEpoch == null) {
        terminalBlockHashActivationEpoch = UInt64.valueOf("18446744073709551615");
      }
    }

    public MergeBuilder mergeForkVersion(final Bytes4 mergeForkVersion) {
      checkNotNull(mergeForkVersion);
      this.mergeForkVersion = mergeForkVersion;
      return this;
    }

    public MergeBuilder mergeForkEpoch(final UInt64 mergeForkEpoch) {
      checkNotNull(mergeForkEpoch);
      this.mergeForkEpoch = mergeForkEpoch;
      return this;
    }

    public MergeBuilder inactivityPenaltyQuotientMerge(
        final UInt64 inactivityPenaltyQuotientMerge) {
      this.inactivityPenaltyQuotientMerge = inactivityPenaltyQuotientMerge;
      return this;
    }

    public MergeBuilder minSlashingPenaltyQuotientMerge(
        final Integer minSlashingPenaltyQuotientMerge) {
      this.minSlashingPenaltyQuotientMerge = minSlashingPenaltyQuotientMerge;
      return this;
    }

    public MergeBuilder proportionalSlashingMultiplierMerge(
        final Integer proportionalSlashingMultiplierMerge) {
      this.proportionalSlashingMultiplierMerge = proportionalSlashingMultiplierMerge;
      return this;
    }

    public MergeBuilder maxBytesPerTransaction(final int maxBytesPerTransaction) {
      this.maxBytesPerTransaction = maxBytesPerTransaction;
      return this;
    }

    public MergeBuilder maxTransactionsPerPayload(final int maxTransactionsPerPayload) {
      this.maxTransactionsPerPayload = maxTransactionsPerPayload;
      return this;
    }

    public MergeBuilder bytesPerLogsBloom(final int bytesPerLogsBloom) {
      this.bytesPerLogsBloom = bytesPerLogsBloom;
      return this;
    }

    public MergeBuilder terminalTotalDifficulty(final UInt256 terminalTotalDifficulty) {
      this.terminalTotalDifficulty = terminalTotalDifficulty;
      return this;
    }

    public MergeBuilder terminalBlockHash(final Bytes32 terminalBlockHash) {
      this.terminalBlockHash = terminalBlockHash;
      return this;
    }

    public MergeBuilder terminalBlockHashActivationEpoch(
        final UInt64 terminalBlockHashActivationEpoch) {
      this.terminalBlockHashActivationEpoch = terminalBlockHashActivationEpoch;
      return this;
    }

    public MergeBuilder maxExtraDataBytes(final int maxExtraDataBytes) {
      this.maxExtraDataBytes = maxExtraDataBytes;
      return this;
    }
  }
}
