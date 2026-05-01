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

package tech.pegasys.teku.spec.logic.versions.heze.util;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.statetransition.epoch.EpochProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.AttestationUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ForkChoiceUtilGloas;
import tech.pegasys.teku.spec.logic.versions.heze.helpers.BeaconStateAccessorsHeze;

public class ForkChoiceUtilHeze extends ForkChoiceUtilGloas {

  public ForkChoiceUtilHeze(
      final SpecConfigHeze specConfig,
      final BeaconStateAccessorsHeze beaconStateAccessors,
      final EpochProcessorGloas epochProcessor,
      final AttestationUtilGloas attestationUtil,
      final MiscHelpersGloas miscHelpers) {
    super(specConfig, beaconStateAccessors, epochProcessor, attestationUtil, miscHelpers);
  }

  @Override
  public Optional<Integer> getInclusionListSubmissionDueMillis() {
    final SpecConfigHeze configHeze = SpecConfigHeze.required(specConfig);
    return Optional.of(
        getSlotComponentDurationMillis(configHeze.getInclusionListSubmissionDueBps()));
  }
}
