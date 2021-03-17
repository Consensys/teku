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

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.type.Bytes4;

public class SpecConstantsAltair extends DelegatingSpecConstants {
  // Updated penalties
  private final UInt64 inactivityPenaltyQuotientAltair;
  private final int minSlashingPenaltyQuotientAltair;
  private final int proportionalSlashingMultiplierAltair;

  // Misc
  private final int syncCommitteeSize;
  private final int syncSubcommitteeSize;
  private final int inactivityScoreBias;

  // Time
  private final int epochsPerSyncCommitteePeriod;

  // Signature domains
  private final Bytes4 domainSyncCommittee;

  // Fork
  private final Bytes4 altairForkVersion;

  // Sync protocol
  private final int minSyncCommitteeParticipants;
  private final int maxValidLightClientUpdates;
  private final int lightClientUpdateTimeout;

  public SpecConstantsAltair(
      final SpecConstants specConstants,
      final UInt64 inactivityPenaltyQuotientAltair,
      final int altairMinSlashingPenaltyQuotient,
      final int proportionalSlashingMultiplierAltair,
      final int syncCommitteeSize,
      final int syncSubcommitteeSize,
      final int inactivityScoreBias,
      final int epochsPerSyncCommitteePeriod,
      final Bytes4 domainSyncCommittee,
      final Bytes4 altairForkVersion,
      final int minSyncCommitteeParticipants,
      final int maxValidLightClientUpdates,
      final int lightClientUpdateTimeout) {
    super(specConstants);
    this.inactivityPenaltyQuotientAltair = inactivityPenaltyQuotientAltair;
    this.minSlashingPenaltyQuotientAltair = altairMinSlashingPenaltyQuotient;
    this.proportionalSlashingMultiplierAltair = proportionalSlashingMultiplierAltair;
    this.syncCommitteeSize = syncCommitteeSize;
    this.syncSubcommitteeSize = syncSubcommitteeSize;
    this.inactivityScoreBias = inactivityScoreBias;
    this.epochsPerSyncCommitteePeriod = epochsPerSyncCommitteePeriod;
    this.domainSyncCommittee = domainSyncCommittee;
    this.altairForkVersion = altairForkVersion;
    this.minSyncCommitteeParticipants = minSyncCommitteeParticipants;
    this.maxValidLightClientUpdates = maxValidLightClientUpdates;
    this.lightClientUpdateTimeout = lightClientUpdateTimeout;
  }

  public static SpecConstantsAltair required(final SpecConstants specConstants) {
    return specConstants
        .toVersionAltair()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected altair constants but got: "
                        + specConstants.getClass().getSimpleName()));
  }

  public UInt64 getInactivityPenaltyQuotientAltair() {
    return inactivityPenaltyQuotientAltair;
  }

  public int getMinSlashingPenaltyQuotientAltair() {
    return minSlashingPenaltyQuotientAltair;
  }

  public int getProportionalSlashingMultiplierAltair() {
    return proportionalSlashingMultiplierAltair;
  }

  public int getSyncCommitteeSize() {
    return syncCommitteeSize;
  }

  public int getSyncSubcommitteeSize() {
    return syncSubcommitteeSize;
  }

  public int getEpochsPerSyncCommitteePeriod() {
    return epochsPerSyncCommitteePeriod;
  }

  public Bytes4 getDomainSyncCommittee() {
    return domainSyncCommittee;
  }

  @Override
  public Optional<SpecConstantsAltair> toVersionAltair() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SpecConstantsAltair that = (SpecConstantsAltair) o;
    return Objects.equals(specConstants, that.specConstants)
        && minSlashingPenaltyQuotientAltair == that.minSlashingPenaltyQuotientAltair
        && proportionalSlashingMultiplierAltair == that.proportionalSlashingMultiplierAltair
        && syncCommitteeSize == that.syncCommitteeSize
        && syncSubcommitteeSize == that.syncSubcommitteeSize
        && inactivityScoreBias == that.inactivityScoreBias
        && epochsPerSyncCommitteePeriod == that.epochsPerSyncCommitteePeriod
        && minSyncCommitteeParticipants == that.minSyncCommitteeParticipants
        && maxValidLightClientUpdates == that.maxValidLightClientUpdates
        && lightClientUpdateTimeout == that.lightClientUpdateTimeout
        && Objects.equals(inactivityPenaltyQuotientAltair, that.inactivityPenaltyQuotientAltair)
        && Objects.equals(domainSyncCommittee, that.domainSyncCommittee)
        && Objects.equals(altairForkVersion, that.altairForkVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConstants,
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
}
