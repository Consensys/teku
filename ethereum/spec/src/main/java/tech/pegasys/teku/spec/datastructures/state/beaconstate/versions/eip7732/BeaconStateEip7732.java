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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732;

import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.CONSOLIDATION_BALANCE_TO_CONSUME;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.DEPOSIT_BALANCE_TO_CONSUME;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.DEPOSIT_REQUESTS_START_INDEX;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.EARLIEST_CONSOLIDATION_EPOCH;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.EARLIEST_EXIT_EPOCH;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.EXIT_BALANCE_TO_CONSUME;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PENDING_BALANCE_DEPOSITS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PENDING_CONSOLIDATIONS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PENDING_PARTIAL_WITHDRAWALS;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingBalanceDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;

public interface BeaconStateEip7732 extends BeaconStateElectra {
  static BeaconStateEip7732 required(final BeaconState state) {
    return state
        .toVersionEip7732()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected an Eip7732 state but got: " + state.getClass().getSimpleName()));
  }

  private static <T extends SszData> void addItems(
      final MoreObjects.ToStringHelper stringBuilder,
      final String keyPrefix,
      final SszList<T> items) {
    for (int i = 0; i < items.size(); i++) {
      stringBuilder.add(keyPrefix + "[" + i + "]", items.get(i));
    }
  }

  static void describeCustomElectraFields(
      final MoreObjects.ToStringHelper stringBuilder, final BeaconStateEip7732 state) {
    BeaconStateDeneb.describeCustomDenebFields(stringBuilder, state);
    stringBuilder.add("deposit_requests_start_index", state.getDepositRequestsStartIndex());
    stringBuilder.add("deposit_balance_to_consume", state.getDepositBalanceToConsume());
    stringBuilder.add("exit_balance_to_consume", state.getExitBalanceToConsume());
    stringBuilder.add("earliest_exit_epoch", state.getEarliestExitEpoch());
    stringBuilder.add("consolidation_balance_to_consume", state.getConsolidationBalanceToConsume());
    stringBuilder.add("earliest_consolidation_epoch", state.getEarliestConsolidationEpoch());
    addItems(stringBuilder, "pending_balance_deposits", state.getPendingBalanceDeposits());
    addItems(stringBuilder, "pending_partial_withdrawals", state.getPendingPartialWithdrawals());
    addItems(stringBuilder, "pending_consolidations", state.getPendingConsolidations());
  }

  @Override
  MutableBeaconStateEip7732 createWritableCopy();

  default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateEip7732 updatedEip7732(
          final Mutator<MutableBeaconStateEip7732, E1, E2, E3> mutator) throws E1, E2, E3 {
    MutableBeaconStateEip7732 writableCopy = createWritableCopy();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  default Optional<BeaconStateEip7732> toVersionEip7732() {
    return Optional.of(this);
  }

  @Override
  default UInt64 getDepositRequestsStartIndex() {
    final int index = getSchema().getFieldIndex(DEPOSIT_REQUESTS_START_INDEX);
    return ((SszUInt64) get(index)).get();
  }

  @Override
  default UInt64 getDepositBalanceToConsume() {
    final int index = getSchema().getFieldIndex(DEPOSIT_BALANCE_TO_CONSUME);
    return ((SszUInt64) get(index)).get();
  }

  @Override
  default UInt64 getExitBalanceToConsume() {
    final int index = getSchema().getFieldIndex(EXIT_BALANCE_TO_CONSUME);
    return ((SszUInt64) get(index)).get();
  }

  @Override
  default UInt64 getEarliestExitEpoch() {
    final int index = getSchema().getFieldIndex(EARLIEST_EXIT_EPOCH);
    return ((SszUInt64) get(index)).get();
  }

  @Override
  default UInt64 getConsolidationBalanceToConsume() {
    final int index = getSchema().getFieldIndex(CONSOLIDATION_BALANCE_TO_CONSUME);
    return ((SszUInt64) get(index)).get();
  }

  @Override
  default UInt64 getEarliestConsolidationEpoch() {
    final int index = getSchema().getFieldIndex(EARLIEST_CONSOLIDATION_EPOCH);
    return ((SszUInt64) get(index)).get();
  }

  @Override
  default SszList<PendingBalanceDeposit> getPendingBalanceDeposits() {
    final int index = getSchema().getFieldIndex(PENDING_BALANCE_DEPOSITS);
    return getAny(index);
  }

  @Override
  default SszList<PendingPartialWithdrawal> getPendingPartialWithdrawals() {
    final int index = getSchema().getFieldIndex(PENDING_PARTIAL_WITHDRAWALS);
    return getAny(index);
  }

  @Override
  default SszList<PendingConsolidation> getPendingConsolidations() {
    final int index = getSchema().getFieldIndex(PENDING_CONSOLIDATIONS);
    return getAny(index);
  }
}
