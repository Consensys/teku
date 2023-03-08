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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BellatrixExecutionClientHandlerTest extends ExecutionHandlerClientTest {
  @BeforeEach
  void setup() {
    spec = TestSpecFactory.createMinimalBellatrix();
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  void engineGetPayload_shouldCallGetPayloadV1() {
    final ExecutionClientHandler handler = getHandler();
    final UInt64 slot = dataStructureUtil.randomUInt64(1_000_000);
    final ExecutionPayloadContext context =
        new ExecutionPayloadContext(
            dataStructureUtil.randomBytes8(),
            dataStructureUtil.randomForkChoiceState(false),
            dataStructureUtil.randomPayloadBuildingAttributes(false));

    final SafeFuture<Response<ExecutionPayloadV1>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                ExecutionPayloadV1.fromInternalExecutionPayload(
                    dataStructureUtil.randomExecutionPayload())));
    when(executionEngineClient.getPayloadV1(context.getPayloadId())).thenReturn(dummyResponse);

    handler.engineGetPayload(context, slot);
    verify(executionEngineClient).getPayloadV1(context.getPayloadId());
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  void engineNewPayload_shouldCallNewPayloadV1() {
    final ExecutionClientHandler handler = getHandler();
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    final ExecutionPayloadV1 payloadV1 = ExecutionPayloadV1.fromInternalExecutionPayload(payload);
    final SafeFuture<Response<PayloadStatusV1>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new PayloadStatusV1(
                    ExecutionPayloadStatus.ACCEPTED, dataStructureUtil.randomBytes32(), null)));
    when(executionEngineClient.newPayloadV1(payloadV1)).thenReturn(dummyResponse);
    handler.engineNewPayload(payload);
    verify(executionEngineClient).newPayloadV1(payloadV1);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  void engineForkChoiceUpdated_shouldCallEngineForkChoiceUpdatedV1() {
    final ExecutionClientHandler handler = getHandler();
    final ForkChoiceState forkChoiceState = dataStructureUtil.randomForkChoiceState(false);
    final ForkChoiceStateV1 forkChoiceStateV1 =
        ForkChoiceStateV1.fromInternalForkChoiceState(forkChoiceState);
    final PayloadBuildingAttributes attributes =
        new PayloadBuildingAttributes(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomEth1Address(),
            Optional.empty(),
            Optional.empty(),
            dataStructureUtil.randomUInt64());
    final Optional<PayloadAttributesV1> payloadAttributes =
        PayloadAttributesV1.fromInternalPayloadBuildingAttributes(Optional.of(attributes));
    final SafeFuture<Response<ForkChoiceUpdatedResult>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new ForkChoiceUpdatedResult(
                    new PayloadStatusV1(
                        ExecutionPayloadStatus.ACCEPTED, dataStructureUtil.randomBytes32(), ""),
                    dataStructureUtil.randomBytes8())));
    when(executionEngineClient.forkChoiceUpdatedV1(forkChoiceStateV1, payloadAttributes))
        .thenReturn(dummyResponse);
    handler.engineForkChoiceUpdated(forkChoiceState, Optional.of(attributes));
    verify(executionEngineClient).forkChoiceUpdatedV1(forkChoiceStateV1, payloadAttributes);
  }
}
