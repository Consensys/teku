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
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.executionclient.schema.TransitionConfigurationV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.metrics.MetricsCountersByIntervals;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;

public class MetricRecordingExecutionEngineClient extends MetricRecordingAbstractClient
    implements ExecutionEngineClient {

  public static final String ENGINE_REQUESTS_COUNTER_NAME = "engine_requests_total";

  public static final String GET_PAYLOAD_METHOD = "get_payload";
  public static final String NEW_PAYLOAD_METHOD = "new_payload";
  public static final String FORKCHOICE_UPDATED_METHOD = "forkchoice_updated";
  public static final String FORKCHOICE_UPDATED_WITH_ATTRIBUTES_METHOD =
      "forkchoice_updated_with_attributes";

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
  public SafeFuture<Optional<PowBlock>> getPowBlock(final Bytes32 blockHash) {
    return delegate.getPowBlock(blockHash);
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    return delegate.getPowChainHead();
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> getPayload(final Bytes8 payloadId) {
    return countRequest(() -> delegate.getPayload(payloadId), GET_PAYLOAD_METHOD);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayload(
      final ExecutionPayloadV1 executionPayload) {
    return countRequest(() -> delegate.newPayload(executionPayload), NEW_PAYLOAD_METHOD);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdated(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV1> payloadAttributes) {
    return countRequest(
        () -> delegate.forkChoiceUpdated(forkChoiceState, payloadAttributes),
        payloadAttributes.isPresent()
            ? FORKCHOICE_UPDATED_WITH_ATTRIBUTES_METHOD
            : FORKCHOICE_UPDATED_METHOD);
  }

  @Override
  public SafeFuture<Response<TransitionConfigurationV1>> exchangeTransitionConfiguration(
      final TransitionConfigurationV1 transitionConfiguration) {
    return delegate.exchangeTransitionConfiguration(transitionConfiguration);
  }
}
