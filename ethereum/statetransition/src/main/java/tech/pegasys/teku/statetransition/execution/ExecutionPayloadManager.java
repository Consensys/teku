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

package tech.pegasys.teku.statetransition.execution;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ExecutionPayloadManager {
  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionPayloadValidator executionPayloadValidator;
  private final ForkChoice forkChoice;
  private final RecentChainData recentChainData;
  private final ExecutionLayerChannel executionLayerChannel;

  public ExecutionPayloadManager(
      final ExecutionPayloadValidator executionPayloadValidator,
      final ForkChoice forkChoice,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayerChannel) {
    this.executionPayloadValidator = executionPayloadValidator;
    this.forkChoice = forkChoice;
    this.recentChainData = recentChainData;
    this.executionLayerChannel = executionLayerChannel;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<InternalValidationResult> validateAndImportExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope,
      final Optional<UInt64> arrivalTimestamp) {
    arrivalTimestamp.ifPresent(
        timestamp -> LOG.trace("Processing payload attestation at {}", timestamp));
    final SafeFuture<InternalValidationResult> validationResult =
        executionPayloadValidator.validate(signedExecutionPayloadEnvelope);
    validationResult.thenAccept(
        result -> {
          switch (result.code()) {
            case ACCEPT, SAVE_FOR_FUTURE -> {
              arrivalTimestamp.ifPresentOrElse(
                  timestamp ->
                      recentChainData.onExecutionPayload(signedExecutionPayloadEnvelope, timestamp),
                  () -> LOG.error("arrivalTimestamp tracking must be enabled to support Eip7732"));
              forkChoice
                  .onExecutionPayload(signedExecutionPayloadEnvelope, executionLayerChannel)
                  .finish(err -> LOG.error("Failed to process received execution payload.", err));
            }
            case IGNORE, REJECT -> {}
          }
        });
    return validationResult;
  }
}
