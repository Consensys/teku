/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.deneb.block;

import java.util.Optional;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
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
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class BlockProcessorDeneb extends BlockProcessorCapella {

  public BlockProcessorDeneb(
      final SpecConfigDeneb specConfig,
      final Predicates predicates,
      final MiscHelpersDeneb miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsDeneb schemaDefinitions) {
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
  public void processBlock(
      final MutableBeaconState genericState,
      final BeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor,
      final KzgCommitmentsProcessor kzgCommitmentsProcessor)
      throws BlockProcessingException {
    super.processBlock(
        genericState,
        block,
        indexedAttestationCache,
        signatureVerifier,
        payloadExecutor,
        kzgCommitmentsProcessor);

    processBlobKzgCommitments(genericState, block.getBody(), kzgCommitmentsProcessor);
  }

  @Override
  public void validateExecutionPayload(
      BeaconState genericState,
      BeaconBlockBody beaconBlockBody,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    final int maxBlobsPerBlock = SpecConfigDeneb.required(specConfig).getMaxBlobsPerBlock();
    final SszList<SszKZGCommitment> blobKzgCommitments =
        beaconBlockBody.getOptionalBlobKzgCommitments().orElseThrow();
    if (blobKzgCommitments.size() > maxBlobsPerBlock) {
      throw new BlockProcessingException(
          "Number of kzg commitments in block exceeds max blobs per block");
    }
    super.validateExecutionPayload(genericState, beaconBlockBody, payloadExecutor);
  }

  @Override
  public void processBlobKzgCommitments(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final KzgCommitmentsProcessor kzgCommitmentsProcessor)
      throws BlockProcessingException {
    kzgCommitmentsProcessor.processBlobKzgCommitments(body);
  }
}
