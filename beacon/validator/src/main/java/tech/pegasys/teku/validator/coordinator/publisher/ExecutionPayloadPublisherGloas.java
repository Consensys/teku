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
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadGossipChannel;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeContents;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;
import tech.pegasys.teku.validator.coordinator.DataColumnSidecarCreationException;
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
        SafeFuture.of(
            () -> executionPayloadFactory.createDataColumnSidecars(signedExecutionPayload)),
        broadcastValidationLevel);
  }

  @Override
  public SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelopeContents signedExecutionPayloadEnvelopeContents,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    return publishSignedExecutionPayload(
        signedExecutionPayloadEnvelopeContents.getSignedExecutionPayloadEnvelope(),
        SafeFuture.of(
            () ->
                executionPayloadFactory.createDataColumnSidecars(
                    signedExecutionPayloadEnvelopeContents)),
        broadcastValidationLevel);
  }

  /**
   * The data column sidecars are built concurrently with the broadcast validation, but both must
   * have completed before anything is published: a failure to build the sidecars (for example when
   * the blobs are not cached for a stateful submission) must prevent the execution payload from
   * being broadcast. Gossiping an envelope whose columns cannot be made available is pointless, as
   * no honest node adds such a payload to its store.
   */
  private SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final SafeFuture<List<DataColumnSidecar>> dataColumnSidecarsFuture,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    final Bytes32 beaconBlockRoot = signedExecutionPayload.getBeaconBlockRoot();
    return executionPayloadManager
        .validateAndImportExecutionPayloadForBroadcast(
            signedExecutionPayload, broadcastValidationLevel)
        .thenComposeCombined(
            recoverDataColumnSidecarCreationFailure(dataColumnSidecarsFuture, beaconBlockRoot),
            (validateAndImportResult, dataColumnSidecarsResult) -> {
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
              if (dataColumnSidecarsResult.rejectionReason().isPresent()) {
                // the payload is valid but we cannot make its columns available, so the caller is
                // told it was not published while the import is left to complete on its own
                validateAndImportResult.importResult().ifPresent(future -> future.finishError(LOG));
                return SafeFuture.completedFuture(
                    PublishSignedExecutionPayloadResult.rejected(
                        beaconBlockRoot, dataColumnSidecarsResult.rejectionReason().get()));
              }
              publishExecutionPayloadAndDataColumnSidecars(
                  signedExecutionPayload, dataColumnSidecarsResult.dataColumnSidecars());
              return validateAndImportResult
                  .importResult()
                  .orElseThrow(() -> new IllegalStateException("ACCEPT without import future"))
                  .thenApply(
                      importResult ->
                          importResult.isSuccessful()
                              ? PublishSignedExecutionPayloadResult.success(beaconBlockRoot)
                              : PublishSignedExecutionPayloadResult.notImported(
                                  beaconBlockRoot, importResult.getFailureReason().name()));
            });
  }

  /**
   * A {@link DataColumnSidecarCreationException} is caused by the submission itself rather than by
   * an internal failure, so it is turned into a rejection reason the caller can act on instead of
   * being propagated as an error. Any other failure is left to propagate.
   */
  private static SafeFuture<DataColumnSidecarsResult> recoverDataColumnSidecarCreationFailure(
      final SafeFuture<List<DataColumnSidecar>> dataColumnSidecarsFuture,
      final Bytes32 beaconBlockRoot) {
    return dataColumnSidecarsFuture
        .thenApply(DataColumnSidecarsResult::created)
        .exceptionallyCompose(
            error ->
                ExceptionUtil.getCause(error, DataColumnSidecarCreationException.class)
                    .map(
                        creationException -> {
                          LOG.warn(
                              "Unable to build the data column sidecars for the execution payload"
                                  + " envelope with beacon block root {}: {}",
                              beaconBlockRoot,
                              creationException.getMessage());
                          return SafeFuture.completedFuture(
                              DataColumnSidecarsResult.rejected(creationException.getMessage()));
                        })
                    .orElseGet(() -> SafeFuture.failedFuture(error)));
  }

  /**
   * Either the data column sidecars to publish, or the reason why the execution payload envelope
   * must be rejected because they could not be built.
   */
  private record DataColumnSidecarsResult(
      List<DataColumnSidecar> dataColumnSidecars, Optional<String> rejectionReason) {

    static DataColumnSidecarsResult created(final List<DataColumnSidecar> dataColumnSidecars) {
      return new DataColumnSidecarsResult(dataColumnSidecars, Optional.empty());
    }

    static DataColumnSidecarsResult rejected(final String rejectionReason) {
      return new DataColumnSidecarsResult(List.of(), Optional.of(rejectionReason));
    }
  }

  private void publishExecutionPayloadAndDataColumnSidecars(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final List<DataColumnSidecar> dataColumnSidecars) {
    executionPayloadGossipChannel.publishExecutionPayload(signedExecutionPayload).finishError(LOG);
    dataColumnSidecarGossipChannel.publishDataColumnSidecars(
        dataColumnSidecars, RemoteOrigin.LOCAL_PROPOSAL);
  }
}
