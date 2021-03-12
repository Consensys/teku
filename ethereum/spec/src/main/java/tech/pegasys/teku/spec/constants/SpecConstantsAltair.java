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
}
