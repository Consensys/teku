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

package tech.pegasys.teku.statetransition.execution;

import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class DefaultExecutionPayloadManager implements ExecutionPayloadManager {

  private static final Logger LOG = LogManager.getLogger();

  // The cache is currently only used for the `payload_present` voting, so no need for a long term
  // caching
  private static final int RECENT_SEEN_EXECUTION_PAYLOADS_CACHE_SIZE = 32;

  private final Set<Bytes32> recentSeenExecutionPayloads =
      LimitedSet.createSynchronized(RECENT_SEEN_EXECUTION_PAYLOADS_CACHE_SIZE);

  private final AsyncRunner asyncRunner;
  private final ExecutionPayloadGossipValidator executionPayloadGossipValidator;
  private final ForkChoice forkChoice;
  private final ExecutionLayerChannel executionLayer;

  public DefaultExecutionPayloadManager(
      final AsyncRunner asyncRunner,
      final ExecutionPayloadGossipValidator executionPayloadGossipValidator,
      final ForkChoice forkChoice,
      final ExecutionLayerChannel executionLayer) {
    this.asyncRunner = asyncRunner;
    this.executionPayloadGossipValidator = executionPayloadGossipValidator;
    this.forkChoice = forkChoice;
    this.executionLayer = executionLayer;
  }

  @Override
  public boolean isExecutionPayloadRecentlySeen(final Bytes32 beaconBlockRoot) {
    return recentSeenExecutionPayloads.contains(beaconBlockRoot);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public SafeFuture<InternalValidationResult> validateAndImportExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final Optional<UInt64> arrivalTimestamp) {
    final SafeFuture<InternalValidationResult> validationResult =
        executionPayloadGossipValidator.validate(signedExecutionPayload);
    validationResult.thenAccept(
        result -> {
          switch (result.code()) {
            case ACCEPT ->
                doImportExecutionPayload(signedExecutionPayload)
                    .finish(err -> LOG.error("Failed to process received execution payload.", err));
            // TODO-GLOAS: what do we do in these cases??
            // https://github.com/Consensys/teku/issues/9878
            case REJECT, SAVE_FOR_FUTURE, IGNORE -> {}
          }
        });
    return validationResult;
  }

  private SafeFuture<ExecutionPayloadImportResult> doImportExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload) {
    // cache the seen `beacon_block_root`
    recentSeenExecutionPayloads.add(signedExecutionPayload.getBeaconBlockRoot());
    return asyncRunner
        .runAsync(() -> forkChoice.onExecutionPayload(signedExecutionPayload, executionLayer))
        .thenPeek(
            result -> {
              if (result.isSuccessful()) {
                LOG.debug(
                    "Successfully imported execution payload {}",
                    signedExecutionPayload::toLogString);
              } else {
                LOG.debug(
                    "Failed to import execution payload for reason {}: {}",
                    result::getFailureReason,
                    signedExecutionPayload::toLogString);
              }
            })
        .exceptionally(
            ex -> {
              final String internalErrorMessage =
                  String.format(
                      "Internal error while importing execution payload: %s. Block content: %s",
                      signedExecutionPayload.toLogString(),
                      getExecutionPayloadContent(signedExecutionPayload));
              LOG.error(internalErrorMessage, ex);
              return ExecutionPayloadImportResult.internalError(ex);
            });
  }

  private String getExecutionPayloadContent(
      final SignedExecutionPayloadEnvelope signedExecutionPayload) {
    return signedExecutionPayload.sszSerialize().toHexString();
  }
}
