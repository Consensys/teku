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

package tech.pegasys.teku.spec.logic.versions.genesis.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.genesis.BeaconStateGenesis;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.genesis.MutableBeaconStateGenesis;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.common.util.results.ValidatorStats;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.backing.collections.SszBitlist;

public final class BlockProcessorGenesis extends AbstractBlockProcessor {
  private static final Logger LOG = LogManager.getLogger();

  public BlockProcessorGenesis(
      final SpecConstants specConstants,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil) {
    super(specConstants, beaconStateUtil, attestationUtil, validatorsUtil);
  }

  @Override
  public void processAttestationsNoValidation(
      MutableBeaconState genericState, SSZList<Attestation> attestations)
      throws BlockProcessingException {
    final MutableBeaconStateGenesis state =
        MutableBeaconStateGenesis.requireGenesisStateMutable(genericState);

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
          state.getCurrent_epoch_attestations().add(pendingAttestation);
        } else {
          state.getPrevious_epoch_attestations().add(pendingAttestation);
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  @Override
  public ValidatorStats getValidatorStatsPreviousEpoch(
      final BeaconState genericState, final Bytes32 correctTargetRoot) {
    final BeaconStateGenesis state = BeaconStateGenesis.requireGenesisState(genericState);
    return getValidatorStats(state.getPrevious_epoch_attestations(), correctTargetRoot);
  }

  @Override
  public ValidatorStats getValidatorStatsCurrentEpoch(
      final BeaconState genericState, final Bytes32 correctTargetRoot) {
    final BeaconStateGenesis state = BeaconStateGenesis.requireGenesisState(genericState);
    return getValidatorStats(state.getCurrent_epoch_attestations(), correctTargetRoot);
  }

  private ValidatorStats getValidatorStats(
      final SSZList<PendingAttestation> attestations, final Bytes32 correctTargetRoot) {

    final Map<UInt64, Map<UInt64, SszBitlist>> liveValidatorsAggregationBitsBySlotAndCommittee =
        new HashMap<>();
    final Map<UInt64, Map<UInt64, SszBitlist>> correctValidatorsAggregationBitsBySlotAndCommittee =
        new HashMap<>();

    attestations.forEach(
        attestation -> {
          if (isCorrectAttestation(attestation, correctTargetRoot)) {
            correctValidatorsAggregationBitsBySlotAndCommittee
                .computeIfAbsent(attestation.getData().getSlot(), __ -> new HashMap<>())
                .merge(
                    attestation.getData().getIndex(),
                    attestation.getAggregation_bits(),
                    SszBitlist::nullableOr);
          }

          liveValidatorsAggregationBitsBySlotAndCommittee
              .computeIfAbsent(attestation.getData().getSlot(), __ -> new HashMap<>())
              .merge(
                  attestation.getData().getIndex(),
                  attestation.getAggregation_bits(),
                  SszBitlist::nullableOr);
        });

    final int numberOfCorrectValidators =
        correctValidatorsAggregationBitsBySlotAndCommittee.values().stream()
            .flatMap(aggregationBitsByCommittee -> aggregationBitsByCommittee.values().stream())
            .mapToInt(SszBitlist::getBitCount)
            .sum();

    final int numberOfLiveValidators =
        liveValidatorsAggregationBitsBySlotAndCommittee.values().stream()
            .flatMap(aggregationBitsByCommittee -> aggregationBitsByCommittee.values().stream())
            .mapToInt(SszBitlist::getBitCount)
            .sum();

    return new ValidatorStats(numberOfCorrectValidators, numberOfLiveValidators);
  }

  private boolean isCorrectAttestation(
      final PendingAttestation attestation, final Bytes32 correctTargetRoot) {
    return attestation.getData().getTarget().getRoot().equals(correctTargetRoot);
  }
}
