/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.ethereum.executionclient;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.executionclient.schema.TransitionConfigurationV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;

public class ThrottlingExecutionEngineClient implements ExecutionEngineClient {
  private final ExecutionEngineClient delegate;
  private final ThrottlingTaskQueue taskQueue;

  public ThrottlingExecutionEngineClient(
      final ExecutionEngineClient delegate,
      final int maximumConcurrentRequests,
      final MetricsSystem metricsSystem) {
    this.delegate = delegate;
    taskQueue =
        ThrottlingTaskQueue.create(
            maximumConcurrentRequests,
            metricsSystem,
            TekuMetricCategory.BEACON,
            "ee_request_queue_size");
  }

  @Override
  public SafeFuture<Optional<PowBlock>> getPowBlock(final Bytes32 blockHash) {
    return taskQueue.queueTask(() -> delegate.getPowBlock(blockHash));
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    return taskQueue.queueTask(delegate::getPowChainHead);
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> getPayload(final Bytes8 payloadId) {
    return taskQueue.queueTask(() -> delegate.getPayload(payloadId));
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayload(
      final ExecutionPayloadV1 executionPayload) {
    return taskQueue.queueTask(() -> delegate.newPayload(executionPayload));
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdated(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV1> payloadAttributes) {
    return taskQueue.queueTask(
        () -> delegate.forkChoiceUpdated(forkChoiceState, payloadAttributes));
  }

  @Override
  public SafeFuture<Response<TransitionConfigurationV1>> exchangeTransitionConfiguration(
      final TransitionConfigurationV1 transitionConfiguration) {
    return taskQueue.queueTask(
        () -> delegate.exchangeTransitionConfiguration(transitionConfiguration));
  }
}
