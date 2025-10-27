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

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadGossipChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.validator.coordinator.ExecutionPayloadFactory;

class ExecutionPayloadPublisherGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final ExecutionPayloadFactory executionPayloadFactory =
      mock(ExecutionPayloadFactory.class);
  private final ExecutionPayloadGossipChannel executionPayloadGossipChannel =
      mock(ExecutionPayloadGossipChannel.class);
  private final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel =
      mock(DataColumnSidecarGossipChannel.class);
  private final ExecutionPayloadManager executionPayloadManager =
      mock(ExecutionPayloadManager.class);

  private final ExecutionPayloadPublisherGloas executionPayloadPublisher =
      new ExecutionPayloadPublisherGloas(
          executionPayloadFactory,
          executionPayloadGossipChannel,
          dataColumnSidecarGossipChannel,
          executionPayloadManager);

  final SignedExecutionPayloadEnvelope signedExecutionPayload =
      dataStructureUtil.randomSignedExecutionPayloadEnvelope(42);
  final List<DataColumnSidecar> dataColumnSidecars =
      List.of(dataStructureUtil.randomDataColumnSidecar());

  @BeforeEach
  public void setUp() {
    when(executionPayloadFactory.createDataColumnSidecars(signedExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));
    when(executionPayloadGossipChannel.publishExecutionPayload(signedExecutionPayload))
        .thenReturn(SafeFuture.COMPLETE);
    when(executionPayloadManager.importExecutionPayload(signedExecutionPayload))
        .thenReturn(
            SafeFuture.completedFuture(
                ExecutionPayloadImportResult.successful(signedExecutionPayload)));
  }

  @Test
  public void publishSignedExecutionPayload_shouldPublishImmediatelyAndImport() {
    SafeFutureAssert.assertThatSafeFuture(
            executionPayloadPublisher.publishSignedExecutionPayload(signedExecutionPayload))
        .isCompleted();

    final InOrder inOrder =
        inOrder(
            executionPayloadGossipChannel, dataColumnSidecarGossipChannel, executionPayloadManager);

    inOrder.verify(executionPayloadGossipChannel).publishExecutionPayload(signedExecutionPayload);
    inOrder
        .verify(dataColumnSidecarGossipChannel)
        .publishDataColumnSidecars(dataColumnSidecars, RemoteOrigin.LOCAL_PROPOSAL);
    inOrder.verify(executionPayloadManager).importExecutionPayload(signedExecutionPayload);
  }
}
