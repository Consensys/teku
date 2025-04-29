/*
 * Copyright Consensys Software Inc., 2025
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
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.ethereum.executionclient.schema.UpdatePayloadWithInclusionListV1Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.spec.executionlayer.UpdatePayloadWithInclusionListResponse;

public class EngineUpdatePayloadWithInclusionListV1
    extends AbstractEngineJsonRpcMethod<UpdatePayloadWithInclusionListResponse> {

  private static final Logger LOG = LogManager.getLogger();

  public EngineUpdatePayloadWithInclusionListV1(final ExecutionEngineClient executionEngineClient) {
    super(executionEngineClient);
  }

  @Override
  public String getName() {
    return EngineApiMethod.ENGINE_UPDATE_PAYLOAD_WITH_INCLUSION_LIST.getName();
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public SafeFuture<UpdatePayloadWithInclusionListResponse> execute(
      final JsonRpcRequestParams params) {
    final Bytes8 payloadId = params.getRequiredParameter(0, Bytes8.class);
    final List<Bytes> inclusionList = params.getRequiredListParameter(1, Bytes.class);

    LOG.trace(
        "Calling {}(payloadId={}, inclusionList={})", getVersionedName(), payloadId, inclusionList);

    return executionEngineClient
        .updatePayloadWithInclusionListV1(payloadId, inclusionList)
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(
            UpdatePayloadWithInclusionListV1Response
                ::asInternalUpdatePayloadWithInclusionListResponse)
        .thenPeek(
            updatePayloadWithInclusionListResponse ->
                LOG.trace(
                    "Response {}(payloadId={}) -> {}",
                    getVersionedName(),
                    updatePayloadWithInclusionListResponse.payloadId(),
                    updatePayloadWithInclusionListResponse));
  }
}
