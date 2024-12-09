/*
 * Copyright Consensys Software Inc., 2024
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
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;

public class SpecConfigEip7732Impl extends DelegatingSpecConfigElectra
    implements SpecConfigEip7732 {

  private final Bytes4 eip7732ForkVersion;
  private final UInt64 eip7732ForkEpoch;

  private final int ptcSize;
  private final int maxPayloadAttestations;
  private final int kzgCommitmentInclusionProofDepthEip7732;
  private final int maxRequestPayloads;

  public SpecConfigEip7732Impl(
      final SpecConfigElectra specConfig,
      final Bytes4 eip7732ForkVersion,
      final UInt64 eip7732ForkEpoch,
      final int ptcSize,
      final int maxPayloadAttestations,
      final int kzgCommitmentInclusionProofDepthEip7732,
      final int maxRequestPayloads) {
    super(specConfig);
    this.eip7732ForkVersion = eip7732ForkVersion;
    this.eip7732ForkEpoch = eip7732ForkEpoch;
    this.ptcSize = ptcSize;
    this.maxPayloadAttestations = maxPayloadAttestations;
    this.kzgCommitmentInclusionProofDepthEip7732 = kzgCommitmentInclusionProofDepthEip7732;
    this.maxRequestPayloads = maxRequestPayloads;
  }

  @Override
  public Bytes4 getEip7732ForkVersion() {
    return eip7732ForkVersion;
  }

  @Override
  public UInt64 getEip7732ForkEpoch() {
    return eip7732ForkEpoch;
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
  public int getKzgCommitmentInclusionProofDepthEip7732() {
    return kzgCommitmentInclusionProofDepthEip7732;
  }

  @Override
  public int getMaxRequestPayloads() {
    return maxRequestPayloads;
  }

  @Override
  public Optional<SpecConfigEip7732> toVersionEip7732() {
    return Optional.of(this);
  }

  @Override
  public SpecMilestone getMilestone() {
    return SpecMilestone.EIP7732;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SpecConfigEip7732Impl that = (SpecConfigEip7732Impl) o;
    return Objects.equals(specConfig, that.specConfig)
        && Objects.equals(eip7732ForkVersion, that.eip7732ForkVersion)
        && Objects.equals(eip7732ForkEpoch, that.eip7732ForkEpoch)
        && ptcSize == that.ptcSize
        && maxPayloadAttestations == that.maxPayloadAttestations
        && kzgCommitmentInclusionProofDepthEip7732 == that.kzgCommitmentInclusionProofDepthEip7732
        && maxRequestPayloads == that.maxRequestPayloads;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
        eip7732ForkVersion,
        eip7732ForkEpoch,
        ptcSize,
        maxPayloadAttestations,
        kzgCommitmentInclusionProofDepthEip7732,
        maxRequestPayloads);
  }
}
