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
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;

public class ThrottlingBuilderClient implements BuilderClient {
  private final BuilderClient delegate;
  private final ThrottlingTaskQueue taskQueue;

  public ThrottlingBuilderClient(
      final BuilderClient delegate,
      final int maximumConcurrentRequests,
      final MetricsSystem metricsSystem) {
    this.delegate = delegate;
    taskQueue =
        ThrottlingTaskQueue.create(
            maximumConcurrentRequests,
            metricsSystem,
            TekuMetricCategory.BEACON,
            "builder_request_queue_size");
  }

  @Override
  public SafeFuture<Response<Void>> status() {
    return taskQueue.queueTask(delegate::status);
  }

  @Override
  public SafeFuture<Response<Void>> registerValidators(
      final UInt64 slot, final SszList<SignedValidatorRegistration> signedValidatorRegistrations) {
    return taskQueue.queueTask(
        () -> delegate.registerValidators(slot, signedValidatorRegistrations));
  }

  @Override
  public SafeFuture<Response<Optional<SignedBuilderBid>>> getHeader(
      final UInt64 slot, final BLSPublicKey pubKey, final Bytes32 parentHash) {
    return taskQueue.queueTask(() -> delegate.getHeader(slot, pubKey, parentHash));
  }

  @Override
  public SafeFuture<Response<ExecutionPayload>> getPayload(
      final SignedBeaconBlock signedBlindedBeaconBlock) {
    return taskQueue.queueTask(() -> delegate.getPayload(signedBlindedBeaconBlock));
  }
}
