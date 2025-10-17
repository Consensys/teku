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

package tech.pegasys.teku.validator.coordinator.publisher;

import java.util.List;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadGossipChannel;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.validator.coordinator.ExecutionPayloadFactory;

public class ExecutionPayloadPublisherGloas implements ExecutionPayloadPublisher {

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
  public SafeFuture<Void> publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload) {
    // we publish the execution payload (and data column sidecars) immediately and then import
    publishExecutionPayloadAndDataColumnSidecars(
        signedExecutionPayload,
        executionPayloadFactory.createDataColumnSidecars(signedExecutionPayload));
    return executionPayloadManager.importExecutionPayload(signedExecutionPayload).toVoid();
  }

  private void publishExecutionPayloadAndDataColumnSidecars(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final SafeFuture<List<DataColumnSidecar>> dataColumnSidecarsFuture) {
    executionPayloadGossipChannel
        .publishExecutionPayload(signedExecutionPayload)
        .finishStackTrace();
    dataColumnSidecarsFuture
        .thenAccept(
            dataColumnSidecars ->
                dataColumnSidecarGossipChannel.publishDataColumnSidecars(
                    dataColumnSidecars, RemoteOrigin.LOCAL_PROPOSAL))
        .finishStackTrace();
  }
}
