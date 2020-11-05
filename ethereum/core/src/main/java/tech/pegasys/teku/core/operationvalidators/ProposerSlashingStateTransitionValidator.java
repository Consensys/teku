/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.core.operationvalidators;

import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.core.operationvalidators.OperationInvalidReason.check;
import static tech.pegasys.teku.core.operationvalidators.OperationInvalidReason.firstOf;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.is_slashable_validator;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ProposerSlashingStateTransitionValidator
    implements OperationStateTransitionValidator<ProposerSlashing> {

  @Override
  public Optional<OperationInvalidReason> validate(
      final BeaconState state, final ProposerSlashing proposerSlashing) {
    final BeaconBlockHeader header1 = proposerSlashing.getHeader_1().getMessage();
    final BeaconBlockHeader header2 = proposerSlashing.getHeader_2().getMessage();
    return firstOf(
        () ->
            check(
                header1.getSlot().equals(header2.getSlot()),
                ProposerSlashingInvalidReason.HEADER_SLOTS_DIFFERENT),
        () ->
            check(
                header1.getProposerIndex().equals(header2.getProposerIndex()),
                ProposerSlashingInvalidReason.PROPOSER_INDICES_DIFFERENT),
        () -> check(!Objects.equals(header1, header2), ProposerSlashingInvalidReason.SAME_HEADER),
        () ->
            check(
                UInt64.valueOf(state.getValidators().size()).compareTo(header1.getProposerIndex())
                    > 0,
                ProposerSlashingInvalidReason.INVALID_PROPOSER),
        () ->
            check(
                is_slashable_validator(
                    state.getValidators().get(toIntExact(header1.getProposerIndex().longValue())),
                    get_current_epoch(state)),
                ProposerSlashingInvalidReason.PROPOSER_NOT_SLASHABLE));
  }

  public enum ProposerSlashingInvalidReason implements OperationInvalidReason {
    HEADER_SLOTS_DIFFERENT("Header slots don't match"),
    PROPOSER_INDICES_DIFFERENT("Header proposer indices don't match"),
    SAME_HEADER("Headers are not different"),
    INVALID_PROPOSER("Invalid proposer index"),
    PROPOSER_NOT_SLASHABLE("Proposer is not slashable");

    private final String description;

    ProposerSlashingInvalidReason(final String description) {
      this.description = description;
    }

    @Override
    public String describe() {
      return description;
    }
  }
}
