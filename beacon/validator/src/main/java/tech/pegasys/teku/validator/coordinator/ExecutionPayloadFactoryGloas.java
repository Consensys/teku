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

package tech.pegasys.teku.validator.coordinator;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class ExecutionPayloadFactoryGloas implements ExecutionPayloadFactory {

  private final Spec spec;
  private final ExecutionLayerBlockProductionManager executionLayerBlockProductionManager;

  public ExecutionPayloadFactoryGloas(
      final Spec spec,
      final ExecutionLayerBlockProductionManager executionLayerBlockProductionManager) {
    this.spec = spec;
    this.executionLayerBlockProductionManager = executionLayerBlockProductionManager;
  }

  @Override
  public SafeFuture<ExecutionPayloadEnvelope> createUnsignedExecutionPayload(
      final UInt64 builderIndex, final BeaconBlockAndState blockAndState) {
    final UInt64 slot = blockAndState.getSlot();
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final SafeFuture<ExecutionPayloadEnvelope> newExecutionPayload =
        getCachedGetPayloadResponseFuture(slot)
            .thenApply(
                getPayloadResponse -> {
                  final Bytes32 tmpStateRoot = Bytes32.ZERO;
                  return schemaDefinitions
                      .getExecutionPayloadEnvelopeSchema()
                      .create(
                          getPayloadResponse.getExecutionPayload(),
                          getPayloadResponse.getExecutionRequests().orElseThrow(),
                          builderIndex,
                          blockAndState.getRoot(),
                          slot,
                          schemaDefinitions
                              .getBlobKzgCommitmentsSchema()
                              .createFromBlobsBundle(
                                  getPayloadResponse.getBlobsBundle().orElseThrow()),
                          tmpStateRoot);
                });
    return newExecutionPayload.thenApplyChecked(
        executionPayload -> {
          // Run state transition and set state root
          final BeaconState newState =
              blockAndState
                  .getState()
                  .updated(
                      state ->
                          spec.atSlot(slot)
                              .getExecutionPayloadProcessor()
                              .orElseThrow()
                              .processUnsignedExecutionPayload(
                                  state, executionPayload, Optional.empty()));
          return executionPayload.copyWithNewStateRoot(newState.hashTreeRoot());
        });
  }

  @Override
  public SafeFuture<List<DataColumnSidecar>> createDataColumnSidecars(
      final SignedExecutionPayloadEnvelope signedExecutionPayload) {
    final UInt64 slot = signedExecutionPayload.getMessage().getSlot();
    final SpecVersion specVersion = spec.atSlot(slot);
    return getCachedGetPayloadResponseFuture(slot)
        .thenApply(
            getPayloadResponse -> {
              final BlobsBundle blobsBundle = getPayloadResponse.getBlobsBundle().orElseThrow();
              final List<Blob> blobs = blobsBundle.getBlobs();
              final int numberOfColumns =
                  SpecConfigFulu.required(specVersion.getConfig()).getNumberOfColumns();
              final List<BlobAndCellProofs> blobAndCellProofsList =
                  IntStream.range(0, blobs.size())
                      .mapToObj(
                          index ->
                              new BlobAndCellProofs(
                                  blobs.get(index),
                                  blobsBundle.getProofs().stream()
                                      .skip((long) index * numberOfColumns)
                                      .limit(numberOfColumns)
                                      .toList()))
                      .toList();
              return MiscHelpersGloas.required(specVersion.miscHelpers())
                  .constructDataColumnSidecars(signedExecutionPayload, blobAndCellProofsList);
            });
  }

  // Only local flow for ePBS -> this result has been cached when creating the bid for the block
  private SafeFuture<GetPayloadResponse> getCachedGetPayloadResponseFuture(final UInt64 slot) {
    return executionLayerBlockProductionManager
        .getCachedPayloadResult(slot)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "ExecutionPayloadResult hasn't been cached for slot " + slot))
        .getPayloadResponseFutureFromLocalFlowRequired();
  }
}
