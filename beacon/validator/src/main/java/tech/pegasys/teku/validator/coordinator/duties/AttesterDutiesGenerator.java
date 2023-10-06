/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.validator.coordinator.duties;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.AttesterDuty;

public class AttesterDutiesGenerator {
  private final Spec spec;

  public AttesterDutiesGenerator(Spec spec) {
    this.spec = spec;
  }

  public AttesterDuties getAttesterDutiesFromIndicesAndState(
      final BeaconState state,
      final UInt64 epoch,
      final IntCollection validatorIndices,
      final boolean isChainHeadOptimistic) {
    final Bytes32 dependentRoot =
        epoch.isGreaterThan(spec.getCurrentEpoch(state))
            ? spec.atEpoch(epoch).getBeaconStateUtil().getCurrentDutyDependentRoot(state)
            : spec.atEpoch(epoch).getBeaconStateUtil().getPreviousDutyDependentRoot(state);
    final List<AttesterDuty> duties = createAttesterDuties(state, epoch, validatorIndices);
    return new AttesterDuties(isChainHeadOptimistic, dependentRoot, duties);
  }

  private List<AttesterDuty> createAttesterDuties(
      final BeaconState state, final UInt64 epoch, final IntCollection validatorIndices) {
    final List<Optional<AttesterDuty>> maybeAttesterDutyList = new ArrayList<>();
    final BeaconStateAccessors beaconStateAccessors = spec.atEpoch(epoch).beaconStateAccessors();
    final UInt64 committeeCountPerSlot =
        beaconStateAccessors.getCommitteeCountPerSlot(state, epoch);
    final Map<Integer, CommitteeAssignment> validatorIndexToCommitteeAssignmentMap =
        getValidatorIndexToCommitteeAssignmentMap(
            state, beaconStateAccessors, committeeCountPerSlot.intValue(), epoch);
    for (final Integer validatorIndex : validatorIndices) {
      final CommitteeAssignment committeeAssignment =
          validatorIndexToCommitteeAssignmentMap.get(validatorIndex);
      if (committeeAssignment != null) {
        maybeAttesterDutyList.add(
            attesterDutyFromCommitteeAssignment(
                committeeAssignment, validatorIndex, committeeCountPerSlot, state));
      }
    }
    return maybeAttesterDutyList.stream().filter(Optional::isPresent).map(Optional::get).toList();
  }

  private Map<Integer, CommitteeAssignment> getValidatorIndexToCommitteeAssignmentMap(
      final BeaconState state,
      final BeaconStateAccessors beaconStateAccessors,
      final int committeeCountPerSlot,
      final UInt64 epoch) {
    final Map<Integer, CommitteeAssignment> attesterMap = new HashMap<>();
    final SpecConfig specConfig = spec.atEpoch(epoch).getConfig();
    final MiscHelpers miscHelpers = spec.atEpoch(epoch).miscHelpers();
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    for (int slotOffset = 0; slotOffset < specConfig.getSlotsPerEpoch(); slotOffset++) {
      final UInt64 slot = startSlot.plus(slotOffset);
      for (int i = 0; i < committeeCountPerSlot; i++) {
        final UInt64 committeeIndex = UInt64.valueOf(i);
        final IntList committee =
            beaconStateAccessors.getBeaconCommittee(state, slot, committeeIndex);
        committee.forEach(
            j -> attesterMap.put(j, new CommitteeAssignment(committee, committeeIndex, slot)));
      }
    }
    return attesterMap;
  }

  private Optional<AttesterDuty> attesterDutyFromCommitteeAssignment(
      final CommitteeAssignment committeeAssignment,
      final int validatorIndex,
      final UInt64 committeeCountPerSlot,
      final BeaconState state) {
    return spec.getValidatorPubKey(state, UInt64.valueOf(validatorIndex))
        .map(
            publicKey ->
                new AttesterDuty(
                    publicKey,
                    validatorIndex,
                    committeeAssignment.getCommittee().size(),
                    committeeAssignment.getCommitteeIndex().intValue(),
                    committeeCountPerSlot.intValue(),
                    committeeAssignment.getCommittee().indexOf(validatorIndex),
                    committeeAssignment.getSlot()));
  }
}
