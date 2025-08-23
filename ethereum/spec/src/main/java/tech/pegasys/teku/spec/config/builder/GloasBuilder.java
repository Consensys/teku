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
import java.util.function.BiConsumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.config.SpecConfigGloasImpl;

public class GloasBuilder extends BaseForkBuilder
    implements ForkConfigBuilder<SpecConfigFulu, SpecConfigGloas> {
  private Integer aggregateDueBpsGloas;
  private Integer attestationDueBpsGloas;
  private Integer contributionDueBpsGloas;
  private Integer maxRequestPayloads;
  private Integer payloadAttestationDueBps;
  private Integer syncMessageDueBpsGloas;

  // gloas preset
  private UInt64 kzgCommitmentsInclusionProofDepthGloas;
  private Integer ptcSize;
  private Integer maxPayloadAttestations;

  GloasBuilder() {}

  @Override
  public SpecConfigAndParent<SpecConfigGloas> build(
      final SpecConfigAndParent<SpecConfigFulu> specConfigAndParent) {
    return SpecConfigAndParent.of(
        new SpecConfigGloasImpl(
            specConfigAndParent.specConfig(),
            aggregateDueBpsGloas,
            attestationDueBpsGloas,
            contributionDueBpsGloas,
            kzgCommitmentsInclusionProofDepthGloas,
            maxPayloadAttestations,
            maxRequestPayloads,
            payloadAttestationDueBps,
            ptcSize,
            syncMessageDueBpsGloas),
        specConfigAndParent);
  }

  public GloasBuilder aggregateDueBpsGloas(final Integer aggregateDueBpsGloas) {
    checkNotNull(aggregateDueBpsGloas);
    this.aggregateDueBpsGloas = aggregateDueBpsGloas;
    return this;
  }

  public GloasBuilder attestationDueBpsGloas(final Integer attestationDueBpsGloas) {
    checkNotNull(attestationDueBpsGloas);
    this.attestationDueBpsGloas = attestationDueBpsGloas;
    return this;
  }

  public GloasBuilder contributionDueBpsGloas(final Integer contributionDueBpsGloas) {
    checkNotNull(contributionDueBpsGloas);
    this.contributionDueBpsGloas = contributionDueBpsGloas;
    return this;
  }

  public GloasBuilder maxRequestPayloads(final Integer maxRequestPayloads) {
    checkNotNull(maxRequestPayloads);
    this.maxRequestPayloads = maxRequestPayloads;
    return this;
  }

  public GloasBuilder payloadAttestationDueBps(final Integer payloadAttestationDueBps) {
    checkNotNull(payloadAttestationDueBps);
    this.payloadAttestationDueBps = payloadAttestationDueBps;
    return this;
  }

  public GloasBuilder syncMessageDueBpsGloas(final Integer syncMessageDueBpsGloas) {
    checkNotNull(syncMessageDueBpsGloas);
    this.syncMessageDueBpsGloas = syncMessageDueBpsGloas;
    return this;
  }

  public GloasBuilder maxPayloadAttestations(final Integer maxPayloadAttestations) {
    checkNotNull(maxPayloadAttestations);
    this.maxPayloadAttestations = maxPayloadAttestations;
    return this;
  }

  public GloasBuilder ptcSize(final Integer ptcSize) {
    checkNotNull(ptcSize);
    this.ptcSize = ptcSize;
    return this;
  }

  public GloasBuilder kzgCommitmentsInclusionProofDepthGloas(
      final UInt64 kzgCommitmentsInclusionProofDepthGloas) {
    checkNotNull(kzgCommitmentsInclusionProofDepthGloas);
    this.kzgCommitmentsInclusionProofDepthGloas = kzgCommitmentsInclusionProofDepthGloas;
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
    constants.put("aggregateDueBpsGloas", aggregateDueBpsGloas);
    constants.put("attestationDueBpsGloas", attestationDueBpsGloas);
    constants.put("contributionDueBpsGloas", contributionDueBpsGloas);
    constants.put("maxRequestPayloads", maxRequestPayloads);
    constants.put("payloadAttestationDueBps", payloadAttestationDueBps);
    constants.put("syncMessageDueBpsGloas", syncMessageDueBpsGloas);

    constants.put("kzgCommitmentsInclusionProofDepthGloas", kzgCommitmentsInclusionProofDepthGloas);
    constants.put("ptcSize", ptcSize);
    constants.put("maxPayloadAttestations", maxPayloadAttestations);

    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {}
}
