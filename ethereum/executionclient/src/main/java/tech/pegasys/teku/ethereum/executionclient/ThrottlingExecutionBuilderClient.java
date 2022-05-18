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

package tech.pegasys.teku.ethereum.executionclient;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;

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
  public SafeFuture<Response<Void>> status() {
    return taskQueue.queueTask(delegate::status);
  }

  @Override
  public SafeFuture<Response<Void>> registerValidator(
      UInt64 slot, final SignedValidatorRegistration signedValidatorRegistration) {
    return taskQueue.queueTask(
        () -> delegate.registerValidator(slot, signedValidatorRegistration));
  }

  @Override
  public SafeFuture<Response<SignedBuilderBid>> getHeader(
      final UInt64 slot, final BLSPublicKey pubKey, final Bytes32 parentHash) {
    return taskQueue.queueTask(() -> delegate.getHeader(slot, pubKey, parentHash));
  }

  @Override
  public SafeFuture<Response<ExecutionPayload>> getPayload(
      SignedBeaconBlock signedBlindedBeaconBlock) {
    return taskQueue.queueTask(() -> delegate.getPayload(signedBlindedBeaconBlock));
  }
}
