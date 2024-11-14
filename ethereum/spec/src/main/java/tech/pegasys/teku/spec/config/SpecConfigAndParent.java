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

import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import java.util.Optional;
import tech.pegasys.teku.spec.SpecMilestone;

public record SpecConfigAndParent<TConfig extends SpecConfig>(
    TConfig specConfig, Optional<SpecConfigAndParent<? extends SpecConfig>> parentSpecConfig) {

  public static <TConfig extends TParentConfig, TParentConfig extends SpecConfig>
      SpecConfigAndParent<TConfig> of(
          final TConfig spec, final SpecConfigAndParent<TParentConfig> parentSpec) {
    return new SpecConfigAndParent<>(spec, Optional.of(parentSpec));
  }

  public static <TConfig extends SpecConfig> SpecConfigAndParent<TConfig> of(final TConfig spec) {
    return new SpecConfigAndParent<>(spec, Optional.empty());
  }

  public SpecConfigAndParent<? extends SpecConfig> forMilestone(final SpecMilestone milestone) {
    if (specConfig.getMilestone() == milestone) {
      return this;
    }
    if (parentSpecConfig.isEmpty()) {
      throw new IllegalArgumentException("No config available for milestone " + milestone);
    }
    return parentSpecConfig.get().forMilestone(milestone);
  }

  @SuppressWarnings("unchecked")
  public static SpecConfigAndParent<SpecConfigPhase0> requirePhase0(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    return (SpecConfigAndParent<SpecConfigPhase0>) specConfigAndParent.forMilestone(PHASE0);
  }

  @SuppressWarnings("unchecked")
  public static SpecConfigAndParent<SpecConfigAltair> requireAltair(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    return (SpecConfigAndParent<SpecConfigAltair>) specConfigAndParent.forMilestone(ALTAIR);
  }

  @SuppressWarnings("unchecked")
  public static SpecConfigAndParent<SpecConfigBellatrix> requireBellatrix(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    return (SpecConfigAndParent<SpecConfigBellatrix>) specConfigAndParent.forMilestone(BELLATRIX);
  }

  @SuppressWarnings("unchecked")
  public static SpecConfigAndParent<SpecConfigCapella> requireCapella(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    return (SpecConfigAndParent<SpecConfigCapella>) specConfigAndParent.forMilestone(CAPELLA);
  }

  @SuppressWarnings("unchecked")
  public static SpecConfigAndParent<SpecConfigDeneb> requireDeneb(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    return (SpecConfigAndParent<SpecConfigDeneb>) specConfigAndParent.forMilestone(DENEB);
  }

  @SuppressWarnings("unchecked")
  public static SpecConfigAndParent<SpecConfigElectra> requireElectra(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent) {
    return (SpecConfigAndParent<SpecConfigElectra>) specConfigAndParent.forMilestone(ELECTRA);
  }
}
