/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.validator.coordinator.publisher;

import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadGossipChannel;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeContents;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;
import tech.pegasys.teku.validator.coordinator.ExecutionPayloadFactory;

public class ExecutionPayloadPublisherGloas implements ExecutionPayloadPublisher {

  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionPayloadFactory executionPayloadFactory;
  private final ExecutionPayloadGossipChannel executionPayloadGossipChannel;
  private final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel;
  private final ExecutionPayloadManager executionPayloadManager;

  public ExecutionPayloadPublisherGloas(
      final ExecutionPayloadFactory executionPayloadFactory,
      final ExecutionPayloadGossipChannel executionPayloadGossipChannel,
      final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel,
      final ExecutionPayloadManager executionPayloadManager) {
    this.executionPayloadFactory = executionPayloadFactory;
    this.executionPayloadGossipChannel = executionPayloadGossipChannel;
    this.dataColumnSidecarGossipChannel = dataColumnSidecarGossipChannel;
    this.executionPayloadManager = executionPayloadManager;
  }

  @Override
  public SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    return publishSignedExecutionPayload(
        signedExecutionPayload,
        executionPayloadFactory.createDataColumnSidecars(signedExecutionPayload),
        broadcastValidationLevel);
  }

  @Override
  public SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelopeContents signedExecutionPayloadEnvelopeContents,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    return publishSignedExecutionPayload(
        signedExecutionPayloadEnvelopeContents.getSignedExecutionPayloadEnvelope(),
        executionPayloadFactory.createDataColumnSidecars(signedExecutionPayloadEnvelopeContents),
        broadcastValidationLevel);
  }

  @Override
  public SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      final SignedBlindedExecutionPayloadEnvelope signedBlindedExecutionPayload,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    return SafeFuture.<SignedExecutionPayloadEnvelope>of(
            () ->
                executionPayloadFactory.unblindSignedExecutionPayload(
                    signedBlindedExecutionPayload))
        .thenApply(Optional::of)
        .exceptionally(error -> Optional.empty())
        .thenCompose(
            maybeSignedExecutionPayload ->
                maybeSignedExecutionPayload
                    .map(
                        signedExecutionPayload ->
                            publishSignedExecutionPayload(
                                signedExecutionPayload, broadcastValidationLevel))
                    .orElseGet(
                        () ->
                            SafeFuture.completedFuture(
                                PublishSignedExecutionPayloadResult.rejected(
                                    signedBlindedExecutionPayload.getBeaconBlockRoot(),
                                    "No cached execution payload envelope found for blinded"
                                        + " envelope"))));
  }

  private SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final SafeFuture<List<DataColumnSidecar>> dataColumnSidecarsFuture,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    final Bytes32 beaconBlockRoot = signedExecutionPayload.getBeaconBlockRoot();
    return executionPayloadManager
        .validateAndImportExecutionPayloadForBroadcast(
            signedExecutionPayload, broadcastValidationLevel)
        .thenCompose(
            validateAndImportResult -> {
              final InternalValidationResult validationResult =
                  validateAndImportResult.validationResult();
              if (!validationResult.isAccept()) {
                return SafeFuture.completedFuture(
                    PublishSignedExecutionPayloadResult.rejected(
                        beaconBlockRoot,
                        "Failed broadcast validation"
                            + validationResult
                                .getDescription()
                                .map(description -> ": " + description)
                                .orElse("")));
              }
              publishExecutionPayloadAndDataColumnSidecars(
                  signedExecutionPayload, dataColumnSidecarsFuture);
              return validateAndImportResult
                  .importResult()
                  .orElseGet(
                      () ->
                          SafeFuture.completedFuture(
                              ExecutionPayloadImportResult.successful(signedExecutionPayload)))
                  .thenApply(
                      importResult ->
                          importResult.isSuccessful()
                              ? PublishSignedExecutionPayloadResult.success(beaconBlockRoot)
                              : PublishSignedExecutionPayloadResult.notImported(
                                  beaconBlockRoot, importResult.getFailureReason().name()));
            });
  }

  private void publishExecutionPayloadAndDataColumnSidecars(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final SafeFuture<List<DataColumnSidecar>> dataColumnSidecarsFuture) {
    executionPayloadGossipChannel.publishExecutionPayload(signedExecutionPayload).finishError(LOG);
    dataColumnSidecarsFuture
        .thenAccept(
            dataColumnSidecars ->
                dataColumnSidecarGossipChannel.publishDataColumnSidecars(
                    dataColumnSidecars, RemoteOrigin.LOCAL_PROPOSAL))
        .finishError(LOG);
  }
}
