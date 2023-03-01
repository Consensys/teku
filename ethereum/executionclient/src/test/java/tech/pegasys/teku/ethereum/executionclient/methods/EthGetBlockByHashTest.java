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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
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

class EthGetBlockByHashTest {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private EthGetBlockByHash jsonRpcMethod;

  @BeforeEach
  public void setUp() {
    jsonRpcMethod = new EthGetBlockByHash(executionEngineClient);
  }

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("eth_getBlockByHash");
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(0); // no version
    assertThat(jsonRpcMethod.getVersionedName()).isEqualTo("eth_getBlockByHash"); // no version
  }

  @Test
  public void blockHashParamIsRequired() {
    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 0");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void shouldReturnFailedExecutionWhenEngineClientRequestFails() {
    final Bytes32 blockHash = Bytes32.random();
    final String errorResponseFromClient = "error!";

    when(executionEngineClient.getPowBlock(eq(blockHash)))
        .thenReturn(dummyFailedResponse(errorResponseFromClient));

    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().add(blockHash).build();

    assertThat(jsonRpcMethod.execute(params))
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(RuntimeException.class)
        .withMessageContaining(errorResponseFromClient);
  }

  @Test
  public void shouldReturnBlockWhenBlockFound() {
    final Bytes32 blockHash = Bytes32.random();
    final PowBlock block =
        new PowBlock(
            blockHash,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt256(),
            dataStructureUtil.randomUInt64());

    when(executionEngineClient.getPowBlock(eq(blockHash)))
        .thenReturn(dummySuccessfulResponse(Optional.of(block)));

    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().add(blockHash).build();

    assertThat(jsonRpcMethod.execute(params)).isCompletedWithValue(Optional.of(block));

    verify(executionEngineClient).getPowBlock(eq(blockHash));
    verifyNoMoreInteractions(executionEngineClient);
  }

  @Test
  public void shouldReturnEmptyOptionalWhenBlockNotFound() {
    final Bytes32 blockHash = Bytes32.random();

    when(executionEngineClient.getPowBlock(eq(blockHash)))
        .thenReturn(dummySuccessfulResponse(Optional.empty()));

    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().add(blockHash).build();

    assertThat(jsonRpcMethod.execute(params)).isCompletedWithValue(Optional.empty());

    verify(executionEngineClient).getPowBlock(eq(blockHash));
    verifyNoMoreInteractions(executionEngineClient);
  }

  private SafeFuture<Optional<PowBlock>> dummySuccessfulResponse(Optional<PowBlock> block) {
    return SafeFuture.completedFuture(block);
  }

  private SafeFuture<Optional<PowBlock>> dummyFailedResponse(final String errorMessage) {
    return SafeFuture.failedFuture(new RuntimeException(errorMessage));
  }
}
