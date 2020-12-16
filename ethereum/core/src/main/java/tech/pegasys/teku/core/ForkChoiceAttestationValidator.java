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

package tech.pegasys.teku.core;

import static tech.pegasys.teku.core.ForkChoiceUtil.get_ancestor;
import static tech.pegasys.teku.core.ForkChoiceUtil.get_current_slot;
import static tech.pegasys.teku.datastructures.util.AttestationProcessingResult.SUCCESSFUL;
import static tech.pegasys.teku.datastructures.util.AttestationUtil.is_valid_indexed_attestation;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.util.config.Constants.GENESIS_EPOCH;

import java.util.Optional;
import javax.annotation.CheckReturnValue;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ForkChoiceAttestationValidator {

  @CheckReturnValue
  public AttestationProcessingResult validate(
      final ReadOnlyStore store,
      final ValidateableAttestation validateableAttestation,
      final Optional<BeaconState> maybeTargetState) {
    Attestation attestation = validateableAttestation.getAttestation();
    return validateOnAttestation(store, attestation)
        .ifSuccessful(
            () -> {
              if (maybeTargetState.isEmpty()) {
                return AttestationProcessingResult.UNKNOWN_BLOCK;
              } else {
                return is_valid_indexed_attestation(
                    maybeTargetState.get(), validateableAttestation);
              }
            })
        .ifSuccessful(() -> checkIfAttestationShouldBeSavedForFuture(store, attestation));
  }

  private AttestationProcessingResult validateOnAttestation(
      final ReadOnlyStore store, final Attestation attestation) {
    final Checkpoint target = attestation.getData().getTarget();
    UInt64 current_epoch = compute_epoch_at_slot(get_current_slot(store));

    // Use GENESIS_EPOCH for previous when genesis to avoid underflow
    UInt64 previous_epoch =
        current_epoch.compareTo(UInt64.valueOf(GENESIS_EPOCH)) > 0
            ? current_epoch.minus(UInt64.ONE)
            : UInt64.valueOf(GENESIS_EPOCH);

    if (!target.getEpoch().equals(previous_epoch) && !target.getEpoch().equals(current_epoch)) {
      return AttestationProcessingResult.invalid(
          "Attestations must be from the current or previous epoch");
    }

    if (!target.getEpoch().equals(compute_epoch_at_slot(attestation.getData().getSlot()))) {
      return AttestationProcessingResult.invalid("Attestation slot must be within specified epoch");
    }

    final ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();
    if (!forkChoiceStrategy.contains(target.getRoot())) {
      // Attestations target must be for a known block. If a target block is unknown, delay
      // consideration until the block is found
      return AttestationProcessingResult.UNKNOWN_BLOCK;
    }

    Optional<UInt64> blockSlot =
        forkChoiceStrategy.blockSlot(attestation.getData().getBeacon_block_root());
    if (blockSlot.isEmpty()) {
      // Attestations must be for a known block. If block is unknown, delay consideration until the
      // block is found
      return AttestationProcessingResult.UNKNOWN_BLOCK;
    }

    if (blockSlot.get().compareTo(attestation.getData().getSlot()) > 0) {
      return AttestationProcessingResult.invalid(
          "Attestations must not be for blocks in the future. If not, the attestation should not be considered");
    }

    // LMD vote must be consistent with FFG vote target
    final UInt64 target_slot = compute_start_slot_at_epoch(target.getEpoch());
    if (get_ancestor(forkChoiceStrategy, attestation.getData().getBeacon_block_root(), target_slot)
        .map(ancestorRoot -> !ancestorRoot.equals(target.getRoot()))
        .orElse(true)) {
      return AttestationProcessingResult.invalid(
          "LMD vote must be consistent with FFG vote target");
    }

    return SUCCESSFUL;
  }

  private AttestationProcessingResult checkIfAttestationShouldBeSavedForFuture(
      ReadOnlyStore store, Attestation attestation) {

    // Attestations can only affect the fork choice of subsequent slots.
    // Delay consideration in the fork choice until their slot is in the past.
    final UInt64 currentSlot = get_current_slot(store);
    if (currentSlot.compareTo(attestation.getData().getSlot()) < 0) {
      return AttestationProcessingResult.SAVED_FOR_FUTURE;
    }
    if (currentSlot.compareTo(attestation.getData().getSlot()) == 0) {
      return AttestationProcessingResult.DEFER_FOR_FORK_CHOICE;
    }

    // Attestations cannot be from future epochs. If they are, delay consideration until the epoch
    // arrives
    if (currentSlot.compareTo(attestation.getData().getTarget().getEpochStartSlot()) < 0) {
      return AttestationProcessingResult.SAVED_FOR_FUTURE;
    }
    return SUCCESSFUL;
  }
}
