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

package tech.pegasys.teku.spec.config;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.spec.SpecMilestone;

public class SpecConfigHezeImpl extends DelegatingSpecConfigGloas implements SpecConfigHeze {

  private final int viewFreezeCutoffBps;
  private final int inclusionListSubmissionDueBps;
  private final int proposerInclusionListCutoffBps;
  private final int maxRequestInclusionList;
  private final int maxBytesPerInclusionList;
  private final int inclusionListCommitteeSize;

  public SpecConfigHezeImpl(
      final SpecConfigGloas specConfig,
      final int viewFreezeCutoffBps,
      final int inclusionListSubmissionDueBps,
      final int proposerInclusionListCutoffBps,
      final int maxRequestInclusionList,
      final int maxBytesPerInclusionList,
      final int inclusionListCommitteeSize) {
    super(specConfig);
    this.viewFreezeCutoffBps = viewFreezeCutoffBps;
    this.inclusionListSubmissionDueBps = inclusionListSubmissionDueBps;
    this.proposerInclusionListCutoffBps = proposerInclusionListCutoffBps;
    this.maxRequestInclusionList = maxRequestInclusionList;
    this.maxBytesPerInclusionList = maxBytesPerInclusionList;
    this.inclusionListCommitteeSize = inclusionListCommitteeSize;
  }

  @Override
  public SpecMilestone getMilestone() {
    return SpecMilestone.HEZE;
  }

  @Override
  public int getViewFreezeCutoffBps() {
    return viewFreezeCutoffBps;
  }

  @Override
  public int getInclusionListSubmissionDueBps() {
    return inclusionListSubmissionDueBps;
  }

  @Override
  public int getProposerInclusionListCutoffBps() {
    return proposerInclusionListCutoffBps;
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
  public int getInclusionListCommitteeSize() {
    return inclusionListCommitteeSize;
  }

  @Override
  public Optional<SpecConfigHeze> toVersionHeze() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    SpecConfigHezeImpl that = (SpecConfigHezeImpl) o;
    return viewFreezeCutoffBps == that.viewFreezeCutoffBps
        && inclusionListSubmissionDueBps == that.inclusionListSubmissionDueBps
        && proposerInclusionListCutoffBps == that.proposerInclusionListCutoffBps
        && maxRequestInclusionList == that.maxRequestInclusionList
        && maxBytesPerInclusionList == that.maxBytesPerInclusionList
        && inclusionListCommitteeSize == that.inclusionListCommitteeSize;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        viewFreezeCutoffBps,
        inclusionListSubmissionDueBps,
        proposerInclusionListCutoffBps,
        maxRequestInclusionList,
        maxBytesPerInclusionList,
        inclusionListCommitteeSize);
  }
}
