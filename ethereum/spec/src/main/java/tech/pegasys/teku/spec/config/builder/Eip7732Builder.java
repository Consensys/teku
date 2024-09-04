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
import tech.pegasys.teku.spec.config.SpecConfigEip7732;
import tech.pegasys.teku.spec.config.SpecConfigEip7732Impl;
import tech.pegasys.teku.spec.config.SpecConfigElectra;

public class Eip7732Builder implements ForkConfigBuilder<SpecConfigElectra, SpecConfigEip7732> {

  private Bytes4 eip7732ForkVersion;
  private UInt64 eip7732ForkEpoch;

  private Integer ptcSize;
  private Integer maxPayloadAttestations;
  private Integer kzgCommitmentInclusionProofDepthEip7732;
  private Integer maxRequestPayloads;

  Eip7732Builder() {}

  @Override
  public SpecConfigEip7732 build(final SpecConfigElectra specConfig) {
    return new SpecConfigEip7732Impl(
        specConfig,
        eip7732ForkVersion,
        eip7732ForkEpoch,
        ptcSize,
        maxPayloadAttestations,
        kzgCommitmentInclusionProofDepthEip7732,
        maxRequestPayloads);
  }

  public Eip7732Builder eip7732ForkEpoch(final UInt64 eip7732ForkEpoch) {
    checkNotNull(eip7732ForkEpoch);
    this.eip7732ForkEpoch = eip7732ForkEpoch;
    return this;
  }

  public Eip7732Builder eip7732ForkVersion(final Bytes4 eip7732ForkVersion) {
    checkNotNull(eip7732ForkVersion);
    this.eip7732ForkVersion = eip7732ForkVersion;
    return this;
  }

  public Eip7732Builder ptcSize(final Integer ptcSize) {
    checkNotNull(ptcSize);
    this.ptcSize = ptcSize;
    return this;
  }

  public Eip7732Builder maxPayloadAttestations(final Integer maxPayloadAttestations) {
    checkNotNull(maxPayloadAttestations);
    this.maxPayloadAttestations = maxPayloadAttestations;
    return this;
  }

  public Eip7732Builder kzgCommitmentInclusionProofDepthEip7732(
      final Integer kzgCommitmentInclusionProofDepthEip7732) {
    checkNotNull(kzgCommitmentInclusionProofDepthEip7732);
    this.kzgCommitmentInclusionProofDepthEip7732 = kzgCommitmentInclusionProofDepthEip7732;
    return this;
  }

  public Eip7732Builder maxRequestPayloads(final Integer maxRequestPayloads) {
    checkNotNull(maxRequestPayloads);
    this.maxRequestPayloads = maxRequestPayloads;
    return this;
  }

  @Override
  public void validate() {
    if (eip7732ForkEpoch == null) {
      eip7732ForkEpoch = SpecConfig.FAR_FUTURE_EPOCH;
      eip7732ForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }

    // Fill default zeros if fork is unsupported
    if (eip7732ForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      SpecBuilderUtil.fillMissingValuesWithZeros(this);
    }

    validateConstants();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();

    constants.put("eip7732ForkEpoch", eip7732ForkEpoch);
    constants.put("eip7732ForkVersion", eip7732ForkVersion);
    constants.put("ptcSize", ptcSize);
    constants.put("maxPayloadAttestations", maxPayloadAttestations);
    constants.put(
        "kzgCommitmentInclusionProofDepthEip7732", kzgCommitmentInclusionProofDepthEip7732);
    constants.put("maxRequestPayloads", maxRequestPayloads);

    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    rawConfig.accept("EIP7732_FORK_EPOCH", eip7732ForkEpoch);
  }
}
