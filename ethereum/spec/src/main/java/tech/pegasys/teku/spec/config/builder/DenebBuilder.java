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

package tech.pegasys.teku.spec.config.builder;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigDenebImpl;

public class DenebBuilder extends BaseForkBuilder
    implements ForkConfigBuilder<SpecConfigCapella, SpecConfigDeneb> {

  private Integer maxPerEpochActivationChurnLimit;
  private Integer fieldElementsPerBlob;
  private Integer maxBlobCommitmentsPerBlock;
  private Integer maxBlobsPerBlock;
  private Integer kzgCommitmentInclusionProofDepth;
  private Integer maxRequestBlocksDeneb;
  private Integer maxRequestBlobSidecars;
  private Integer minEpochsForBlobSidecarsRequests;
  private Integer blobSidecarSubnetCount;
  private Optional<Integer> epochsStoreBlobs = Optional.empty();

  DenebBuilder() {}

  @Override
  public SpecConfigAndParent<SpecConfigDeneb> build(
      final SpecConfigAndParent<SpecConfigCapella> specConfigAndParent) {
    return SpecConfigAndParent.of(
        new SpecConfigDenebImpl(
            specConfigAndParent.specConfig(),
            maxPerEpochActivationChurnLimit,
            fieldElementsPerBlob,
            maxBlobCommitmentsPerBlock,
            maxBlobsPerBlock,
            kzgCommitmentInclusionProofDepth,
            maxRequestBlocksDeneb,
            maxRequestBlobSidecars,
            minEpochsForBlobSidecarsRequests,
            blobSidecarSubnetCount,
            epochsStoreBlobs),
        specConfigAndParent);
  }

  public DenebBuilder maxPerEpochActivationChurnLimit(
      final Integer maxPerEpochActivationChurnLimit) {
    checkNotNull(maxPerEpochActivationChurnLimit);
    this.maxPerEpochActivationChurnLimit = maxPerEpochActivationChurnLimit;
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

  public DenebBuilder kzgCommitmentInclusionProofDepth(
      final Integer kzgCommitmentInclusionProofDepth) {
    this.kzgCommitmentInclusionProofDepth = kzgCommitmentInclusionProofDepth;
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

  public DenebBuilder epochsStoreBlobs(final Optional<Integer> epochsStoreBlobs) {
    this.epochsStoreBlobs = epochsStoreBlobs;
    return this;
  }

  @Override
  public void validate() {
    defaultValuesIfRequired(this);
    validateConstants();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("maxPerEpochActivationChurnLimit", maxPerEpochActivationChurnLimit);
    constants.put("fieldElementsPerBlob", fieldElementsPerBlob);
    constants.put("maxBlobCommitmentsPerBlock", maxBlobCommitmentsPerBlock);
    constants.put("maxBlobsPerBlock", maxBlobsPerBlock);
    constants.put("kzgCommitmentInclusionProofDepth", kzgCommitmentInclusionProofDepth);
    constants.put("maxRequestBlocksDeneb", maxRequestBlocksDeneb);
    constants.put("maxRequestBlobSidecars", maxRequestBlobSidecars);
    constants.put("minEpochsForBlobSidecarsRequests", minEpochsForBlobSidecarsRequests);
    constants.put("blobSidecarSubnetCount", blobSidecarSubnetCount);

    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {}
}
