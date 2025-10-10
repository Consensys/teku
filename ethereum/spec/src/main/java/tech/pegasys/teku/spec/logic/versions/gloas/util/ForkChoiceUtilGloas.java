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

package tech.pegasys.teku.spec.logic.versions.gloas.util;

import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.versions.fulu.util.ForkChoiceUtilFulu;

public class ForkChoiceUtilGloas extends ForkChoiceUtilFulu {

  public ForkChoiceUtilGloas(
      final SpecConfig specConfig,
      final BeaconStateAccessors beaconStateAccessors,
      final EpochProcessor epochProcessor,
      final AttestationUtil attestationUtil,
      final MiscHelpers miscHelpers) {
    super(specConfig, beaconStateAccessors, epochProcessor, attestationUtil, miscHelpers);
  }

  @Override
  public AvailabilityChecker<?> createAvailabilityChecker(final SignedBeaconBlock block) {
    // TODO(GLOAS): in ePBS, data availability is delayed until the processing of the execution
    // payload.
    // We may have a dedicated availability checker for the execution stage.
    // If it will be the case, this will remain a NOOP
    return AvailabilityChecker.NOOP;
  }
}
