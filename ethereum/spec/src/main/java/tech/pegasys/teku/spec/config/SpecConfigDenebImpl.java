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

package tech.pegasys.teku.spec.config;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SpecConfigDenebImpl extends DelegatingSpecConfigCapella implements SpecConfigDeneb {

  private final Bytes4 denebForkVersion;
  private final UInt64 denebForkEpoch;

  private final int fieldElementsPerBlob;
  private final int maxBlobCommitmentsPerBlock;
  private final int maxBlobsPerBlock;
  private final Optional<String> trustedSetupPath;
  private final boolean kzgNoop;
  private final int maxRequestBlocksDeneb;
  private final int maxRequestBlobSidecars;
  private final int minEpochsForBlobSidecarsRequests;
  private final int blobSidecarSubnetCount;

  public SpecConfigDenebImpl(
      final SpecConfigCapella specConfig,
      final Bytes4 denebForkVersion,
      final UInt64 denebForkEpoch,
      final int fieldElementsPerBlob,
      final int maxBlobCommitmentsPerBlock,
      final int maxBlobsPerBlock,
      final Optional<String> trustedSetupPath,
      final boolean kzgNoop,
      final int maxRequestBlocksDeneb,
      final int maxRequestBlobSidecars,
      final int minEpochsForBlobSidecarsRequests,
      final int blobSidecarSubnetCount) {
    super(specConfig);
    this.denebForkVersion = denebForkVersion;
    this.denebForkEpoch = denebForkEpoch;
    this.fieldElementsPerBlob = fieldElementsPerBlob;
    this.maxBlobCommitmentsPerBlock = maxBlobCommitmentsPerBlock;
    this.maxBlobsPerBlock = maxBlobsPerBlock;
    this.trustedSetupPath = trustedSetupPath;
    this.kzgNoop = kzgNoop;
    this.maxRequestBlocksDeneb = maxRequestBlocksDeneb;
    this.maxRequestBlobSidecars = maxRequestBlobSidecars;
    this.minEpochsForBlobSidecarsRequests = minEpochsForBlobSidecarsRequests;
    this.blobSidecarSubnetCount = blobSidecarSubnetCount;
  }

  @Override
  public Bytes4 getDenebForkVersion() {
    return denebForkVersion;
  }

  @Override
  public UInt64 getDenebForkEpoch() {
    return denebForkEpoch;
  }

  @Override
  public int getFieldElementsPerBlob() {
    return fieldElementsPerBlob;
  }

  @Override
  public int getMaxBlobCommitmentsPerBlock() {
    return maxBlobCommitmentsPerBlock;
  }

  @Override
  public int getMaxBlobsPerBlock() {
    return maxBlobsPerBlock;
  }

  @Override
  public Optional<String> getTrustedSetupPath() {
    return trustedSetupPath;
  }

  @Override
  public boolean isKZGNoop() {
    return kzgNoop;
  }

  @Override
  public int getMaxRequestBlocksDeneb() {
    return maxRequestBlocksDeneb;
  }

  @Override
  public int getMaxRequestBlobSidecars() {
    return maxRequestBlobSidecars;
  }

  @Override
  public int getMinEpochsForBlobSidecarsRequests() {
    return minEpochsForBlobSidecarsRequests;
  }

  @Override
  public int getBlobSidecarSubnetCount() {
    return blobSidecarSubnetCount;
  }

  @Override
  public Optional<SpecConfigDeneb> toVersionDeneb() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SpecConfigDenebImpl that = (SpecConfigDenebImpl) o;
    return Objects.equals(specConfig, that.specConfig)
        && Objects.equals(denebForkVersion, that.denebForkVersion)
        && Objects.equals(denebForkEpoch, that.denebForkEpoch)
        && fieldElementsPerBlob == that.fieldElementsPerBlob
        && maxBlobCommitmentsPerBlock == that.maxBlobCommitmentsPerBlock
        && maxBlobsPerBlock == that.maxBlobsPerBlock
        && Objects.equals(trustedSetupPath, that.trustedSetupPath)
        && kzgNoop == that.kzgNoop
        && maxRequestBlocksDeneb == that.maxRequestBlocksDeneb
        && maxRequestBlobSidecars == that.maxRequestBlobSidecars
        && minEpochsForBlobSidecarsRequests == that.minEpochsForBlobSidecarsRequests
        && blobSidecarSubnetCount == that.blobSidecarSubnetCount;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
        denebForkVersion,
        denebForkEpoch,
        fieldElementsPerBlob,
        maxBlobCommitmentsPerBlock,
        maxBlobsPerBlock,
        trustedSetupPath,
        kzgNoop,
        maxRequestBlocksDeneb,
        maxRequestBlobSidecars,
        minEpochsForBlobSidecarsRequests,
        blobSidecarSubnetCount);
  }
}
