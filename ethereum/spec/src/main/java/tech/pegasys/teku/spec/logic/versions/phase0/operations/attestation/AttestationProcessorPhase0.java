/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.versions.phase0.operations.attestation;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.MutableBeaconStatePhase0;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.operations.attestation.AttestationProcessor;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;

public class AttestationProcessorPhase0 extends AttestationProcessor {
  final BeaconStateAccessors beaconStateAccessors;

  public AttestationProcessorPhase0(
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final AttestationDataStateTransitionValidator attestationValidator) {
    super(beaconStateUtil, attestationUtil, attestationValidator);
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  protected void processAttestation(
      final MutableBeaconState genericState,
      final Attestation attestation,
      final IndexedAttestationProvider indexedAttestationProvider) {
    final MutableBeaconStatePhase0 state = MutableBeaconStatePhase0.required(genericState);
    final AttestationData data = attestation.getData();

    PendingAttestation pendingAttestation =
        new PendingAttestation(
            attestation.getAggregation_bits(),
            data,
            state.getSlot().minus(data.getSlot()),
            UInt64.valueOf(beaconStateAccessors.getBeaconProposerIndex(state)));

    if (data.getTarget().getEpoch().equals(beaconStateAccessors.getCurrentEpoch(state))) {
      state.getCurrent_epoch_attestations().append(pendingAttestation);
    } else {
      state.getPrevious_epoch_attestations().append(pendingAttestation);
    }
  }
}
