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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV2Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV3Response;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV2;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV3;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

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
  public SafeFuture<PowBlock> getPowBlock(final Bytes32 blockHash) {
    return taskQueue.queueTask(() -> delegate.getPowBlock(blockHash));
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    return taskQueue.queueTask(delegate::getPowChainHead);
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> getPayloadV1(final Bytes8 payloadId) {
    return taskQueue.queueTask(() -> delegate.getPayloadV1(payloadId));
  }

  @Override
  public SafeFuture<Response<GetPayloadV2Response>> getPayloadV2(final Bytes8 payloadId) {
    return taskQueue.queueTask(() -> delegate.getPayloadV2(payloadId));
  }

  @Override
  public SafeFuture<Response<GetPayloadV3Response>> getPayloadV3(final Bytes8 payloadId) {
    return taskQueue.queueTask(() -> delegate.getPayloadV3(payloadId));
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV1(
      final ExecutionPayloadV1 executionPayload) {
    return taskQueue.queueTask(() -> delegate.newPayloadV1(executionPayload));
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV2(
      final ExecutionPayloadV2 executionPayload) {
    return taskQueue.queueTask(() -> delegate.newPayloadV2(executionPayload));
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV3(
      final ExecutionPayloadV3 executionPayload,
      final List<VersionedHash> blobVersionedHashes,
      final Bytes32 parentBeaconBlockRoot) {
    return taskQueue.queueTask(
        () -> delegate.newPayloadV3(executionPayload, blobVersionedHashes, parentBeaconBlockRoot));
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV1(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV1> payloadAttributes) {
    return taskQueue.queueTask(
        () -> delegate.forkChoiceUpdatedV1(forkChoiceState, payloadAttributes));
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV2(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV2> payloadAttributes) {
    return taskQueue.queueTask(
        () -> delegate.forkChoiceUpdatedV2(forkChoiceState, payloadAttributes));
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV3(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV3> payloadAttributes) {
    return taskQueue.queueTask(
        () -> delegate.forkChoiceUpdatedV3(forkChoiceState, payloadAttributes));
  }

  @Override
  public SafeFuture<Response<List<String>>> exchangeCapabilities(final List<String> capabilities) {
    return taskQueue.queueTask(() -> delegate.exchangeCapabilities(capabilities));
  }
}
