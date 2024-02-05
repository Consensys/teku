/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.electra.block;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.bytes.Bytes31;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.verkle.ExecutionWitness;
import tech.pegasys.teku.spec.datastructures.execution.verkle.StemStateDiff;
import tech.pegasys.teku.spec.datastructures.execution.verkle.SuffixStateDiff;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.capella.block.BlockProcessorCapella;
import tech.pegasys.teku.spec.logic.versions.capella.helpers.MiscHelpersCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class BlockProcessorElectra extends BlockProcessorCapella {

  public BlockProcessorElectra(
      final SpecConfigElectra specConfig,
      final Predicates predicates,
      final MiscHelpersCapella miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsElectra schemaDefinitions) {
    super(
        specConfig,
        predicates,
        miscHelpers,
        syncCommitteeUtil,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        beaconStateUtil,
        attestationUtil,
        validatorsUtil,
        operationValidator,
        SchemaDefinitionsCapella.required(schemaDefinitions));
  }

  @Override
  public void validateExecutionPayload(
      final BeaconState genericState,
      final BeaconBlockBody beaconBlockBody,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    final ExecutionWitness executionWitness = extractExecutionWitness(beaconBlockBody);
    final List<StemStateDiff> stemStateDiffs = executionWitness.getStateDiffs();
    Optional<Bytes31> lastStem = Optional.empty();

    for (final StemStateDiff stemStateDiff : stemStateDiffs) {
      // only valid if list is sorted by stems
      final Bytes31 currentStem = stemStateDiff.getStem();
      if (lastStem.isPresent()) {
        if (currentStem.toUnsignedBigInteger().compareTo(lastStem.get().toUnsignedBigInteger())
            <= 0) {
          throw new BlockProcessingException("StemStateDiffs are not sorted by stems");
        }
      }
      lastStem = Optional.of(currentStem);

      final List<SuffixStateDiff> stateDiffs = stemStateDiff.getStateDiffs();
      Optional<Integer> lastSuffix = Optional.empty();
      for (final SuffixStateDiff suffixStateDiff : stateDiffs) {
        // only valid if list is sorted by suffixes
        final int suffix = Byte.toUnsignedInt(suffixStateDiff.getSuffix());
        if (lastSuffix.isPresent()) {
          if (suffix <= lastSuffix.get()) {
            throw new BlockProcessingException("SuffixStateDiffs are not sorted by suffixes");
          }
        }
        lastSuffix = Optional.of(suffix);
      }
    }

    super.validateExecutionPayload(genericState, beaconBlockBody, payloadExecutor);
  }

  public ExecutionWitness extractExecutionWitness(final BeaconBlockBody beaconBlockBody)
      throws BlockProcessingException {
    return beaconBlockBody
        .getOptionalExecutionWitness()
        .orElseThrow(() -> new BlockProcessingException("ExecutionWitness expected"));
  }
}
