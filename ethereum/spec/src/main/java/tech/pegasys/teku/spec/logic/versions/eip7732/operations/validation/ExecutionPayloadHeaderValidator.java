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

package tech.pegasys.teku.spec.logic.versions.eip7732.operations.validation;

import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.check;
import static tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason.firstOf;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7732.ExecutionPayloadHeaderEip7732;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732.BeaconStateEip7732;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationStateTransitionValidator;

public class ExecutionPayloadHeaderValidator
    implements OperationStateTransitionValidator<SignedExecutionPayloadHeader> {

  ExecutionPayloadHeaderValidator() {}

  @Override
  public Optional<OperationInvalidReason> validate(
      final Fork fork, final BeaconState state, final SignedExecutionPayloadHeader signedHeader) {
    final ExecutionPayloadHeaderEip7732 header =
        ExecutionPayloadHeaderEip7732.required(signedHeader.getMessage());
    return firstOf(
        () -> verifyBuilderBalanceCoversTheHeaderValue(state, header),
        () -> verifySlotIsTheProposalBlockSlot(state, header),
        () -> verifyParentBlockHashIsLatestBlockHash(state, header));
  }

  private Optional<OperationInvalidReason> verifyBuilderBalanceCoversTheHeaderValue(
      final BeaconState state, final ExecutionPayloadHeaderEip7732 header) {
    return check(
        state
            .getBalances()
            .get(header.getBuilderIndex().intValue())
            .get()
            .isGreaterThanOrEqualTo(header.getValue()),
        () ->
            String.format(
                "Builder with index %s does not have the funds to cover the bid (%s)",
                header.getBuilderIndex(), header.getValue()));
  }

  private Optional<OperationInvalidReason> verifySlotIsTheProposalBlockSlot(
      final BeaconState state, final ExecutionPayloadHeaderEip7732 header) {
    return check(
        header.getSlot().equals(state.getSlot()),
        () ->
            String.format(
                "Header slot %s is not the proposal slot %s", header.getSlot(), state.getSlot()));
  }

  private Optional<OperationInvalidReason> verifyParentBlockHashIsLatestBlockHash(
      final BeaconState state, final ExecutionPayloadHeaderEip7732 header) {
    final Bytes32 latestBlockHash = BeaconStateEip7732.required(state).getLatestBlockHash();
    return check(
        header.getParentBlockHash().equals(latestBlockHash),
        () ->
            String.format(
                "Header parent block hash %s does not equal the state latest block hash %s",
                header.getParentBlockHash(), latestBlockHash));
  }
}
