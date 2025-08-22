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

package tech.pegasys.teku.spec.logic.versions.eip7805.helpers;

import static com.google.common.base.Preconditions.checkArgument;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigEip7805;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BeaconStateAccessorsFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;

public class BeaconStateAccessorsEip7805 extends BeaconStateAccessorsFulu {

  private final SpecConfigEip7805 specConfigEip7805;

  public BeaconStateAccessorsEip7805(
      final SpecConfig specConfig,
      final PredicatesElectra predicatesElectra,
      final MiscHelpersFulu miscHelpers) {
    super(SpecConfigFulu.required(specConfig), predicatesElectra, miscHelpers);
    this.specConfigEip7805 = config.toVersionEip7805().orElseThrow();
  }

  public static BeaconStateAccessorsEip7805 required(
      final BeaconStateAccessors beaconStateAccessors) {
    checkArgument(
        beaconStateAccessors instanceof BeaconStateAccessorsEip7805,
        "Expected %s but it was %s",
        BeaconStateAccessorsEip7805.class,
        beaconStateAccessors.getClass());
    return (BeaconStateAccessorsEip7805) beaconStateAccessors;
  }

  public IntList getInclusionListCommittee(final BeaconState state, final UInt64 slot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    final Bytes32 seed = getSeed(state, epoch, Domain.DOMAIN_INCLUSION_LIST_COMMITTEE);
    final IntList indices = getActiveValidatorIndices(state, epoch);
    final int activeValidatorCount = indices.size();
    final UInt64 start =
        slot.mod(specConfigEip7805.getSlotsPerEpoch())
            .times(specConfigEip7805.getInclusionListCommitteeSize());
    final UInt64 end = start.plus(specConfigEip7805.getInclusionListCommitteeSize());
    final IntList inclusionListCommitteeIndices = new IntArrayList();
    for (int index = start.intValue(); index < end.intValue(); index++) {
      final int shuffledIndex =
          miscHelpers.computeShuffledIndex(
              index % activeValidatorCount, activeValidatorCount, seed);
      inclusionListCommitteeIndices.add(indices.getInt(shuffledIndex));
    }
    return inclusionListCommitteeIndices;
  }
}
