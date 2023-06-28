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
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.Optional;
import java.util.function.BiConsumer;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigDenebImpl;

public class DenebBuilder implements ForkConfigBuilder<SpecConfigCapella, SpecConfigDeneb> {

  private Bytes4 denebForkVersion;
  private UInt64 denebForkEpoch;

  private Integer fieldElementsPerBlob;
  private Integer maxBlobCommitmentsPerBlock;
  private Integer maxBlobsPerBlock;

  private Optional<String> trustedSetupPath = Optional.empty();
  private Boolean kzgNoop = false;

  private Integer maxRequestBlocksDeneb;
  private Integer maxRequestBlobSidecars;
  private Integer minEpochsForBlobSidecarsRequests;
  private Integer blobSidecarSubnetCount;

  DenebBuilder() {}

  @Override
  public SpecConfigDeneb build(final SpecConfigCapella specConfig) {
    return new SpecConfigDenebImpl(
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

  public DenebBuilder denebForkEpoch(final UInt64 denebForkEpoch) {
    checkNotNull(denebForkEpoch);
    this.denebForkEpoch = denebForkEpoch;
    return this;
  }

  public DenebBuilder denebForkVersion(final Bytes4 denebForkVersion) {
    checkNotNull(denebForkVersion);
    this.denebForkVersion = denebForkVersion;
    return this;
  }

  public DenebBuilder fieldElementsPerBlob(final Integer fieldElementsPerBlob) {
    this.fieldElementsPerBlob = fieldElementsPerBlob;
    return this;
  }

  public DenebBuilder maxBlobCommitmentsPerBlock(final Integer maxBlobCommitmentsPerBlock) {
    this.maxBlobCommitmentsPerBlock = maxBlobCommitmentsPerBlock;
    return this;
  }

  public DenebBuilder maxBlobsPerBlock(final Integer maxBlobsPerBlock) {
    this.maxBlobsPerBlock = maxBlobsPerBlock;
    return this;
  }

  public DenebBuilder trustedSetupPath(final String trustedSetupPath) {
    this.trustedSetupPath = Optional.of(trustedSetupPath);
    return this;
  }

  public DenebBuilder kzgNoop(final Boolean kzgNoop) {
    this.kzgNoop = kzgNoop;
    return this;
  }

  public DenebBuilder maxRequestBlocksDeneb(final Integer maxRequestBlocksDeneb) {
    this.maxRequestBlocksDeneb = maxRequestBlocksDeneb;
    return this;
  }

  public DenebBuilder maxRequestBlobSidecars(final Integer maxRequestBlobSidecars) {
    this.maxRequestBlobSidecars = maxRequestBlobSidecars;
    return this;
  }

  public DenebBuilder minEpochsForBlobSidecarsRequests(
      final Integer minEpochsForBlobSidecarsRequests) {
    this.minEpochsForBlobSidecarsRequests = minEpochsForBlobSidecarsRequests;
    return this;
  }

  public DenebBuilder blobSidecarSubnetCount(final Integer blobSidecarSubnetCount) {
    this.blobSidecarSubnetCount = blobSidecarSubnetCount;
    return this;
  }

  @Override
  public void validate() {
    if (denebForkEpoch == null) {
      denebForkEpoch = SpecConfig.FAR_FUTURE_EPOCH;
      denebForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }

    // Fill default zeros if fork is unsupported
    if (denebForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      SpecBuilderUtil.fillMissingValuesWithZeros(this);
    }

    SpecBuilderUtil.validateConstant("denebForkEpoch", denebForkEpoch);
    SpecBuilderUtil.validateConstant("denebForkVersion", denebForkVersion);
    SpecBuilderUtil.validateConstant("fieldElementsPerBlob", fieldElementsPerBlob);
    SpecBuilderUtil.validateConstant("maxBlobCommitmentsPerBlock", maxBlobCommitmentsPerBlock);
    SpecBuilderUtil.validateConstant("maxBlobsPerBlock", maxBlobsPerBlock);
    SpecBuilderUtil.validateConstant("maxRequestBlocksDeneb", maxRequestBlocksDeneb);
    SpecBuilderUtil.validateConstant("maxRequestBlobSidecars", maxRequestBlobSidecars);
    SpecBuilderUtil.validateConstant(
        "minEpochsForBlobSidecarsRequests", minEpochsForBlobSidecarsRequests);
    SpecBuilderUtil.validateConstant("blobSidecarSubnetCount", blobSidecarSubnetCount);
    if (!denebForkEpoch.equals(SpecConfig.FAR_FUTURE_EPOCH) && !kzgNoop) {
      SpecBuilderUtil.validateRequiredOptional("trustedSetupPath", trustedSetupPath);
    }
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    rawConfig.accept("DENEB_FORK_EPOCH", denebForkEpoch);
  }
}
