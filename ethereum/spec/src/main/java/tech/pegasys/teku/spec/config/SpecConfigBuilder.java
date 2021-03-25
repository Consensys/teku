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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.type.Bytes4;

public class SpecConfigBuilder {
  private final Map<String, Object> rawConfig = new HashMap<>();
  private String configName = "Custom (unknown)";

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
  private Bytes blsWithdrawalPrefix;

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

  // Signature domains
  private Bytes4 domainBeaconProposer;
  private Bytes4 domainBeaconAttester;
  private Bytes4 domainRandao;
  private Bytes4 domainDeposit;
  private Bytes4 domainVoluntaryExit;
  private Bytes4 domainSelectionProof;
  private Bytes4 domainAggregateAndProof;

  // Validator
  private Integer targetAggregatorsPerCommittee;
  private UInt64 secondsPerEth1Block;
  private Integer randomSubnetsPerValidator;
  private Integer epochsPerRandomSubnetSubscription;

  // Fork Choice
  private Integer safeSlotsToUpdateJustified;

  // Deposit Contract
  private Integer depositChainId;
  private Integer depositNetworkId;
  private Bytes depositContractAddress;

  // Altair
  private Optional<AltairBuilder> altairBuilder = Optional.empty();

  public SpecConfig build() {
    validate();
    final SpecConfig phase0 =
        new SpecConfigPhase0(
            rawConfig,
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

    return altairBuilder.map(b -> (SpecConfig) b.build(phase0)).orElse(phase0);
  }

  private void validate() {
    checkArgument(rawConfig.size() > 0, "Raw spec config must be provided");
    validateConstant("configName", configName);
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
    validateConstant("blsWithdrawalPrefix", blsWithdrawalPrefix);
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
    validateConstant("domainBeaconProposer", domainBeaconProposer);
    validateConstant("domainBeaconAttester", domainBeaconAttester);
    validateConstant("domainRandao", domainRandao);
    validateConstant("domainDeposit", domainDeposit);
    validateConstant("domainVoluntaryExit", domainVoluntaryExit);
    validateConstant("domainSelectionProof", domainSelectionProof);
    validateConstant("domainAggregateAndProof", domainAggregateAndProof);
    validateConstant("targetAggregatorsPerCommittee", targetAggregatorsPerCommittee);
    validateConstant("secondsPerEth1Block", secondsPerEth1Block);
    validateConstant("randomSubnetsPerValidator", randomSubnetsPerValidator);
    validateConstant("epochsPerRandomSubnetSubscription", epochsPerRandomSubnetSubscription);
    validateConstant("safeSlotsToUpdateJustified", safeSlotsToUpdateJustified);
    validateConstant("depositChainId", depositChainId);
    validateConstant("depositNetworkId", depositNetworkId);
    validateConstant("depositContractAddress", depositContractAddress);

    altairBuilder.ifPresent(AltairBuilder::validate);
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

  public SpecConfigBuilder rawConfig(final Map<String, Object> rawConfig) {
    checkNotNull(rawConfig);
    this.rawConfig.putAll(rawConfig);
    return this;
  }

  public SpecConfigBuilder configName(final String configName) {
    checkNotNull(configName);
    this.configName = configName;
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

  public SpecConfigBuilder blsWithdrawalPrefix(final Bytes blsWithdrawalPrefix) {
    checkNotNull(blsWithdrawalPrefix);
    this.blsWithdrawalPrefix = blsWithdrawalPrefix;
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

  public SpecConfigBuilder domainBeaconProposer(final Bytes4 domainBeaconProposer) {
    checkNotNull(domainBeaconProposer);
    this.domainBeaconProposer = domainBeaconProposer;
    return this;
  }

  public SpecConfigBuilder domainBeaconAttester(final Bytes4 domainBeaconAttester) {
    checkNotNull(domainBeaconAttester);
    this.domainBeaconAttester = domainBeaconAttester;
    return this;
  }

  public SpecConfigBuilder domainRandao(final Bytes4 domainRandao) {
    checkNotNull(domainRandao);
    this.domainRandao = domainRandao;
    return this;
  }

  public SpecConfigBuilder domainDeposit(final Bytes4 domainDeposit) {
    checkNotNull(domainDeposit);
    this.domainDeposit = domainDeposit;
    return this;
  }

  public SpecConfigBuilder domainVoluntaryExit(final Bytes4 domainVoluntaryExit) {
    checkNotNull(domainVoluntaryExit);
    this.domainVoluntaryExit = domainVoluntaryExit;
    return this;
  }

  public SpecConfigBuilder domainSelectionProof(final Bytes4 domainSelectionProof) {
    checkNotNull(domainSelectionProof);
    this.domainSelectionProof = domainSelectionProof;
    return this;
  }

  public SpecConfigBuilder domainAggregateAndProof(final Bytes4 domainAggregateAndProof) {
    checkNotNull(domainAggregateAndProof);
    this.domainAggregateAndProof = domainAggregateAndProof;
    return this;
  }

  public SpecConfigBuilder targetAggregatorsPerCommittee(
      final Integer targetAggregatorsPerCommittee) {
    checkNotNull(targetAggregatorsPerCommittee);
    this.targetAggregatorsPerCommittee = targetAggregatorsPerCommittee;
    return this;
  }

  public SpecConfigBuilder secondsPerEth1Block(final UInt64 secondsPerEth1Block) {
    checkNotNull(secondsPerEth1Block);
    this.secondsPerEth1Block = secondsPerEth1Block;
    return this;
  }

  public SpecConfigBuilder randomSubnetsPerValidator(final Integer randomSubnetsPerValidator) {
    checkNotNull(randomSubnetsPerValidator);
    this.randomSubnetsPerValidator = randomSubnetsPerValidator;
    return this;
  }

  public SpecConfigBuilder epochsPerRandomSubnetSubscription(
      final Integer epochsPerRandomSubnetSubscription) {
    checkNotNull(epochsPerRandomSubnetSubscription);
    this.epochsPerRandomSubnetSubscription = epochsPerRandomSubnetSubscription;
    return this;
  }

  public SpecConfigBuilder safeSlotsToUpdateJustified(final Integer safeSlotsToUpdateJustified) {
    checkNotNull(safeSlotsToUpdateJustified);
    this.safeSlotsToUpdateJustified = safeSlotsToUpdateJustified;
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

  class AltairBuilder {
    // Updated penalties
    private UInt64 inactivityPenaltyQuotientAltair;
    private Integer minSlashingPenaltyQuotientAltair;
    private Integer proportionalSlashingMultiplierAltair;

    // Misc
    private Integer syncCommitteeSize;
    private Integer syncSubcommitteeSize;
    private UInt64 inactivityScoreBias;

    // Time
    private Integer epochsPerSyncCommitteePeriod;

    // Signature domains
    private Bytes4 domainSyncCommittee;

    // Fork
    private Bytes4 altairForkVersion;

    // Sync protocol
    private Integer minSyncCommitteeParticipants;
    private Integer maxValidLightClientUpdates;
    private Integer lightClientUpdateTimeout;

    private AltairBuilder() {}

    SpecConfigAltair build(final SpecConfig specConfig) {
      return new SpecConfigAltair(
          specConfig,
          inactivityPenaltyQuotientAltair,
          minSlashingPenaltyQuotientAltair,
          proportionalSlashingMultiplierAltair,
          syncCommitteeSize,
          syncSubcommitteeSize,
          inactivityScoreBias,
          epochsPerSyncCommitteePeriod,
          domainSyncCommittee,
          altairForkVersion,
          minSyncCommitteeParticipants,
          maxValidLightClientUpdates,
          lightClientUpdateTimeout);
    }

    void validate() {
      validateConstant("inactivityPenaltyQuotientAltair", inactivityPenaltyQuotientAltair);
      validateConstant("minSlashingPenaltyQuotientAltair", minSlashingPenaltyQuotientAltair);
      validateConstant(
          "proportionalSlashingMultiplierAltair", proportionalSlashingMultiplierAltair);
      validateConstant("syncCommitteeSize", syncCommitteeSize);
      validateConstant("syncSubcommitteeSize", syncSubcommitteeSize);
      validateConstant("inactivityScoreBias", inactivityScoreBias);
      validateConstant("epochsPerSyncCommitteePeriod", epochsPerSyncCommitteePeriod);
      validateConstant("domainSyncCommittee", domainSyncCommittee);
      validateConstant("altairForkVersion", altairForkVersion);
      validateConstant("minSyncCommitteeParticipants", minSyncCommitteeParticipants);
      validateConstant("maxValidLightClientUpdates", maxValidLightClientUpdates);
      validateConstant("lightClientUpdateTimeout", lightClientUpdateTimeout);
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

    public AltairBuilder syncSubcommitteeSize(final Integer syncSubcommitteeSize) {
      checkNotNull(syncSubcommitteeSize);
      this.syncSubcommitteeSize = syncSubcommitteeSize;
      return this;
    }

    public AltairBuilder epochsPerSyncCommitteePeriod(final Integer epochsPerSyncCommitteePeriod) {
      checkNotNull(epochsPerSyncCommitteePeriod);
      this.epochsPerSyncCommitteePeriod = epochsPerSyncCommitteePeriod;
      return this;
    }

    public AltairBuilder domainSyncCommittee(final Bytes4 domainSyncCommittee) {
      checkNotNull(domainSyncCommittee);
      this.domainSyncCommittee = domainSyncCommittee;
      return this;
    }

    public AltairBuilder inactivityScoreBias(final UInt64 inactivityScoreBias) {
      checkNotNull(inactivityScoreBias);
      this.inactivityScoreBias = inactivityScoreBias;
      return this;
    }

    public AltairBuilder altairForkVersion(final Bytes4 altairForkVersion) {
      checkNotNull(altairForkVersion);
      this.altairForkVersion = altairForkVersion;
      return this;
    }

    public AltairBuilder minSyncCommitteeParticipants(final Integer minSyncCommitteeParticipants) {
      checkNotNull(minSyncCommitteeParticipants);
      this.minSyncCommitteeParticipants = minSyncCommitteeParticipants;
      return this;
    }

    public AltairBuilder maxValidLightClientUpdates(final Integer maxValidLightClientUpdates) {
      checkNotNull(maxValidLightClientUpdates);
      this.maxValidLightClientUpdates = maxValidLightClientUpdates;
      return this;
    }

    public AltairBuilder lightClientUpdateTimeout(final Integer lightClientUpdateTimeout) {
      checkNotNull(lightClientUpdateTimeout);
      this.lightClientUpdateTimeout = lightClientUpdateTimeout;
      return this;
    }
  }
}
