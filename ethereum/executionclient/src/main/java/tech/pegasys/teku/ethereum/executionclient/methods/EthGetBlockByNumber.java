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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;

public class EthGetBlockByNumber extends AbstractEngineJsonRpcMethod<PowBlock> {

  private static final Logger LOG = LogManager.getLogger();

  public EthGetBlockByNumber(final ExecutionEngineClient executionEngineClient) {
    super(executionEngineClient);
  }

  @Override
  public String getName() {
    return "eth_getBlockByNumber";
  }

  @Override
  public int getVersion() {
    return Integer.MAX_VALUE;
  }

  @Override
  public String getVersionedName() {
    return getName();
  }

  @Override
  public SafeFuture<PowBlock> execute(final JsonRpcRequestParams params) {
    LOG.trace("calling eth1GetPowChainHead()");
    return executionEngineClient
        .getPowChainHead()
        .thenPeek(powBlock -> LOG.trace("eth1GetPowChainHead() -> {}", powBlock));
  }
}
