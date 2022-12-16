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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.BiConsumer;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigAltairImpl;

public class AltairBuilder implements ForkConfigBuilder<SpecConfig, SpecConfigAltair> {

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
  private Integer updateTimeout;

  // Light client
  private Integer syncCommitteeBranchLength;
  private Integer finalityBranchLength;
  private static final int SYNC_COMMITTEE_BRANCH_LENGTH_DEFAULT = 5;
  private static final int FINALITY_BRANCH_LENGTH_DEFAULT = 6;

  AltairBuilder() {}

  @Override
  public SpecConfigAltair build(final SpecConfig specConfig) {
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
        minSyncCommitteeParticipants,
        updateTimeout,
        syncCommitteeBranchLength,
        finalityBranchLength);
  }

  @Override
  public void validate() {
    if (altairForkEpoch == null) {
      altairForkEpoch = SpecConfig.FAR_FUTURE_EPOCH;
      altairForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
      inactivityScoreBias = UInt64.valueOf(4);
      inactivityScoreRecoveryRate = UInt64.valueOf(16);
    }
    SpecBuilderUtil.validateConstant(
        "inactivityPenaltyQuotientAltair", inactivityPenaltyQuotientAltair);
    SpecBuilderUtil.validateConstant(
        "minSlashingPenaltyQuotientAltair", minSlashingPenaltyQuotientAltair);
    SpecBuilderUtil.validateConstant(
        "proportionalSlashingMultiplierAltair", proportionalSlashingMultiplierAltair);
    SpecBuilderUtil.validateConstant("syncCommitteeSize", syncCommitteeSize);
    SpecBuilderUtil.validateConstant("inactivityScoreBias", inactivityScoreBias);
    SpecBuilderUtil.validateConstant("inactivityScoreRecoveryRate", inactivityScoreRecoveryRate);
    SpecBuilderUtil.validateConstant("epochsPerSyncCommitteePeriod", epochsPerSyncCommitteePeriod);
    SpecBuilderUtil.validateConstant("altairForkVersion", altairForkVersion);
    SpecBuilderUtil.validateConstant("altairForkEpoch", altairForkEpoch);
    SpecBuilderUtil.validateConstant("minSyncCommitteeParticipants", minSyncCommitteeParticipants);
    // Config items were added after launch so provide defaults to preserve compatibility
    if (updateTimeout == null) {
      updateTimeout = epochsPerSyncCommitteePeriod * 32;
    }
    if (syncCommitteeBranchLength == null) {
      syncCommitteeBranchLength = SYNC_COMMITTEE_BRANCH_LENGTH_DEFAULT;
    }
    if (finalityBranchLength == null) {
      finalityBranchLength = FINALITY_BRANCH_LENGTH_DEFAULT;
    }
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    rawConfig.accept("ALTAIR_FORK_EPOCH", altairForkEpoch);
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

  public AltairBuilder updateTimeout(final Integer updateTimeout) {
    checkNotNull(updateTimeout);
    this.updateTimeout = updateTimeout;
    return this;
  }

  public AltairBuilder syncCommitteeBranchLength(final Integer syncCommitteeBranchLength) {
    checkNotNull(syncCommitteeBranchLength);
    this.syncCommitteeBranchLength = syncCommitteeBranchLength;
    return this;
  }

  public AltairBuilder finalityBranchLength(final Integer finalityBranchLength) {
    checkNotNull(finalityBranchLength);
    this.finalityBranchLength = finalityBranchLength;
    return this;
  }
}
