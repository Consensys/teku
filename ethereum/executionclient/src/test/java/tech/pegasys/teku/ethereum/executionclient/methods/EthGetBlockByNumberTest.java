/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.ethereum.executionclient.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EthGetBlockByNumberTest {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private EthGetBlockByNumber jsonRpcMethod;

  @BeforeEach
  public void setUp() {
    jsonRpcMethod = new EthGetBlockByNumber(executionEngineClient);
  }

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("eth_getBlockByNumber");
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(0); // no version
    assertThat(jsonRpcMethod.getVersionedName()).isEqualTo("eth_getBlockByNumber"); // no version
  }

  @Test
  public void shouldReturnFailedExecutionWhenEngineClientRequestFails() {
    final String errorResponseFromClient = "error!";

    when(executionEngineClient.getPowChainHead())
        .thenReturn(dummyFailedResponse(errorResponseFromClient));

    assertThat(jsonRpcMethod.execute(JsonRpcRequestParams.NO_PARAMS))
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(RuntimeException.class)
        .withMessageContaining(errorResponseFromClient);
  }

  @Test
  public void shouldReturnHeadBlock() {
    final Bytes32 blockHash = Bytes32.random();
    final PowBlock block =
        new PowBlock(
            blockHash,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt256(),
            dataStructureUtil.randomUInt64());

    when(executionEngineClient.getPowChainHead()).thenReturn(dummySuccessfulResponse(block));

    assertThat(jsonRpcMethod.execute(JsonRpcRequestParams.NO_PARAMS)).isCompletedWithValue(block);

    verify(executionEngineClient).getPowChainHead();
    verifyNoMoreInteractions(executionEngineClient);
  }

  private SafeFuture<PowBlock> dummySuccessfulResponse(PowBlock block) {
    return SafeFuture.completedFuture(block);
  }

  private SafeFuture<PowBlock> dummyFailedResponse(final String errorMessage) {
    return SafeFuture.failedFuture(new RuntimeException(errorMessage));
  }
}
