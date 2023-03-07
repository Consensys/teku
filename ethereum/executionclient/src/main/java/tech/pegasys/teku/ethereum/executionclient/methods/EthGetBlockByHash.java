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

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;

public class EthGetBlockByHash extends AbstractEngineJsonRpcMethod<Optional<PowBlock>> {

  private static final Logger LOG = LogManager.getLogger();

  public EthGetBlockByHash(final ExecutionEngineClient executionEngineClient) {
    super(executionEngineClient);
  }

  @Override
  public String getName() {
    return EngineApiMethods.ETH_GET_BLOCK_BY_HASH.getName();
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @Override
  public SafeFuture<Optional<PowBlock>> execute(final JsonRpcRequestParams params) {
    final Bytes32 blockHash = params.getRequiredParameter(0, Bytes32.class);

    LOG.trace("Calling {}(blockHash={})", getVersionedName(), blockHash);
    return executionEngineClient
        .getPowBlock(blockHash)
        .thenPeek(
            powBlock ->
                LOG.trace(
                    "Response {}(blockHash={}) -> {}", getVersionedName(), blockHash, powBlock));
  }
}
