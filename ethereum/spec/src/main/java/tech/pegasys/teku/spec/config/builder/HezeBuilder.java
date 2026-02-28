/*
 * Copyright Consensys Software Inc., 2026
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
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.config.SpecConfigHezeImpl;

public class HezeBuilder extends BaseForkBuilder
    implements ForkConfigBuilder<SpecConfigGloas, SpecConfigHeze> {

  private Integer viewFreezeCutoffBps;
  private Integer inclusionListSubmissionDueBps;
  private Integer proposerInclusionListCutoffBps;
  private Integer maxRequestInclusionList;
  private Integer maxBytesPerInclusionList;

  // heze preset
  private Integer inclusionListCommitteeSize;

  HezeBuilder() {}

  @Override
  public SpecConfigAndParent<SpecConfigHeze> build(
      final SpecConfigAndParent<SpecConfigGloas> specConfigAndParent) {
    return SpecConfigAndParent.of(
        new SpecConfigHezeImpl(
            specConfigAndParent.specConfig(),
            viewFreezeCutoffBps,
            inclusionListSubmissionDueBps,
            proposerInclusionListCutoffBps,
            maxRequestInclusionList,
            maxBytesPerInclusionList,
            inclusionListCommitteeSize),
        specConfigAndParent);
  }

  public HezeBuilder viewFreezeCutoffBps(final Integer viewFreezeCutoffBps) {
    checkNotNull(viewFreezeCutoffBps);
    this.viewFreezeCutoffBps = viewFreezeCutoffBps;
    return this;
  }

  public HezeBuilder inclusionListSubmissionDueBps(final Integer inclusionListSubmissionDueBps) {
    checkNotNull(inclusionListSubmissionDueBps);
    this.inclusionListSubmissionDueBps = inclusionListSubmissionDueBps;
    return this;
  }

  public HezeBuilder proposerInclusionListCutoffBps(final Integer proposerInclusionListCutoffBps) {
    checkNotNull(proposerInclusionListCutoffBps);
    this.proposerInclusionListCutoffBps = proposerInclusionListCutoffBps;
    return this;
  }

  public HezeBuilder maxRequestInclusionList(final Integer maxRequestInclusionList) {
    checkNotNull(maxRequestInclusionList);
    this.maxRequestInclusionList = maxRequestInclusionList;
    return this;
  }

  public HezeBuilder maxBytesPerInclusionList(final Integer maxBytesPerInclusionList) {
    checkNotNull(maxBytesPerInclusionList);
    this.maxBytesPerInclusionList = maxBytesPerInclusionList;
    return this;
  }

  public HezeBuilder inclusionListCommitteeSize(final Integer inclusionListCommitteeSize) {
    checkNotNull(inclusionListCommitteeSize);
    this.inclusionListCommitteeSize = inclusionListCommitteeSize;
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
    constants.put("viewFreezeCutoffBps", viewFreezeCutoffBps);
    constants.put("inclusionListSubmissionDueBps", inclusionListSubmissionDueBps);
    constants.put("proposerInclusionListCutoffBps", proposerInclusionListCutoffBps);
    constants.put("maxRequestInclusionList", maxRequestInclusionList);
    constants.put("maxBytesPerInclusionList", maxBytesPerInclusionList);
    constants.put("inclusionListCommitteeSize", inclusionListCommitteeSize);
    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {}
}
