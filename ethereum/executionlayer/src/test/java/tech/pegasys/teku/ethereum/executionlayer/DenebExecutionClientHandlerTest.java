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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobsBundleV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV3Response;
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
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadDeneb;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadWithValue;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DenebExecutionClientHandlerTest extends ExecutionHandlerClientTest {

  @BeforeEach
  void setup() {
    spec = TestSpecFactory.createMinimalDeneb();
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  void engineGetPayload_bellatrixFork() throws ExecutionException, InterruptedException {
    final Spec bellatrixSpec = TestSpecFactory.createMinimalBellatrix();
    final ExecutionClientHandler handler =
        new DenebExecutionClientHandler(bellatrixSpec, executionEngineClient);
    final ExecutionPayloadContext context = randomContext();

    DataStructureUtil data = new DataStructureUtil(bellatrixSpec);
    final SafeFuture<Response<GetPayloadV3Response>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new GetPayloadV3Response(
                    ExecutionPayloadV3.fromInternalExecutionPayload(data.randomExecutionPayload()),
                    dataStructureUtil.randomUInt256())));
    when(executionEngineClient.getPayloadV3(context.getPayloadId())).thenReturn(dummyResponse);

    final UInt64 slot = dataStructureUtil.randomUInt64(1_000_000);
    final SafeFuture<ExecutionPayloadWithValue> future = handler.engineGetPayload(context, slot);
    verify(executionEngineClient).getPayloadV3(context.getPayloadId());
    verify(executionEngineClient, never()).getPayloadV2(any());
    verify(executionEngineClient, never()).getPayloadV1(any());

    assertThat(future).isCompleted();
    assertThat(future.get().getExecutionPayload()).isInstanceOf(ExecutionPayloadBellatrix.class);
  }

  @Test
  void engineGetPayload_capellaFork() throws ExecutionException, InterruptedException {
    final Spec capellaSpec = TestSpecFactory.createMinimalCapella();
    final ExecutionClientHandler handler =
        new DenebExecutionClientHandler(capellaSpec, executionEngineClient);
    final ExecutionPayloadContext context = randomContext();

    DataStructureUtil data = new DataStructureUtil(capellaSpec);
    final SafeFuture<Response<GetPayloadV3Response>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new GetPayloadV3Response(
                    ExecutionPayloadV3.fromInternalExecutionPayload(data.randomExecutionPayload()),
                    UInt256.MAX_VALUE)));
    when(executionEngineClient.getPayloadV3(context.getPayloadId())).thenReturn(dummyResponse);

    final UInt64 slot = dataStructureUtil.randomUInt64(1_000_000);
    final SafeFuture<ExecutionPayloadWithValue> future = handler.engineGetPayload(context, slot);
    verify(executionEngineClient).getPayloadV3(context.getPayloadId());
    assertThat(future).isCompleted();
    assertThat(future.get().getExecutionPayload()).isInstanceOf(ExecutionPayloadCapella.class);
  }

  @Test
  void engineGetPayload_denebFork() throws ExecutionException, InterruptedException {
    final ExecutionClientHandler handler = getHandler();
    final ExecutionPayloadContext context = randomContext();
    final SafeFuture<Response<GetPayloadV3Response>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new GetPayloadV3Response(
                    ExecutionPayloadV3.fromInternalExecutionPayload(
                        dataStructureUtil.randomExecutionPayload()),
                    UInt256.MAX_VALUE)));
    when(executionEngineClient.getPayloadV3(context.getPayloadId())).thenReturn(dummyResponse);

    final UInt64 slot = dataStructureUtil.randomUInt64(1_000_000);
    final SafeFuture<ExecutionPayloadWithValue> future = handler.engineGetPayload(context, slot);
    verify(executionEngineClient).getPayloadV3(context.getPayloadId());
    assertThat(future).isCompleted();
    assertThat(future.get().getExecutionPayload()).isInstanceOf(ExecutionPayloadDeneb.class);
  }

  @Test
  void engineNewPayload_bellatrixFork() {
    final Spec bellatrixSpec = TestSpecFactory.createMinimalBellatrix();
    DataStructureUtil data = new DataStructureUtil(bellatrixSpec);
    final ExecutionClientHandler handler =
        new DenebExecutionClientHandler(bellatrixSpec, executionEngineClient);
    final ExecutionPayload payload = data.randomExecutionPayload();
    final ExecutionPayloadV1 payloadV1 = ExecutionPayloadV1.fromInternalExecutionPayload(payload);
    final SafeFuture<Response<PayloadStatusV1>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new PayloadStatusV1(ExecutionPayloadStatus.ACCEPTED, data.randomBytes32(), null)));
    when(executionEngineClient.newPayloadV3(payloadV1)).thenReturn(dummyResponse);
    final SafeFuture<PayloadStatus> future = handler.engineNewPayload(payload);
    verify(executionEngineClient).newPayloadV3(payloadV1);
    verify(executionEngineClient, never()).newPayloadV2(payloadV1);
    verify(executionEngineClient, never()).newPayloadV1(payloadV1);
    assertThat(future).isCompleted();
  }

  @Test
  void engineNewPayload_capellaFork() {
    final Spec capellaSpec = TestSpecFactory.createMinimalCapella();
    DataStructureUtil data = new DataStructureUtil(capellaSpec);
    final ExecutionClientHandler handler =
        new DenebExecutionClientHandler(capellaSpec, executionEngineClient);
    final ExecutionPayload payload = data.randomExecutionPayload();
    final ExecutionPayloadV2 payloadV2 = ExecutionPayloadV2.fromInternalExecutionPayload(payload);
    final SafeFuture<Response<PayloadStatusV1>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new PayloadStatusV1(ExecutionPayloadStatus.ACCEPTED, data.randomBytes32(), null)));
    when(executionEngineClient.newPayloadV3(payloadV2)).thenReturn(dummyResponse);
    final SafeFuture<PayloadStatus> future = handler.engineNewPayload(payload);
    verify(executionEngineClient).newPayloadV3(payloadV2);
    verify(executionEngineClient, never()).newPayloadV2(payloadV2);
    verify(executionEngineClient, never()).newPayloadV1(payloadV2);
    assertThat(future).isCompleted();
  }

  @Test
  void engineNewPayload_denebFork() {
    final ExecutionClientHandler handler = getHandler();
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    final ExecutionPayloadV3 payloadV3 = ExecutionPayloadV3.fromInternalExecutionPayload(payload);
    final SafeFuture<Response<PayloadStatusV1>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(
                new PayloadStatusV1(
                    ExecutionPayloadStatus.ACCEPTED, dataStructureUtil.randomBytes32(), null)));
    when(executionEngineClient.newPayloadV3(payloadV3)).thenReturn(dummyResponse);
    final SafeFuture<PayloadStatus> future = handler.engineNewPayload(payload);
    verify(executionEngineClient).newPayloadV3(payloadV3);
    assertThat(future).isCompleted();
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  void engineGetBlobsBundle_shouldCallGetBlobsBundleV1() {
    final ExecutionClientHandler handler = getHandler();
    final UInt64 slot = dataStructureUtil.randomUInt64(1_000_000);
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle();
    final SafeFuture<Response<BlobsBundleV1>> dummyResponse =
        SafeFuture.completedFuture(
            new Response<>(BlobsBundleV1.fromInternalBlobsBundle(blobsBundle)));
    when(executionEngineClient.getBlobsBundleV1(payloadId)).thenReturn(dummyResponse);

    handler.engineGetBlobsBundle(payloadId, slot);
    verify(executionEngineClient).getBlobsBundleV1(payloadId);
  }

  private ExecutionPayloadContext randomContext() {
    return new ExecutionPayloadContext(
        dataStructureUtil.randomBytes8(),
        dataStructureUtil.randomForkChoiceState(false),
        dataStructureUtil.randomPayloadBuildingAttributes(false));
  }

  @Override
  public ExecutionClientHandler getHandler() {
    return new DenebExecutionClientHandler(spec, executionEngineClient);
  }
}
