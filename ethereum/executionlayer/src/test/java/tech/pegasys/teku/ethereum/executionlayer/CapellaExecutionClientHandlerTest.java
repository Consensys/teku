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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV2Response;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV2;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.ExecutionPayloadBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapella;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadWithValue;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class CapellaExecutionClientHandlerTest extends ExecutionHandlerClientTest {

  @BeforeEach
  void setup() {
    spec = TestSpecFactory.createMinimalCapella();
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  void engineGetPayload_bellatrixFork() throws ExecutionException, InterruptedException {
    final Spec capellaStartingAtEpochOneSpec =
        TestSpecFactory.createMinimalWithCapellaForkEpoch(UInt64.ONE);
    final ExecutionClientHandler handler =
        new ExecutionClientHandlerImpl(
            new MilestoneBasedExecutionJsonRpcMethodsResolver(
                capellaStartingAtEpochOneSpec,
                new LocallySupportedEngineApiCapabilitiesProvider(
                    capellaStartingAtEpochOneSpec, executionEngineClient)));
    final DataStructureUtil data = new DataStructureUtil(capellaStartingAtEpochOneSpec);

    final UInt64 bellatrixSlot = UInt64.ONE;
    final ExecutionPayloadContext context =
        new ExecutionPayloadContext(
            data.randomBytes8(),
            data.randomForkChoiceState(false),
            data.randomPayloadBuildingAttributes(false));

    final SafeFuture<Response<GetPayloadV2Response>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new GetPayloadV2Response(
                    ExecutionPayloadV2.fromInternalExecutionPayload(data.randomExecutionPayload()),
                    data.randomUInt256())));
    when(executionEngineClient.getPayloadV2(context.getPayloadId())).thenReturn(dummyResponse);

    final SafeFuture<ExecutionPayloadWithValue> future =
        handler.engineGetPayload(context, bellatrixSlot);
    verify(executionEngineClient).getPayloadV2(context.getPayloadId());
    verify(executionEngineClient, never()).getPayloadV1(any());

    assertThat(future).isCompleted();
    assertThat(future.get().getExecutionPayload()).isInstanceOf(ExecutionPayloadBellatrix.class);
  }

  @Test
  void engineGetPayload_capellaFork() throws ExecutionException, InterruptedException {
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

    final SafeFuture<ExecutionPayloadWithValue> future = handler.engineGetPayload(context, slot);
    verify(executionEngineClient).getPayloadV2(context.getPayloadId());
    assertThat(future).isCompleted();
    assertThat(future.get().getExecutionPayload()).isInstanceOf(ExecutionPayloadCapella.class);
  }

  @Test
  void engineNewPayload_capellaFork() throws ExecutionException, InterruptedException {
    final ExecutionClientHandler handler = getHandler();
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    final ExecutionPayloadV2 payloadV2 = ExecutionPayloadV2.fromInternalExecutionPayload(payload);
    final SafeFuture<Response<PayloadStatusV1>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new PayloadStatusV1(
                    ExecutionPayloadStatus.ACCEPTED, dataStructureUtil.randomBytes32(), null)));
    when(executionEngineClient.newPayloadV2(payloadV2)).thenReturn(dummyResponse);
    final SafeFuture<PayloadStatus> future = handler.engineNewPayload(payload);
    verify(executionEngineClient).newPayloadV2(payloadV2);
    assertThat(future).isCompleted();
  }

  @Test
  void engineNewPayload_bellatrixFork() {
    final ExecutionClientHandler handler = getHandler();
    final Spec bellatrixSpec = TestSpecFactory.createMinimalBellatrix();
    DataStructureUtil data = new DataStructureUtil(bellatrixSpec);
    final ExecutionPayload payload = data.randomExecutionPayload();
    final ExecutionPayloadV1 payloadV1 = ExecutionPayloadV1.fromInternalExecutionPayload(payload);
    final SafeFuture<Response<PayloadStatusV1>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new PayloadStatusV1(ExecutionPayloadStatus.ACCEPTED, data.randomBytes32(), null)));
    when(executionEngineClient.newPayloadV2(payloadV1)).thenReturn(dummyResponse);
    final SafeFuture<PayloadStatus> future = handler.engineNewPayload(payload);
    verify(executionEngineClient).newPayloadV2(payloadV1);
    verify(executionEngineClient, never()).newPayloadV1(payloadV1);
    assertThat(future).isCompleted();
  }

  @Test
  void engineForkChoiceUpdated_capellaFork() {
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
    final SafeFuture<tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult> future =
        handler.engineForkChoiceUpdated(forkChoiceState, Optional.of(attributes));
    verify(executionEngineClient).forkChoiceUpdatedV2(forkChoiceStateV1, payloadAttributes);
    assertThat(future).isCompleted();
  }

  @Test
  void engineForkChoiceUpdated_bellatrixFork() {
    final Spec capellaStartingAtEpochOneSpec =
        TestSpecFactory.createMinimalWithCapellaForkEpoch(UInt64.ONE);
    final ExecutionClientHandler handler =
        new ExecutionClientHandlerImpl(
            new MilestoneBasedExecutionJsonRpcMethodsResolver(
                capellaStartingAtEpochOneSpec,
                new LocallySupportedEngineApiCapabilitiesProvider(
                    capellaStartingAtEpochOneSpec, executionEngineClient)));
    final DataStructureUtil data = new DataStructureUtil(capellaStartingAtEpochOneSpec);

    final ForkChoiceState forkChoiceState = data.randomForkChoiceState(false);
    final ForkChoiceStateV1 forkChoiceStateV1 =
        ForkChoiceStateV1.fromInternalForkChoiceState(forkChoiceState);
    final UInt64 bellatrixSlot = UInt64.ZERO;
    final PayloadBuildingAttributes attributes =
        new PayloadBuildingAttributes(
            data.randomUInt64(),
            data.randomBytes32(),
            data.randomEth1Address(),
            Optional.empty(),
            Optional.empty(),
            bellatrixSlot);
    final Optional<PayloadAttributesV1> payloadAttributes =
        PayloadAttributesV1.fromInternalPayloadBuildingAttributes(Optional.of(attributes));
    final SafeFuture<Response<ForkChoiceUpdatedResult>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new ForkChoiceUpdatedResult(
                    new PayloadStatusV1(ExecutionPayloadStatus.ACCEPTED, data.randomBytes32(), ""),
                    data.randomBytes8())));
    when(executionEngineClient.forkChoiceUpdatedV2(forkChoiceStateV1, payloadAttributes))
        .thenReturn(dummyResponse);
    final SafeFuture<tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult> future =
        handler.engineForkChoiceUpdated(forkChoiceState, Optional.of(attributes));
    verify(executionEngineClient, never())
        .forkChoiceUpdatedV1(forkChoiceStateV1, payloadAttributes);
    verify(executionEngineClient).forkChoiceUpdatedV2(forkChoiceStateV1, payloadAttributes);
    assertThat(future).isCompleted();
  }

  @Test
  public void engineGetBlobsBundle_throwsNotYetSupported() {
    final ExecutionClientHandler handler = getHandler();
    assertThatThrownBy(
            () ->
                handler.engineGetBlobsBundle(
                    Bytes8.fromHexString("abcd1234abcd1234"), UInt64.valueOf(123)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Can't find method with name engine_getBlobsBundle");
  }
}
