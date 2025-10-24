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

package tech.pegasys.teku.statetransition.validation.block.rules.phase0;

import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ValidationResultSubCode.IGNORE_ALREADY_SEEN;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ValidationResultSubCode.IGNORE_EQUIVOCATION_DETECTED;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.StatelessValidationRule;
import tech.pegasys.teku.statetransition.validation.block.EquivocationChecker;

public record EquivocationRule(EquivocationChecker equivocationChecker)
    implements StatelessValidationRule {

  /*
   * [IGNORE] The block is the first block with valid signature received for the proposer for the slot, signed_beacon_block.message.slot
   */
  @Override
  public Optional<InternalValidationResult> validate(final SignedBeaconBlock block) {
    return switch (equivocationChecker.check(block)) {
      case FIRST_BLOCK_FOR_SLOT_PROPOSER -> Optional.empty();
      case BLOCK_ALREADY_SEEN_FOR_SLOT_PROPOSER ->
          Optional.of(
              ignore(
                  IGNORE_ALREADY_SEEN, "Block is a known duplicate for this slot and proposer."));
      case EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER ->
          Optional.of(
              ignore(
                  IGNORE_EQUIVOCATION_DETECTED,
                  "Equivocating block detected for this slot and proposer."));
    };
  }
}
