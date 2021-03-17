/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.versions.phase0.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.MutableBeaconStatePhase0;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.ssz.SszList;

public final class BlockProcessorPhase0 extends AbstractBlockProcessor {
  private static final Logger LOG = LogManager.getLogger();

  public BlockProcessorPhase0(
      final SpecConfig specConfig,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil) {
    super(specConfig, beaconStateUtil, attestationUtil, validatorsUtil);
  }

  @Override
  public void processAttestationsNoValidation(
      MutableBeaconState genericState, SszList<Attestation> attestations)
      throws BlockProcessingException {
    final MutableBeaconStatePhase0 state = MutableBeaconStatePhase0.required(genericState);

    try {
      final AttestationDataStateTransitionValidator validator =
          new AttestationDataStateTransitionValidator();

      for (Attestation attestation : attestations) {
        AttestationData data = attestation.getData();
        final Optional<OperationInvalidReason> invalidReason = validator.validate(state, data);
        checkArgument(
            invalidReason.isEmpty(),
            "process_attestations: %s",
            invalidReason.map(OperationInvalidReason::describe).orElse(""));

        List<Integer> committee =
            beaconStateUtil.getBeaconCommittee(state, data.getSlot(), data.getIndex());
        checkArgument(
            attestation.getAggregation_bits().size() == committee.size(),
            "process_attestations: Attestation aggregation bits and committee don't have the same length");

        PendingAttestation pendingAttestation =
            new PendingAttestation(
                attestation.getAggregation_bits(),
                data,
                state.getSlot().minus(data.getSlot()),
                UInt64.valueOf(beaconStateUtil.getBeaconProposerIndex(state)));

        if (data.getTarget().getEpoch().equals(beaconStateUtil.getCurrentEpoch(state))) {
          state.getCurrent_epoch_attestations().append(pendingAttestation);
        } else {
          state.getPrevious_epoch_attestations().append(pendingAttestation);
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }
}
