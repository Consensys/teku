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

package tech.pegasys.teku.validator.coordinator.publisher;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadGossipChannel;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class ExecutionPayloadPublisher {

  private final ExecutionPayloadManager executionPayloadManager;
  private final ExecutionPayloadGossipChannel executionPayloadGossipChannel;

  public ExecutionPayloadPublisher(
      final ExecutionPayloadManager executionPayloadManager,
      final ExecutionPayloadGossipChannel executionPayloadGossipChannel) {
    this.executionPayloadManager = executionPayloadManager;
    this.executionPayloadGossipChannel = executionPayloadGossipChannel;
  }

  public SafeFuture<InternalValidationResult> sendExecutionPayload(
      final SignedExecutionPayloadEnvelope executionPayload) {
    return executionPayloadManager
        .validateAndImportExecutionPayload(executionPayload, Optional.empty())
        .thenPeek(
            result -> {
              if (result.isAccept()) {
                executionPayloadGossipChannel.publishExecutionPayload(executionPayload);
              }
            });
  }
}
