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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ValidatorsUtilGloas;
import tech.pegasys.teku.spec.logic.versions.heze.helpers.BeaconStateAccessorsHeze;

public class ValidatorsUtilHeze extends ValidatorsUtilGloas {

  private final BeaconStateAccessorsHeze beaconStateAccessorsHeze;

  public ValidatorsUtilHeze(
      final SpecConfigHeze specConfig,
      final MiscHelpersGloas miscHelpers,
      final BeaconStateAccessorsHeze beaconStateAccessors) {
    super(specConfig, miscHelpers, beaconStateAccessors);
    this.beaconStateAccessorsHeze = beaconStateAccessors;
  }

  @Override
  public Int2ObjectMap<UInt64> getValidatorIndexToILCommitteeAssignmentMap(
      final BeaconState state, final UInt64 epoch) {
    final Int2ObjectMap<UInt64> ilAssignment = new Int2ObjectOpenHashMap<>();
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);

    UInt64.range(startSlot, startSlot.plus(specConfig.getSlotsPerEpoch()))
        .forEach(
            slot -> {
              final IntList committee =
                  beaconStateAccessorsHeze.getInclusionListCommittee(state, slot);
              committee
                  .intStream()
                  .distinct()
                  .forEach(validatorIndex -> ilAssignment.put(validatorIndex, slot));
            });

    return ilAssignment;
  }
}
