/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionlayer;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionengine.ExecutePayloadResult;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ForkChoiceState;
import tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionengine.PayloadAttributes;

public class ThrottlingExecutionEngineChannel implements ExecutionEngineChannel {
  private final ExecutionEngineChannel delegate;
  private final ThrottlingTaskQueue taskQueue;

  public ThrottlingExecutionEngineChannel(
      final ExecutionEngineChannel delegate,
      final int maximumConcurrentRequests,
      final MetricsSystem metricsSystem) {
    this.delegate = delegate;
    taskQueue =
        new ThrottlingTaskQueue(
            maximumConcurrentRequests,
            metricsSystem,
            TekuMetricCategory.BEACON,
            "ee_request_queue_size");
  }

  @Override
  public SafeFuture<Optional<PowBlock>> getPowBlock(Bytes32 blockHash) {
    return taskQueue.queueTask(() -> delegate.getPowBlock(blockHash));
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    return taskQueue.queueTask(delegate::getPowChainHead);
  }

  @Override
  public SafeFuture<ForkChoiceUpdatedResult> forkChoiceUpdated(
      ForkChoiceState forkChoiceState, Optional<PayloadAttributes> payloadAttributes) {
    return taskQueue.queueTask(
        () -> delegate.forkChoiceUpdated(forkChoiceState, payloadAttributes));
  }

  @Override
  public SafeFuture<ExecutionPayload> getPayload(Bytes8 payloadId, UInt64 slot) {
    return taskQueue.queueTask(() -> delegate.getPayload(payloadId, slot));
  }

  @Override
  public SafeFuture<ExecutePayloadResult> executePayload(ExecutionPayload executionPayload) {
    return taskQueue.queueTask(() -> delegate.executePayload(executionPayload));
  }
}
