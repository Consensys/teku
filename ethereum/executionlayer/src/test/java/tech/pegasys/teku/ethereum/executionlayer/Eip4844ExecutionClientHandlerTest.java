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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobsBundleV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsBundle;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class Eip4844ExecutionClientHandlerTest extends ExecutionHandlerClientTest {

  @BeforeEach
  void setup() {
    spec = TestSpecFactory.createMinimalEip4844();
    dataStructureUtil = new DataStructureUtil(spec);
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

  @Override
  public ExecutionClientHandler getHandler() {
    return new Eip4844ExecutionClientHandler(spec, executionEngineClient);
  }
}
