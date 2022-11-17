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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.executionclient.schema.TransitionConfigurationV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionlayer.TransitionConfiguration;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public abstract class ExecutionHandlerClientTest {
  protected Spec spec;
  protected DataStructureUtil dataStructureUtil;
  protected final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  void engineExchangeTransitionConfiguration_shouldCallExecutionClient() {
    ExecutionClientHandler handler = getHandler();
    TransitionConfiguration config =
        new TransitionConfiguration(
            dataStructureUtil.randomUInt256(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64());
    TransitionConfigurationV1 v1Config =
        TransitionConfigurationV1.fromInternalTransitionConfiguration(config);

    when(executionEngineClient.exchangeTransitionConfiguration(v1Config))
        .thenReturn(SafeFuture.completedFuture(new Response<>(v1Config)));
    handler.engineExchangeTransitionConfiguration(config);
    verify(executionEngineClient)
        .exchangeTransitionConfiguration(any(TransitionConfigurationV1.class));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  void eth1GetPowBlock_shouldCallExecutionClient() {
    ExecutionClientHandler handler = getHandler();
    final Bytes32 root = dataStructureUtil.randomBytes32();

    when(executionEngineClient.getPowBlock(root))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    handler.eth1GetPowBlock(root);
    verify(executionEngineClient).getPowBlock(root);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  void eth1GetPowChainHead_shouldCallExecutionClient() {
    ExecutionClientHandler handler = getHandler();
    PowBlock block =
        new PowBlock(
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt256(),
            dataStructureUtil.randomUInt64());

    when(executionEngineClient.getPowChainHead()).thenReturn(SafeFuture.completedFuture(block));
    handler.eth1GetPowChainHead();
    verify(executionEngineClient).getPowChainHead();
  }

  public abstract ExecutionClientHandler getHandler();
}
