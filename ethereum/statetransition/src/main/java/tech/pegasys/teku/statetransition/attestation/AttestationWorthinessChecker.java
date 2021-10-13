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

package tech.pegasys.teku.statetransition.attestation;

import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.integerSquareRoot;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/**
 * This filter is meant to exclude attestations that pay no rewards, thus not worthy to be included
 * in a block
 *
 * <p>It currently filters out the attestations older than {@code
 * integerSquareRoot(specVersion.getSlotsPerEpoch())} slots having the wrong target (Altair only)
 */
public class AttestationWorthinessChecker {

  private final Bytes32 expectedAttestationTarget;
  private final UInt64 oldestAcceptedAttestationSlot;
  private final boolean isFilterEnabled;

  public AttestationWorthinessChecker(final Spec spec, BeaconState state) {
    UInt64 currentSlot = state.getSlot();
    SpecVersion specVersion = spec.atSlot(currentSlot);
    isFilterEnabled = specVersion.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ALTAIR);

    if (isFilterEnabled) {
      UInt64 start_slot =
          specVersion.miscHelpers().computeStartSlotAtEpoch(spec.computeEpochAtSlot(currentSlot));

      expectedAttestationTarget =
          start_slot.compareTo(currentSlot) == 0 || state.getSlot().compareTo(start_slot) <= 0
              ? state.getLatest_block_header().getRoot()
              : specVersion.beaconStateAccessors().getBlockRootAtSlot(state, start_slot);

      oldestAcceptedAttestationSlot =
          state.getSlot().minusMinZero(integerSquareRoot(specVersion.getSlotsPerEpoch()));
    } else {
      oldestAcceptedAttestationSlot = null;
      expectedAttestationTarget = null;
    }
  }

  public boolean areAttestationsWorthy(final MatchingDataAttestationGroup attestationGroup) {
    if (!isFilterEnabled) {
      return true;
    }

    final var attestationData = attestationGroup.getAttestationData();

    return attestationData.getSlot().isGreaterThanOrEqualTo(oldestAcceptedAttestationSlot)
        || attestationData.getTarget().getRoot().equals(expectedAttestationTarget);
  }
}
