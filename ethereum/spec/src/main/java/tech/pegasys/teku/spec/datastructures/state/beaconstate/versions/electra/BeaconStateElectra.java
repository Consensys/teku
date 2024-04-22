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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra;

import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.CONSOLIDATION_BALANCE_TO_CONSUME;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.DEPOSIT_BALANCE_TO_CONSUME;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.DEPOSIT_RECEIPTS_START_INDEX;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.EARLIEST_CONSOLIDATION_EPOCH;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.EARLIEST_EXIT_EPOCH;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.EXIT_BALANCE_TO_CONSUME;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PENDING_BALANCE_DEPOSITS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PENDING_CONSOLIDATIONS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PENDING_PARTIAL_WITHDRAWALS;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingBalanceDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;

public interface BeaconStateElectra extends BeaconStateDeneb {
  static BeaconStateElectra required(final BeaconState state) {
    return state
        .toVersionElectra()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected an Electra state but got: " + state.getClass().getSimpleName()));
  }

  static void describeCustomElectraFields(
      final MoreObjects.ToStringHelper stringBuilder, final BeaconStateDeneb state) {
    BeaconStateDeneb.describeCustomDenebFields(stringBuilder, state);
    stringBuilder.add("deposit_receipts_start_index", state.getNextWithdrawalIndex());
  }

  @Override
  MutableBeaconStateElectra createWritableCopy();

  default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateElectra updatedElectra(
          final Mutator<MutableBeaconStateElectra, E1, E2, E3> mutator) throws E1, E2, E3 {
    MutableBeaconStateElectra writableCopy = createWritableCopy();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  default Optional<BeaconStateElectra> toVersionElectra() {
    return Optional.of(this);
  }

  default UInt64 getDepositReceiptsStartIndex() {
    final int index = getSchema().getFieldIndex(DEPOSIT_RECEIPTS_START_INDEX);
    return ((SszUInt64) get(index)).get();
  }

  default UInt64 getDepositBalanceToConsume() {
    final int index = getSchema().getFieldIndex(DEPOSIT_BALANCE_TO_CONSUME);
    return ((SszUInt64) get(index)).get();
  }

  default UInt64 getExitBalanceToConsume() {
    final int index = getSchema().getFieldIndex(EXIT_BALANCE_TO_CONSUME);
    return ((SszUInt64) get(index)).get();
  }

  default UInt64 getEarliestExitEpoch() {
    final int index = getSchema().getFieldIndex(EARLIEST_EXIT_EPOCH);
    return ((SszUInt64) get(index)).get();
  }

  default UInt64 getConsolidationBalanceToConsume() {
    final int index = getSchema().getFieldIndex(CONSOLIDATION_BALANCE_TO_CONSUME);
    return ((SszUInt64) get(index)).get();
  }

  default UInt64 getEarliestConsolidationEpoch() {
    final int index = getSchema().getFieldIndex(EARLIEST_CONSOLIDATION_EPOCH);
    return ((SszUInt64) get(index)).get();
  }

  default SszList<PendingBalanceDeposit> getPendingBalanceDeposits() {
    final int index = getSchema().getFieldIndex(PENDING_BALANCE_DEPOSITS);
    return getAny(index);
  }

  default SszList<PendingPartialWithdrawal> getPendingPartialWithdrawals() {
    final int index = getSchema().getFieldIndex(PENDING_PARTIAL_WITHDRAWALS);
    return getAny(index);
  }

  default SszList<PendingConsolidation> getPendingConsolidations() {
    final int index = getSchema().getFieldIndex(PENDING_CONSOLIDATIONS);
    return getAny(index);
  }
}
