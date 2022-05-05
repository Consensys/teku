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

package tech.pegasys.teku.ethereum.executionlayer.client;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.executionengine.schema.Response;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.BlindedBeaconBlockV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.BuilderBidV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.GenericBuilderStatus;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.SignedMessage;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ValidatorRegistrationV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ThrottlingExecutionBuilderClient implements ExecutionBuilderClient {
  private final ExecutionBuilderClient delegate;
  private final ThrottlingTaskQueue taskQueue;

  public ThrottlingExecutionBuilderClient(
      final ExecutionBuilderClient delegate,
      final int maximumConcurrentRequests,
      final MetricsSystem metricsSystem) {
    this.delegate = delegate;
    taskQueue =
        new ThrottlingTaskQueue(
            maximumConcurrentRequests,
            metricsSystem,
            TekuMetricCategory.BEACON,
            "eb_request_queue_size");
  }

  @Override
  public SafeFuture<Response<GenericBuilderStatus>> status() {
    return taskQueue.queueTask(delegate::status);
  }

  @Override
  public SafeFuture<Response<GenericBuilderStatus>> registerValidator(
      final SignedMessage<ValidatorRegistrationV1> signedValidatorRegistrationV1) {
    return taskQueue.queueTask(() -> delegate.registerValidator(signedValidatorRegistrationV1));
  }

  @Override
  public SafeFuture<Response<SignedMessage<BuilderBidV1>>> getHeader(
      final UInt64 slot, final Bytes48 pubKey, final Bytes32 parentHash) {
    return taskQueue.queueTask(() -> delegate.getHeader(slot, pubKey, parentHash));
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> getPayload(
      final SignedMessage<BlindedBeaconBlockV1> signedBlindedBeaconBlock) {
    return taskQueue.queueTask(() -> delegate.getPayload(signedBlindedBeaconBlock));
  }
}
