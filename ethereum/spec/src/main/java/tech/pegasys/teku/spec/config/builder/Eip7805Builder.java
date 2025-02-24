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
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigEip7805;
import tech.pegasys.teku.spec.config.SpecConfigEip7805Impl;
import tech.pegasys.teku.spec.config.SpecConfigElectra;

public class Eip7805Builder implements ForkConfigBuilder<SpecConfigElectra, SpecConfigEip7805> {

  private Bytes4 eip7805ForkVersion;
  private UInt64 eip7805ForkEpoch;

  private Integer inclusionListCommitteeSize;
  private Integer maxTransactionsPerInclusionList;
  private Integer maxRequestInclusionList;
  private Integer maxBytesPerInclusionList;
  private Integer viewFreezeDeadline;

  Eip7805Builder() {}

  @Override
  public SpecConfigAndParent<SpecConfigEip7805> build(
      final SpecConfigAndParent<SpecConfigElectra> specConfigAndParent) {
    return SpecConfigAndParent.of(
        new SpecConfigEip7805Impl(
            specConfigAndParent.specConfig(),
            eip7805ForkVersion,
            eip7805ForkEpoch,
            inclusionListCommitteeSize,
            maxTransactionsPerInclusionList,
            maxRequestInclusionList,
            maxBytesPerInclusionList,
            viewFreezeDeadline),
        specConfigAndParent);
  }

  public Eip7805Builder eip7805ForkEpoch(final UInt64 eip7805ForkEpoch) {
    checkNotNull(eip7805ForkEpoch);
    this.eip7805ForkEpoch = eip7805ForkEpoch;
    return this;
  }

  public Eip7805Builder eip7805ForkVersion(final Bytes4 eip7805ForkVersion) {
    checkNotNull(eip7805ForkVersion);
    this.eip7805ForkVersion = eip7805ForkVersion;
    return this;
  }

  public Eip7805Builder inclusionListCommitteeSize(final Integer inclusionListCommitteeSize) {
    checkNotNull(inclusionListCommitteeSize);
    this.inclusionListCommitteeSize = inclusionListCommitteeSize;
    return this;
  }

  public Eip7805Builder maxTransactionsPerInclusionList(
      final Integer maxTransactionsPerInclusionList) {
    checkNotNull(maxTransactionsPerInclusionList);
    this.maxTransactionsPerInclusionList = maxTransactionsPerInclusionList;
    return this;
  }

  public Eip7805Builder maxRequestInclusionList(final Integer maxRequestInclusionList) {
    checkNotNull(maxRequestInclusionList);
    this.maxRequestInclusionList = maxRequestInclusionList;
    return this;
  }

  public Eip7805Builder maxBytesPerInclusionList(final Integer maxBytesPerInclusionList) {
    checkNotNull(maxBytesPerInclusionList);
    this.maxBytesPerInclusionList = maxBytesPerInclusionList;
    return this;
  }

  public Eip7805Builder viewFreezeDeadline(final Integer viewFreezeDeadline) {
    checkNotNull(viewFreezeDeadline);
    this.viewFreezeDeadline = viewFreezeDeadline;
    return this;
  }

  @Override
  public void validate() {
    if (eip7805ForkEpoch == null) {
      eip7805ForkEpoch = SpecConfig.FAR_FUTURE_EPOCH;
      eip7805ForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }

    // Fill default zeros if fork is unsupported
    if (eip7805ForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      SpecBuilderUtil.fillMissingValuesWithZeros(this);
    }

    validateConstants();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();

    constants.put("eip7805ForkEpoch", eip7805ForkEpoch);
    constants.put("eip7805ForkVersion", eip7805ForkVersion);
    constants.put("inclusionListCommitteeSize", inclusionListCommitteeSize);
    constants.put("maxTransactionsPerInclusionList", maxTransactionsPerInclusionList);
    constants.put("maxRequestInclusionList", maxRequestInclusionList);
    constants.put("maxBytesPerInclusionList", maxBytesPerInclusionList);
    constants.put("viewFreezeDeadline", viewFreezeDeadline);

    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    rawConfig.accept("EIP7805_FORK_EPOCH", eip7805ForkEpoch);
  }
}
