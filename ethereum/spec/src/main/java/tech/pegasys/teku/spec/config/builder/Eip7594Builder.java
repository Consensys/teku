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

package tech.pegasys.teku.spec.config.builder;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.config.SpecConfigEip7594Impl;

public class Eip7594Builder implements ForkConfigBuilder<SpecConfigDeneb, SpecConfigEip7594> {

  private Bytes4 eip7594ForkVersion;
  private UInt64 eip7594ForkEpoch;
  private UInt64 fieldElementsPerCell;
  private UInt64 fieldElementsPerExtBlob;
  private UInt64 kzgCommitmentsInclusionProofDepth;
  private Integer numberOfColumns;
  private Integer dataColumnSidecarSubnetCount;
  private Integer custodyRequirement;
  private Integer minEpochsForDataColumnSidecarsRequests;
  private Integer maxRequestDataColumnSidecars;

  Eip7594Builder() {}

  @Override
  public SpecConfigEip7594 build(final SpecConfigDeneb specConfig) {
    return new SpecConfigEip7594Impl(
        specConfig,
        eip7594ForkVersion,
        eip7594ForkEpoch,
        fieldElementsPerCell,
        fieldElementsPerExtBlob,
        kzgCommitmentsInclusionProofDepth,
        numberOfColumns,
        dataColumnSidecarSubnetCount,
        custodyRequirement,
        minEpochsForDataColumnSidecarsRequests,
        maxRequestDataColumnSidecars);
  }

  public Eip7594Builder eip7594ForkEpoch(final UInt64 eip7594ForkEpoch) {
    checkNotNull(eip7594ForkEpoch);
    this.eip7594ForkEpoch = eip7594ForkEpoch;
    return this;
  }

  public Eip7594Builder eip7594ForkVersion(final Bytes4 eip7594ForkVersion) {
    checkNotNull(eip7594ForkVersion);
    this.eip7594ForkVersion = eip7594ForkVersion;
    return this;
  }

  public Eip7594Builder fieldElementsPerCell(final UInt64 fieldElementsPerCell) {
    checkNotNull(fieldElementsPerCell);
    this.fieldElementsPerCell = fieldElementsPerCell;
    return this;
  }

  public Eip7594Builder fieldElementsPerExtBlob(final UInt64 fieldElementsPerExtBlob) {
    checkNotNull(fieldElementsPerExtBlob);
    this.fieldElementsPerExtBlob = fieldElementsPerExtBlob;
    return this;
  }

  public Eip7594Builder kzgCommitmentsInclusionProofDepth(
      final UInt64 kzgCommitmentsInclusionProofDepth) {
    checkNotNull(kzgCommitmentsInclusionProofDepth);
    this.kzgCommitmentsInclusionProofDepth = kzgCommitmentsInclusionProofDepth;
    return this;
  }

  public Eip7594Builder numberOfColumns(final Integer numberOfColumns) {
    this.numberOfColumns = numberOfColumns;
    return this;
  }

  public Eip7594Builder dataColumnSidecarSubnetCount(final Integer blobSidecarSubnetCount) {
    this.dataColumnSidecarSubnetCount = blobSidecarSubnetCount;
    return this;
  }

  public Eip7594Builder custodyRequirement(final Integer custodyRequirement) {
    checkNotNull(custodyRequirement);
    this.custodyRequirement = custodyRequirement;
    return this;
  }

  public Eip7594Builder minEpochsForDataColumnSidecarsRequests(final Integer custodyEpochs) {
    checkNotNull(custodyEpochs);
    this.minEpochsForDataColumnSidecarsRequests = custodyEpochs;
    return this;
  }

  public Eip7594Builder maxRequestDataColumnSidecars(final Integer maxRequestDataColumnSidecars) {
    checkNotNull(maxRequestDataColumnSidecars);
    this.maxRequestDataColumnSidecars = maxRequestDataColumnSidecars;
    return this;
  }

  @Override
  public void validate() {
    if (eip7594ForkEpoch == null) {
      eip7594ForkEpoch = SpecConfig.FAR_FUTURE_EPOCH;
      eip7594ForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }

    // Fill default zeros if fork is unsupported
    if (eip7594ForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      SpecBuilderUtil.fillMissingValuesWithZeros(this);
    }

    validateConstants();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();

    constants.put("eip7594ForkEpoch", eip7594ForkEpoch);
    constants.put("eip7594ForkVersion", eip7594ForkVersion);
    constants.put("numberOfColumns", numberOfColumns);
    constants.put("dataColumnSidecarSubnetCount", dataColumnSidecarSubnetCount);
    constants.put("custodyRequirement", custodyRequirement);
    constants.put("fieldElementsPerCell", fieldElementsPerCell);
    constants.put("fieldElementsPerExtBlob", fieldElementsPerExtBlob);
    constants.put("kzgCommitmentsInclusionProofDepth", kzgCommitmentsInclusionProofDepth);
    constants.put("minEpochsForDataColumnSidecarsRequests", minEpochsForDataColumnSidecarsRequests);
    constants.put("maxRequestDataColumnSidecars", maxRequestDataColumnSidecars);

    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    rawConfig.accept("EIP7594_FORK_EPOCH", eip7594ForkEpoch);
  }
}
