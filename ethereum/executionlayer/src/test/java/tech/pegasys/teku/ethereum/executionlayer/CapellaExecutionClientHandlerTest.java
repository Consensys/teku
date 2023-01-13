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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV2Response;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV2;
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

public class CapellaExecutionClientHandlerTest extends ExecutionHandlerClientTest {

  @BeforeEach
  void setup() {
    spec = TestSpecFactory.createMinimalCapella();
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  void engineGetPayload_shouldCallGetPayloadV2() {
    final ExecutionClientHandler handler = getHandler();
    final UInt64 slot = dataStructureUtil.randomUInt64(1_000_000);
    final ExecutionPayloadContext context =
        new ExecutionPayloadContext(
            dataStructureUtil.randomBytes8(),
            dataStructureUtil.randomForkChoiceState(false),
            dataStructureUtil.randomPayloadBuildingAttributes(false));

    final SafeFuture<Response<GetPayloadV2Response>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new GetPayloadV2Response(
                    ExecutionPayloadV2.fromInternalExecutionPayload(
                        dataStructureUtil.randomExecutionPayload()),
                    UInt256.MAX_VALUE)));
    when(executionEngineClient.getPayloadV2(context.getPayloadId())).thenReturn(dummyResponse);

    handler.engineGetPayload(context, slot);
    verify(executionEngineClient).getPayloadV2(context.getPayloadId());
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  void engineNewPayload_shouldCallNewPayloadV2() {
    final ExecutionClientHandler handler = getHandler();
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    final ExecutionPayloadV2 payloadV2 = ExecutionPayloadV2.fromInternalExecutionPayload(payload);
    final SafeFuture<Response<PayloadStatusV1>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new PayloadStatusV1(
                    ExecutionPayloadStatus.ACCEPTED, dataStructureUtil.randomBytes32(), null)));
    when(executionEngineClient.newPayloadV2(payloadV2)).thenReturn(dummyResponse);
    handler.engineNewPayload(payload);
    verify(executionEngineClient).newPayloadV2(payloadV2);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  void engineForkChoiceUpdated_shouldCallEngineForkChoiceUpdatedV2() {
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
            Optional.of(List.of()),
            dataStructureUtil.randomUInt64());
    final Optional<PayloadAttributesV1> payloadAttributes =
        PayloadAttributesV2.fromInternalPayloadBuildingAttributesV2(Optional.of(attributes));
    final SafeFuture<Response<ForkChoiceUpdatedResult>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new ForkChoiceUpdatedResult(
                    new PayloadStatusV1(
                        ExecutionPayloadStatus.ACCEPTED, dataStructureUtil.randomBytes32(), ""),
                    dataStructureUtil.randomBytes8())));
    when(executionEngineClient.forkChoiceUpdatedV2(forkChoiceStateV1, payloadAttributes))
        .thenReturn(dummyResponse);
    handler.engineForkChoiceUpdated(forkChoiceState, Optional.of(attributes));
    verify(executionEngineClient).forkChoiceUpdatedV2(forkChoiceStateV1, payloadAttributes);
  }

  @Override
  public ExecutionClientHandler getHandler() {
    return new CapellaExecutionClientHandler(spec, executionEngineClient);
  }
}
