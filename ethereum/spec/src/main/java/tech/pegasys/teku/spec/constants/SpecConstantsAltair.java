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
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class SpecConstantsAltair extends DelegatingSpecConstants {
  private final UInt64 altairInactivityPenaltyQuotient;
  private final Integer altairMinSlashingPenaltyQuotient;
  private final Integer altairProportionalSlashingMultiplier;

  // Sync committees
  private final Integer syncCommitteeSize;
  private final Integer syncSubcommitteeSize;

  // Time
  private final Integer epochsPerSyncCommitteePeriod;

  // Signature domains
  private Bytes4 domainSyncCommittee;

  public SpecConstantsAltair(
      final SpecConstants specConstants,
      final UInt64 altairInactivityPenaltyQuotient,
      final Integer altairMinSlashingPenaltyQuotient,
      final Integer altairProportionalSlashingMultiplier,
      final Integer syncCommitteeSize,
      final Integer syncSubcommitteeSize,
      final Integer epochsPerSyncCommitteePeriod,
      final Bytes4 domainSyncCommittee) {
    super(specConstants);
    this.altairInactivityPenaltyQuotient = altairInactivityPenaltyQuotient;
    this.altairMinSlashingPenaltyQuotient = altairMinSlashingPenaltyQuotient;
    this.altairProportionalSlashingMultiplier = altairProportionalSlashingMultiplier;
    this.syncCommitteeSize = syncCommitteeSize;
    this.syncSubcommitteeSize = syncSubcommitteeSize;
    this.epochsPerSyncCommitteePeriod = epochsPerSyncCommitteePeriod;
    this.domainSyncCommittee = domainSyncCommittee;
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

  public UInt64 getAltairInactivityPenaltyQuotient() {
    return altairInactivityPenaltyQuotient;
  }

  public Integer getAltairMinSlashingPenaltyQuotient() {
    return altairMinSlashingPenaltyQuotient;
  }

  public Integer getAltairProportionalSlashingMultiplier() {
    return altairProportionalSlashingMultiplier;
  }

  public Integer getSyncCommitteeSize() {
    return syncCommitteeSize;
  }

  public Integer getSyncSubcommitteeSize() {
    return syncSubcommitteeSize;
  }

  public Integer getEpochsPerSyncCommitteePeriod() {
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
        && Objects.equals(altairInactivityPenaltyQuotient, that.altairInactivityPenaltyQuotient)
        && Objects.equals(altairMinSlashingPenaltyQuotient, that.altairMinSlashingPenaltyQuotient)
        && Objects.equals(
            altairProportionalSlashingMultiplier, that.altairProportionalSlashingMultiplier)
        && Objects.equals(syncCommitteeSize, that.syncCommitteeSize)
        && Objects.equals(syncSubcommitteeSize, that.syncSubcommitteeSize)
        && Objects.equals(epochsPerSyncCommitteePeriod, that.epochsPerSyncCommitteePeriod)
        && Objects.equals(domainSyncCommittee, that.domainSyncCommittee);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConstants,
        altairInactivityPenaltyQuotient,
        altairMinSlashingPenaltyQuotient,
        altairProportionalSlashingMultiplier,
        syncCommitteeSize,
        syncSubcommitteeSize,
        epochsPerSyncCommitteePeriod,
        domainSyncCommittee);
  }
}
