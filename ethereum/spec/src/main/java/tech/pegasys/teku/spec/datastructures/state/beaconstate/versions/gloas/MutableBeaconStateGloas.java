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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas;

import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.BUILDER_PENDING_PAYMENTS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.BUILDER_PENDING_WITHDRAWALS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.EXECUTION_PAYLOAD_AVAILABILITY;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.LATEST_BLOCK_HASH;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.LATEST_EXECUTION_PAYLOAD_BID;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PAYLOAD_EXPECTED_WITHDRAWALS;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableVector;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.MutableBeaconStateFulu;

public interface MutableBeaconStateGloas extends MutableBeaconStateFulu, BeaconStateGloas {
  static MutableBeaconStateGloas required(final MutableBeaconState state) {
    return state
        .toMutableVersionGloas()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a Gloas state but got: " + state.getClass().getSimpleName()));
  }

  @Override
  default void setLatestExecutionPayloadHeader(
      final ExecutionPayloadHeader executionPayloadHeader) {
    // NO-OP (`latest_execution_payload_header` has been removed in Gloas)
  }

  @Override
  BeaconStateGloas commitChanges();

  @Override
  default Optional<MutableBeaconStateGloas> toMutableVersionGloas() {
    return Optional.of(this);
  }

  default void setLatestExecutionPayloadBid(final ExecutionPayloadBid latestExecutionPayloadBid) {
    final int fieldIndex = getSchema().getFieldIndex(LATEST_EXECUTION_PAYLOAD_BID);
    set(fieldIndex, latestExecutionPayloadBid);
  }

  default void setExecutionPayloadAvailability(final SszBitvector executionPayloadAvailability) {
    final int fieldIndex = getSchema().getFieldIndex(EXECUTION_PAYLOAD_AVAILABILITY);
    set(fieldIndex, executionPayloadAvailability);
  }

  default void setBuilderPendingPayments(
      final SszVector<BuilderPendingPayment> builderPendingPayments) {
    final int fieldIndex = getSchema().getFieldIndex(BUILDER_PENDING_PAYMENTS);
    set(fieldIndex, builderPendingPayments);
  }

  @Override
  default SszMutableVector<BuilderPendingPayment> getBuilderPendingPayments() {
    final int index = getSchema().getFieldIndex(BUILDER_PENDING_PAYMENTS);
    return getAnyByRef(index);
  }

  default void setBuilderPendingWithdrawals(
      final SszList<BuilderPendingWithdrawal> builderPendingWithdrawals) {
    final int fieldIndex = getSchema().getFieldIndex(BUILDER_PENDING_WITHDRAWALS);
    set(fieldIndex, builderPendingWithdrawals);
  }

  @Override
  default SszMutableList<BuilderPendingWithdrawal> getBuilderPendingWithdrawals() {
    final int index = getSchema().getFieldIndex(BUILDER_PENDING_WITHDRAWALS);
    return getAnyByRef(index);
  }

  default void setLatestBlockHash(final Bytes32 latestBlockHash) {
    final int fieldIndex = getSchema().getFieldIndex(LATEST_BLOCK_HASH);
    set(fieldIndex, SszBytes32.of(latestBlockHash));
  }

  default void setPayloadExpectedWithdrawals(final SszList<Withdrawal> payloadExpectedWithdrawals) {
    final int fieldIndex = getSchema().getFieldIndex(PAYLOAD_EXPECTED_WITHDRAWALS);
    set(fieldIndex, payloadExpectedWithdrawals);
  }
}
