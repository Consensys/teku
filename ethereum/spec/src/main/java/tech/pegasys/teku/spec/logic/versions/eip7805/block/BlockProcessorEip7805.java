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

package tech.pegasys.teku.spec.logic.versions.eip7805.block;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.operations.InclusionList;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.logic.versions.eip7805.helpers.MiscHelpersEip7805;
import tech.pegasys.teku.spec.logic.versions.electra.block.BlockProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class BlockProcessorEip7805 extends BlockProcessorElectra {

  private final ExecutionRequestsDataCodec executionRequestsDataCodec7805;

  public BlockProcessorEip7805(
      final SpecConfigElectra specConfig,
      final Predicates predicates,
      final MiscHelpersEip7805 miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsElectra beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsElectra schemaDefinitions,
      final ExecutionRequestsDataCodec executionRequestsDataCodec) {
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
        schemaDefinitions,
        executionRequestsDataCodec);
    this.executionRequestsDataCodec7805 = executionRequestsDataCodec;
  }

  @Override
  public NewPayloadRequest computeNewPayloadRequest(
      final BeaconState state,
      final BeaconBlockBody beaconBlockBody,
      final Function<SlotAndBlockRoot, Optional<List<InclusionList>>> inclusionListSupplier)
      throws BlockProcessingException {
    final ExecutionPayload executionPayload = extractExecutionPayload(beaconBlockBody);
    final SszList<SszKZGCommitment> blobKzgCommitments = extractBlobKzgCommitments(beaconBlockBody);
    final List<VersionedHash> versionedHashes =
        blobKzgCommitments.stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .map(miscHelpers::kzgCommitmentToVersionedHash)
            .toList();
    final Bytes32 parentBeaconBlockRoot = state.getLatestBlockHeader().getParentRoot();
    final ExecutionRequests executionRequests =
        beaconBlockBody
            .getOptionalExecutionRequests()
            .orElseThrow(() -> new BlockProcessingException("Execution requests expected"));

    final Optional<List<InclusionList>> inclusionLists =
        inclusionListSupplier.apply(new SlotAndBlockRoot(state.getSlot(), parentBeaconBlockRoot));
    List<Transaction> inclusionList = List.of();
    if (inclusionLists.isPresent()) {
      inclusionList =
          inclusionLists.get().stream()
              .map(InclusionList::getTransactions)
              .flatMap(List::stream)
              .toList();
    }

    return new NewPayloadRequest(
        executionPayload,
        versionedHashes,
        parentBeaconBlockRoot,
        executionRequestsDataCodec7805.encode(executionRequests),
        inclusionList);
  }
}
