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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
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
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.metrics.MetricsCountersByIntervals;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

public class MetricRecordingExecutionEngineClient extends MetricRecordingAbstractClient
    implements ExecutionEngineClient {

  public static final String ENGINE_REQUESTS_COUNTER_NAME = "engine_requests_total";

  public static final String GET_PAYLOAD_METHOD = "get_payload";
  public static final String NEW_PAYLOAD_METHOD = "new_payload";
  public static final String FORKCHOICE_UPDATED_METHOD = "forkchoice_updated";
  public static final String FORKCHOICE_UPDATED_WITH_ATTRIBUTES_METHOD =
      "forkchoice_updated_with_attributes";
  public static final String GET_PAYLOAD_V2_METHOD = "get_payloadV2";
  public static final String NEW_PAYLOAD_V2_METHOD = "new_payloadV2";
  public static final String FORKCHOICE_UPDATED_V2_METHOD = "forkchoice_updatedV2";
  public static final String FORKCHOICE_UPDATED_WITH_ATTRIBUTES_V2_METHOD =
      "forkchoice_updated_with_attributesV2";
  public static final String FORKCHOICE_UPDATED_V3_METHOD = "forkchoice_updatedV3";
  public static final String FORKCHOICE_UPDATED_WITH_ATTRIBUTES_V3_METHOD =
      "forkchoice_updated_with_attributesV3";
  public static final String GET_PAYLOAD_V3_METHOD = "get_payloadV3";
  public static final String NEW_PAYLOAD_V3_METHOD = "new_payloadV3";

  private final ExecutionEngineClient delegate;

  public MetricRecordingExecutionEngineClient(
      final ExecutionEngineClient delegate,
      final TimeProvider timeProvider,
      final MetricsSystem metricsSystem) {
    super(
        timeProvider,
        MetricsCountersByIntervals.create(
            TekuMetricCategory.BEACON,
            metricsSystem,
            ENGINE_REQUESTS_COUNTER_NAME,
            "Counter recording the number of requests made to the execution engine by method, outcome and execution time interval",
            List.of("method", "outcome"),
            Map.of(List.of(), List.of(100L, 300L, 500L, 1000L, 2000L, 3000L, 5000L))));
    this.delegate = delegate;
  }

  @Override
  public SafeFuture<PowBlock> getPowBlock(final Bytes32 blockHash) {
    return delegate.getPowBlock(blockHash);
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    return delegate.getPowChainHead();
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> getPayloadV1(final Bytes8 payloadId) {
    return countRequest(() -> delegate.getPayloadV1(payloadId), GET_PAYLOAD_METHOD);
  }

  @Override
  public SafeFuture<Response<GetPayloadV2Response>> getPayloadV2(final Bytes8 payloadId) {
    return countRequest(() -> delegate.getPayloadV2(payloadId), GET_PAYLOAD_V2_METHOD);
  }

  @Override
  public SafeFuture<Response<GetPayloadV3Response>> getPayloadV3(final Bytes8 payloadId) {
    return countRequest(() -> delegate.getPayloadV3(payloadId), GET_PAYLOAD_V3_METHOD);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV1(
      final ExecutionPayloadV1 executionPayload) {
    return countRequest(() -> delegate.newPayloadV1(executionPayload), NEW_PAYLOAD_METHOD);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV2(
      final ExecutionPayloadV2 executionPayload) {
    return countRequest(() -> delegate.newPayloadV2(executionPayload), NEW_PAYLOAD_V2_METHOD);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV3(
      final ExecutionPayloadV3 executionPayload,
      final List<VersionedHash> blobVersionedHashes,
      final Bytes32 parentBeaconBlockRoot) {
    return countRequest(
        () -> delegate.newPayloadV3(executionPayload, blobVersionedHashes, parentBeaconBlockRoot),
        NEW_PAYLOAD_V3_METHOD);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV1(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV1> payloadAttributes) {
    return countRequest(
        () -> delegate.forkChoiceUpdatedV1(forkChoiceState, payloadAttributes),
        payloadAttributes.isPresent()
            ? FORKCHOICE_UPDATED_WITH_ATTRIBUTES_METHOD
            : FORKCHOICE_UPDATED_METHOD);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV2(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV2> payloadAttributes) {
    return countRequest(
        () -> delegate.forkChoiceUpdatedV2(forkChoiceState, payloadAttributes),
        payloadAttributes.isPresent()
            ? FORKCHOICE_UPDATED_WITH_ATTRIBUTES_V2_METHOD
            : FORKCHOICE_UPDATED_V2_METHOD);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV3(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV3> payloadAttributes) {
    return countRequest(
        () -> delegate.forkChoiceUpdatedV3(forkChoiceState, payloadAttributes),
        payloadAttributes.isPresent()
            ? FORKCHOICE_UPDATED_WITH_ATTRIBUTES_V3_METHOD
            : FORKCHOICE_UPDATED_V3_METHOD);
  }

  @Override
  public SafeFuture<Response<List<String>>> exchangeCapabilities(final List<String> capabilities) {
    return delegate.exchangeCapabilities(capabilities);
  }
}
