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

public class SpecConfigEip7805Impl extends DelegatingSpecConfigElectra
    implements SpecConfigEip7805 {

  private final Bytes4 eip7805ForkVersion;
  private final UInt64 eip7805ForkEpoch;
  private final int inclusionListCommitteeSize;
  private final int maxRequestInclusionList;
  private final int maxBytesPerInclusionList;
  private final int attestationDeadline;
  private final int proposerInclusionListCutOff;
  private final int viewFreezeDeadline;

  public SpecConfigEip7805Impl(
      final SpecConfigElectra specConfig,
      final Bytes4 eip7805ForkVersion,
      final UInt64 eip7805ForkEpoch,
      final int inclusionListCommitteeSize,
      final int maxRequestInclusionList,
      final int maxBytesPerInclusionList,
      final int attestationDeadline,
      final int proposerInclusionListCutOff,
      final int viewFreezeDeadline) {
    super(specConfig);
    this.eip7805ForkVersion = eip7805ForkVersion;
    this.eip7805ForkEpoch = eip7805ForkEpoch;
    this.inclusionListCommitteeSize = inclusionListCommitteeSize;
    this.maxRequestInclusionList = maxRequestInclusionList;
    this.maxBytesPerInclusionList = maxBytesPerInclusionList;
    this.attestationDeadline = attestationDeadline;
    this.proposerInclusionListCutOff = proposerInclusionListCutOff;
    this.viewFreezeDeadline = viewFreezeDeadline;
  }

  @Override
  public Bytes4 getEip7805ForkVersion() {
    return eip7805ForkVersion;
  }

  @Override
  public UInt64 getEip7805ForkEpoch() {
    return eip7805ForkEpoch;
  }

  @Override
  public int getInclusionListCommitteeSize() {
    return inclusionListCommitteeSize;
  }

  @Override
  public int getMaxRequestInclusionList() {
    return maxRequestInclusionList;
  }

  @Override
  public int getMaxBytesPerInclusionList() {
    return maxBytesPerInclusionList;
  }

  @Override
  public int getAttestationDeadLine() {
    return attestationDeadline;
  }

  @Override
  public int getProposerInclusionListCutOff() {
    return proposerInclusionListCutOff;
  }

  @Override
  public int getViewFreezeDeadline() {
    return viewFreezeDeadline;
  }

  @Override
  public Optional<SpecConfigEip7805> toVersionEip7805() {
    return Optional.of(this);
  }

  @Override
  public SpecMilestone getMilestone() {
    return SpecMilestone.EIP7805;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SpecConfigEip7805Impl that = (SpecConfigEip7805Impl) o;
    return Objects.equals(specConfig, that.specConfig)
        && Objects.equals(eip7805ForkVersion, that.eip7805ForkVersion)
        && Objects.equals(eip7805ForkEpoch, that.eip7805ForkEpoch)
        && inclusionListCommitteeSize == that.inclusionListCommitteeSize
        && maxRequestInclusionList == that.maxRequestInclusionList
        && maxBytesPerInclusionList == that.maxBytesPerInclusionList
        && attestationDeadline == that.attestationDeadline
        && proposerInclusionListCutOff == that.proposerInclusionListCutOff
        && viewFreezeDeadline == that.viewFreezeDeadline;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
        eip7805ForkVersion,
        eip7805ForkEpoch,
        inclusionListCommitteeSize,
        maxRequestInclusionList,
        maxBytesPerInclusionList,
        attestationDeadline,
        proposerInclusionListCutOff,
        viewFreezeDeadline);
  }
}
