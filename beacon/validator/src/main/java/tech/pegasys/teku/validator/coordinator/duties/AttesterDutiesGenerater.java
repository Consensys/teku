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
import java.util.Optional;
import java.util.function.BiFunction;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.AttesterDuty;

public class AttesterDutiesGenerater {
  private final Spec spec;

  public AttesterDutiesGenerater(Spec spec) {
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
    return new AttesterDuties(
        isChainHeadOptimistic,
        dependentRoot,
        validatorIndices
            .intStream()
            .mapToObj(index -> createAttesterDuties(state, epoch, index))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList());
  }

  private Optional<AttesterDuty> createAttesterDuties(
      final BeaconState state, final UInt64 epoch, final int validatorIndex) {

    return combine(
        spec.getValidatorPubKey(state, UInt64.valueOf(validatorIndex)),
        spec.getCommitteeAssignment(state, epoch, validatorIndex),
        (pkey, committeeAssignment) -> {
          final UInt64 committeeCountPerSlot = spec.getCommitteeCountPerSlot(state, epoch);
          return new AttesterDuty(
              pkey,
              validatorIndex,
              committeeAssignment.getCommittee().size(),
              committeeAssignment.getCommitteeIndex().intValue(),
              committeeCountPerSlot.intValue(),
              committeeAssignment.getCommittee().indexOf(validatorIndex),
              committeeAssignment.getSlot());
        });
  }

  private static <A, B, R> Optional<R> combine(
      Optional<A> a, Optional<B> b, BiFunction<A, B, R> fun) {
    if (a.isEmpty() || b.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(fun.apply(a.get(), b.get()));
  }
}
