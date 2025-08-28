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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigEip7805;
import tech.pegasys.teku.spec.config.SpecConfigEip7805Impl;
import tech.pegasys.teku.spec.config.SpecConfigGloas;

public class Eip7805Builder extends BaseForkBuilder
    implements ForkConfigBuilder<SpecConfigGloas, SpecConfigEip7805> {

  private Integer inclusionListCommitteeSize;
  private Integer maxRequestInclusionList;
  private Integer maxBytesPerInclusionList;
  private Integer attestationDeadline;
  private Integer proposerInclusionListCutOff;
  private Integer viewFreezeDeadline;

  Eip7805Builder() {}

  @Override
  public SpecConfigAndParent<SpecConfigEip7805> build(
      final SpecConfigAndParent<SpecConfigGloas> specConfigAndParent) {
    return SpecConfigAndParent.of(
        new SpecConfigEip7805Impl(
            specConfigAndParent.specConfig(),
            inclusionListCommitteeSize,
            maxRequestInclusionList,
            maxBytesPerInclusionList,
            attestationDeadline,
            proposerInclusionListCutOff,
            viewFreezeDeadline),
        specConfigAndParent);
  }

  public Eip7805Builder inclusionListCommitteeSize(final Integer inclusionListCommitteeSize) {
    checkNotNull(inclusionListCommitteeSize);
    this.inclusionListCommitteeSize = inclusionListCommitteeSize;
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

  public Eip7805Builder attestationDeadline(final Integer attestationDeadline) {
    checkNotNull(attestationDeadline);
    this.attestationDeadline = attestationDeadline;
    return this;
  }

  public Eip7805Builder proposerInclusionListCutOff(final Integer proposerInclusionListCutOff) {
    checkNotNull(proposerInclusionListCutOff);
    this.proposerInclusionListCutOff = proposerInclusionListCutOff;
    return this;
  }

  public Eip7805Builder viewFreezeDeadline(final Integer viewFreezeDeadline) {
    checkNotNull(viewFreezeDeadline);
    this.viewFreezeDeadline = viewFreezeDeadline;
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

    constants.put("inclusionListCommitteeSize", inclusionListCommitteeSize);
    constants.put("maxRequestInclusionList", maxRequestInclusionList);
    constants.put("maxBytesPerInclusionList", maxBytesPerInclusionList);
    constants.put("attestationDeadline", attestationDeadline);
    constants.put("proposerInclusionListCutOff", proposerInclusionListCutOff);
    constants.put("viewFreezeDeadline", viewFreezeDeadline);

    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {}
}
