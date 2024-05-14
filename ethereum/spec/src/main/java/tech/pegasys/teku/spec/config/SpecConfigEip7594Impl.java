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

public class SpecConfigEip7594Impl extends DelegatingSpecConfigDeneb implements SpecConfigEip7594 {

  private final Bytes4 eip7594ForkVersion;
  private final UInt64 eip7594ForkEpoch;

  private final int numberOfColumns;
  private final int dataColumnSidecarSubnetCount;
  private final int custodyRequirement;
  private final UInt64 fieldElementsPerCell;
  private final UInt64 fieldElementsPerExtBlob;
  private final UInt64 kzgCommitmentsInclusionProofDepth;
  private final int minEpochsForDataColumnSidecarsRequests;
  private final int maxRequestDataColumnSidecars;

  public SpecConfigEip7594Impl(
      final SpecConfigDeneb specConfig,
      final Bytes4 eip7594ForkVersion,
      final UInt64 eip7594ForkEpoch,
      final UInt64 fieldElementsPerCell,
      final UInt64 fieldElementsPerExtBlob,
      final UInt64 kzgCommitmentsInclusionProofDepth,
      final int numberOfColumns,
      final int dataColumnSidecarSubnetCount,
      final int custodyRequirement,
      final int minEpochsForDataColumnSidecarsRequests,
      final int maxRequestDataColumnSidecars) {
    super(specConfig);
    this.eip7594ForkVersion = eip7594ForkVersion;
    this.eip7594ForkEpoch = eip7594ForkEpoch;
    this.fieldElementsPerCell = fieldElementsPerCell;
    this.fieldElementsPerExtBlob = fieldElementsPerExtBlob;
    this.kzgCommitmentsInclusionProofDepth = kzgCommitmentsInclusionProofDepth;
    this.numberOfColumns = numberOfColumns;
    this.dataColumnSidecarSubnetCount = dataColumnSidecarSubnetCount;
    this.custodyRequirement = custodyRequirement;
    this.minEpochsForDataColumnSidecarsRequests = minEpochsForDataColumnSidecarsRequests;
    this.maxRequestDataColumnSidecars = maxRequestDataColumnSidecars;
  }

  @Override
  public Bytes4 getEip7594ForkVersion() {
    return eip7594ForkVersion;
  }

  @Override
  public UInt64 getEip7594ForkEpoch() {
    return eip7594ForkEpoch;
  }

  @Override
  public UInt64 getFieldElementsPerCell() {
    return fieldElementsPerCell;
  }

  @Override
  public UInt64 getFieldElementsPerExtBlob() {
    return fieldElementsPerExtBlob;
  }

  @Override
  public UInt64 getKzgCommitmentsInclusionProofDepth() {
    return kzgCommitmentsInclusionProofDepth;
  }

  @Override
  public int getNumberOfColumns() {
    return numberOfColumns;
  }

  @Override
  public int getDataColumnSidecarSubnetCount() {
    return dataColumnSidecarSubnetCount;
  }

  @Override
  public int getCustodyRequirement() {
    return custodyRequirement;
  }

  @Override
  public int getMinEpochsForDataColumnSidecarsRequests() {
    return minEpochsForDataColumnSidecarsRequests;
  }

  @Override
  public int getMaxRequestDataColumnSidecars() {
    return maxRequestDataColumnSidecars;
  }

  @Override
  public Optional<SpecConfigEip7594> toVersionEip7594() {
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
    final SpecConfigEip7594Impl that = (SpecConfigEip7594Impl) o;
    return Objects.equals(specConfig, that.specConfig)
        && Objects.equals(eip7594ForkVersion, that.eip7594ForkVersion)
        && Objects.equals(eip7594ForkEpoch, that.eip7594ForkEpoch)
        && Objects.equals(fieldElementsPerCell, that.fieldElementsPerCell)
        && Objects.equals(fieldElementsPerExtBlob, that.fieldElementsPerExtBlob)
        && Objects.equals(kzgCommitmentsInclusionProofDepth, that.kzgCommitmentsInclusionProofDepth)
        && numberOfColumns == that.numberOfColumns
        && dataColumnSidecarSubnetCount == that.dataColumnSidecarSubnetCount
        && custodyRequirement == that.custodyRequirement
        && minEpochsForDataColumnSidecarsRequests == that.minEpochsForDataColumnSidecarsRequests
        && maxRequestDataColumnSidecars == that.maxRequestDataColumnSidecars;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
        eip7594ForkVersion,
        eip7594ForkEpoch,
        numberOfColumns,
        dataColumnSidecarSubnetCount,
        custodyRequirement,
        fieldElementsPerCell,
        fieldElementsPerExtBlob,
        kzgCommitmentsInclusionProofDepth,
        minEpochsForDataColumnSidecarsRequests,
        maxRequestDataColumnSidecars);
  }
}
