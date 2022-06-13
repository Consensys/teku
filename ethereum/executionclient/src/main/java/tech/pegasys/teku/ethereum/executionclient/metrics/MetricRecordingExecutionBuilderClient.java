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

package tech.pegasys.teku.ethereum.executionclient.metrics;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.ExecutionBuilderClient;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.RequestCounter;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;

public class MetricRecordingExecutionBuilderClient implements ExecutionBuilderClient {

  public static final String STATUS_REQUEST_COUNTER_NAME = "builder_status_requests_total";
  public static final String REGISTER_VALIDATORS_REQUEST_COUNTER_NAME =
      "builder_register_validators_requests_total";
  public static final String GET_HEADER_REQUEST_COUNTER_NAME =
      "builder_get_execution_payload_header_requests_total";
  public static final String GET_PAYLOAD_REQUEST_COUNTER_NAME =
      "builder_get_execution_payload_requests_total";

  private final ExecutionBuilderClient delegate;

  private final RequestCounter statusRequestCounter;
  private final RequestCounter registerValidatorsRequestCounter;
  private final RequestCounter getHeaderRequestCounter;
  private final RequestCounter getPayloadRequestCounter;

  public MetricRecordingExecutionBuilderClient(
      final ExecutionBuilderClient delegate, final MetricsSystem metricsSystem) {
    this.delegate = delegate;

    statusRequestCounter =
        RequestCounter.createForBeaconCategory(
            metricsSystem,
            STATUS_REQUEST_COUNTER_NAME,
            "Counter recording the number of status requests sent to the builder");

    registerValidatorsRequestCounter =
        RequestCounter.createForBeaconCategory(
            metricsSystem,
            REGISTER_VALIDATORS_REQUEST_COUNTER_NAME,
            "Counter recording the number of register validators requests sent to the builder");

    getHeaderRequestCounter =
        RequestCounter.createForBeaconCategory(
            metricsSystem,
            GET_HEADER_REQUEST_COUNTER_NAME,
            "Counter recording the number of get execution payload header requests sent to the builder");

    getPayloadRequestCounter =
        RequestCounter.createForBeaconCategory(
            metricsSystem,
            GET_PAYLOAD_REQUEST_COUNTER_NAME,
            "Counter recording the number of get execution payload requests sent to the builder");
  }

  @Override
  public SafeFuture<Response<Void>> status() {
    return countRequest(delegate.status(), statusRequestCounter);
  }

  @Override
  public SafeFuture<Response<Void>> registerValidators(
      final UInt64 slot, final SszList<SignedValidatorRegistration> signedValidatorRegistrations) {
    return countRequest(
        delegate.registerValidators(slot, signedValidatorRegistrations),
        registerValidatorsRequestCounter);
  }

  @Override
  public SafeFuture<Response<SignedBuilderBid>> getHeader(
      final UInt64 slot, final BLSPublicKey pubKey, final Bytes32 parentHash) {
    return countRequest(delegate.getHeader(slot, pubKey, parentHash), getHeaderRequestCounter);
  }

  @Override
  public SafeFuture<Response<ExecutionPayload>> getPayload(
      final SignedBeaconBlock signedBlindedBeaconBlock) {
    return countRequest(delegate.getPayload(signedBlindedBeaconBlock), getPayloadRequestCounter);
  }

  private <T> SafeFuture<Response<T>> countRequest(
      final SafeFuture<Response<T>> request, final RequestCounter requestCounter) {
    return request
        .catchAndRethrow(__ -> requestCounter.onError())
        .thenPeek(
            response -> {
              if (response.isFailure()) {
                requestCounter.onError();
              } else {
                requestCounter.onSuccess();
              }
            });
  }
}
