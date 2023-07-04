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
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;

public class EngineNewPayloadV1 extends AbstractEngineJsonRpcMethod<PayloadStatus> {

  private static final Logger LOG = LogManager.getLogger();

  public EngineNewPayloadV1(final ExecutionEngineClient executionEngineClient) {
    super(executionEngineClient);
  }

  @Override
  public String getName() {
    return EngineApiMethod.ENGINE_NEW_PAYLOAD.getName();
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public SafeFuture<PayloadStatus> execute(final JsonRpcRequestParams params) {
    final ExecutionPayload executionPayload =
        params.getRequiredParameter(0, ExecutionPayload.class);

    LOG.trace("Calling {}(executionPayload={})", getVersionedName(), executionPayload);

    return executionEngineClient
        .newPayloadV1(ExecutionPayloadV1.fromInternalExecutionPayload(executionPayload))
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(PayloadStatusV1::asInternalExecutionPayload)
        .thenPeek(
            payloadStatus ->
                LOG.trace(
                    "Response {}(executionPayload={}) -> {}",
                    getVersionedName(),
                    executionPayload,
                    payloadStatus))
        .exceptionally(PayloadStatus::failedExecution);
  }
}
