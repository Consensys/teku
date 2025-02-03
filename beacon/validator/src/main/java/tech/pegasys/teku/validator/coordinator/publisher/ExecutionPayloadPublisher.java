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

import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.gossip.BlobSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadGossipChannel;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.validator.coordinator.ExecutionPayloadAndBlobSidecarsRevealer;

public class ExecutionPayloadPublisher {
  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionPayloadManager executionPayloadManager;
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  private final ExecutionPayloadGossipChannel executionPayloadGossipChannel;
  private final ExecutionPayloadAndBlobSidecarsRevealer executionPayloadAndBlobSidecarsRevealer;
  private final BlobSidecarGossipChannel blobSidecarGossipChannel;
  private final TimeProvider timeProvider;

  public ExecutionPayloadPublisher(
      final ExecutionPayloadManager executionPayloadManager,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final ExecutionPayloadGossipChannel executionPayloadGossipChannel,
      final ExecutionPayloadAndBlobSidecarsRevealer executionPayloadAndBlobSidecarsRevealer,
      final BlobSidecarGossipChannel blobSidecarGossipChannel,
      final TimeProvider timeProvider) {
    this.executionPayloadManager = executionPayloadManager;
    this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
    this.executionPayloadGossipChannel = executionPayloadGossipChannel;
    this.executionPayloadAndBlobSidecarsRevealer = executionPayloadAndBlobSidecarsRevealer;
    this.blobSidecarGossipChannel = blobSidecarGossipChannel;
    this.timeProvider = timeProvider;
  }

  public SafeFuture<InternalValidationResult> sendExecutionPayload(
      final SignedBeaconBlock block, final SignedExecutionPayloadEnvelope executionPayload) {
    final List<BlobSidecar> blobSidecars =
        executionPayloadAndBlobSidecarsRevealer.revealBlobSidecars(block, executionPayload);
    publishExecutionPayloadAndBlobSidecars(executionPayload, blobSidecars);
    // provide blobs for the execution payload before importing it
    blockBlobSidecarsTrackersPool.onCompletedExecutionPayloadAndBlobSidecars(
        block, executionPayload, blobSidecars);
    return executionPayloadManager.validateAndImportExecutionPayload(
        executionPayload, Optional.of(timeProvider.getTimeInMillis()));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void publishExecutionPayloadAndBlobSidecars(
      final SignedExecutionPayloadEnvelope executionPayload, final List<BlobSidecar> blobSidecars) {
    executionPayloadGossipChannel.publishExecutionPayload(executionPayload);
    blobSidecarGossipChannel.publishBlobSidecars(blobSidecars);
  }
}
