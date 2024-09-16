/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.validator.coordinator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732.BeaconBlockBodyEip7732;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7732.ExecutionPayloadHeaderEip7732;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.logic.versions.eip7732.helpers.MiscHelpersEip7732;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;

public class CachingExecutionPayloadAndBlobSidecarsRevealer
    implements ExecutionPayloadAndBlobSidecarsRevealer {

  private static final Logger LOG = LogManager.getLogger();

  // EIP7732 TODO: should probably use more optimized data structure
  private final Map<Bytes32, GetPayloadResponse> committedPayloads = new ConcurrentHashMap<>();

  private final Spec spec;

  public CachingExecutionPayloadAndBlobSidecarsRevealer(final Spec spec) {
    this.spec = spec;
  }

  @Override
  public void commit(
      final ExecutionPayloadHeader header, final GetPayloadResponse getPayloadResponse) {
    committedPayloads.put(header.hashTreeRoot(), getPayloadResponse);
  }

  @Override
  public Optional<ExecutionPayloadEnvelope> revealExecutionPayload(
      final SignedBeaconBlock block, final BeaconState state) {
    final ExecutionPayloadHeaderEip7732 committedHeader =
        ExecutionPayloadHeaderEip7732.required(
            BeaconBlockBodyEip7732.required(block.getMessage().getBody())
                .getSignedExecutionPayloadHeader()
                .getMessage());
    final GetPayloadResponse getPayloadResponse =
        committedPayloads.get(committedHeader.hashTreeRoot());
    if (getPayloadResponse == null) {
      logMissingPayload(block);
      return Optional.empty();
    }
    final SchemaDefinitionsEip7732 schemaDefinitions =
        SchemaDefinitionsEip7732.required(spec.atSlot(block.getSlot()).getSchemaDefinitions());
    final SszList<SszKZGCommitment> blobKzgCommitments =
        schemaDefinitions
            .getBlobKzgCommitmentsSchema()
            .createFromBlobsBundle(getPayloadResponse.getBlobsBundle().orElseThrow());
    final ExecutionPayloadEnvelope executionPayload =
        schemaDefinitions
            .getExecutionPayloadEnvelopeSchema()
            .create(
                getPayloadResponse.getExecutionPayload(),
                committedHeader.getBuilderIndex(),
                block.getRoot(),
                blobKzgCommitments,
                false,
                state.hashTreeRoot());
    return Optional.of(executionPayload);
  }

  @Override
  public List<BlobSidecar> revealBlobSidecars(
      final SignedBeaconBlock block,
      final SignedExecutionPayloadEnvelope executionPayloadEnvelope) {
    final Bytes32 committedHeaderRoot =
        BeaconBlockBodyEip7732.required(block.getMessage().getBody())
            .getSignedExecutionPayloadHeader()
            .getMessage()
            .hashTreeRoot();
    // clean up the cached payload since it would be no longer used
    final GetPayloadResponse getPayloadResponse = committedPayloads.remove(committedHeaderRoot);
    if (getPayloadResponse == null) {
      logMissingPayload(block);
      return List.of();
    }
    final MiscHelpersEip7732 miscHelpersEip7732 =
        MiscHelpersEip7732.required(spec.atSlot(block.getSlot()).miscHelpers());

    final BlobsBundle blobsBundle = getPayloadResponse.getBlobsBundle().orElseThrow();

    final List<Blob> blobs = blobsBundle.getBlobs();
    final List<KZGProof> proofs = blobsBundle.getProofs();

    return IntStream.range(0, blobsBundle.getNumberOfBlobs())
        .mapToObj(
            index ->
                miscHelpersEip7732.constructBlobSidecar(
                    block,
                    executionPayloadEnvelope,
                    UInt64.valueOf(index),
                    blobs.get(index),
                    new SszKZGProof(proofs.get(index))))
        .toList();
  }

  private void logMissingPayload(final SignedBeaconBlock block) {
    LOG.error(
        "There is no committed payload for block with slot {} and root {}",
        block.getSlot(),
        block.getRoot());
  }
}
