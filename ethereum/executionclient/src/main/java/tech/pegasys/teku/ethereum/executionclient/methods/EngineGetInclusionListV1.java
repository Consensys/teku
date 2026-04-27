/*
 * Copyright Consensys Software Inc., 2026
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

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class EngineGetInclusionListV1 extends AbstractEngineJsonRpcMethod<List<Bytes>> {

  private static final Logger LOG = LogManager.getLogger();

  public EngineGetInclusionListV1(final ExecutionEngineClient executionEngineClient) {
    super(executionEngineClient);
  }

  @Override
  public String getName() {
    return EngineApiMethod.ENGINE_GET_INCLUSION_LIST.getName();
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public SafeFuture<List<Bytes>> execute(final JsonRpcRequestParams params) {
    final Bytes32 parentHash = params.getRequiredParameter(0, Bytes32.class);

    LOG.trace("Calling {}(parentHash={})", getVersionedName(), parentHash);

    return executionEngineClient
        .getInclusionListV1(parentHash)
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenPeek(
            transactions ->
                LOG.trace(
                    "Response {}(parentHash={}) -> {} transactions",
                    getVersionedName(),
                    parentHash,
                    transactions.size()));
  }
}
