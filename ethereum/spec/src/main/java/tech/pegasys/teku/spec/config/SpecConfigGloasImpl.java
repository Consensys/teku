/*
 * Copyright Consensys Software Inc., 2025
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

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.spec.SpecMilestone;

public class SpecConfigGloasImpl extends DelegatingSpecConfigFulu implements SpecConfigGloas {
  private final int aggregateDueBps;
  private final int attestationDueBps;
  private final int contributionDueBps;
  private final long builderPendingWithdrawalsLimit;
  private final int maxPayloadAttestations;
  private final int maxRequestPayloads;
  private final int payloadAttestationDueBps;
  private final int ptcSize;
  private final int syncMessageDueBps;

  public SpecConfigGloasImpl(
      final SpecConfigFulu specConfig,
      final int aggregateDueBps,
      final int attestationDueBps,
      final int contributionDueBps,
      final long builderPendingWithdrawalsLimit,
      final int maxPayloadAttestations,
      final int maxRequestPayloads,
      final int payloadAttestationDueBps,
      final int ptcSize,
      final int syncMessageDueBps) {
    super(specConfig);
    this.aggregateDueBps = aggregateDueBps;
    this.attestationDueBps = attestationDueBps;
    this.contributionDueBps = contributionDueBps;
    this.builderPendingWithdrawalsLimit = builderPendingWithdrawalsLimit;
    this.maxPayloadAttestations = maxPayloadAttestations;
    this.maxRequestPayloads = maxRequestPayloads;
    this.ptcSize = ptcSize;
    this.payloadAttestationDueBps = payloadAttestationDueBps;
    this.syncMessageDueBps = syncMessageDueBps;
  }

  @Override
  public int getAggregateDueBps() {
    return aggregateDueBps;
  }

  @Override
  public int getAttestationDueBps() {
    return attestationDueBps;
  }

  @Override
  public int getContributionDueBps() {
    return contributionDueBps;
  }

  @Override
  public int getMaxRequestPayloads() {
    return maxRequestPayloads;
  }

  @Override
  public int getPayloadAttestationDueBps() {
    return payloadAttestationDueBps;
  }

  @Override
  public int getPtcSize() {
    return ptcSize;
  }

  @Override
  public int getMaxPayloadAttestations() {
    return maxPayloadAttestations;
  }

  @Override
  public long getBuilderPendingWithdrawalsLimit() {
    return builderPendingWithdrawalsLimit;
  }

  @Override
  public int getSyncMessageDueBps() {
    return syncMessageDueBps;
  }

  @Override
  public SpecMilestone getMilestone() {
    return SpecMilestone.GLOAS;
  }

  @Override
  public Optional<SpecConfigGloas> toVersionGloas() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    SpecConfigGloasImpl that = (SpecConfigGloasImpl) o;
    return aggregateDueBps == that.aggregateDueBps
        && attestationDueBps == that.attestationDueBps
        && contributionDueBps == that.contributionDueBps
        && maxPayloadAttestations == that.maxPayloadAttestations
        && maxRequestPayloads == that.maxRequestPayloads
        && payloadAttestationDueBps == that.payloadAttestationDueBps
        && ptcSize == that.ptcSize
        && syncMessageDueBps == that.syncMessageDueBps
        && builderPendingWithdrawalsLimit == that.builderPendingWithdrawalsLimit;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        aggregateDueBps,
        attestationDueBps,
        contributionDueBps,
        builderPendingWithdrawalsLimit,
        maxPayloadAttestations,
        maxRequestPayloads,
        payloadAttestationDueBps,
        ptcSize,
        syncMessageDueBps);
  }
}
