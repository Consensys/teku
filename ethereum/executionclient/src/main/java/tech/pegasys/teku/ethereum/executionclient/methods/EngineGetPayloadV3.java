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
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV3Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.GetPayloadResponseDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class EngineGetPayloadV3 extends AbstractEngineJsonRpcMethod<GetPayloadResponse> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;

  public EngineGetPayloadV3(final ExecutionEngineClient executionEngineClient, final Spec spec) {
    super(executionEngineClient);
    this.spec = spec;
  }

  @Override
  public String getName() {
    return EngineApiMethods.ENGINE_GET_PAYLOAD.getName();
  }

  @Override
  public int getVersion() {
    return 3;
  }

  @Override
  public SafeFuture<GetPayloadResponse> execute(final JsonRpcRequestParams params) {
    final ExecutionPayloadContext executionPayloadContext =
        params.getRequiredParameter(0, ExecutionPayloadContext.class);
    final UInt64 slot = params.getRequiredParameter(1, UInt64.class);

    LOG.trace(
        "Calling {}(payloadId={}, slot={})",
        getVersionedName(),
        executionPayloadContext.getPayloadId(),
        slot);

    return executionEngineClient
        .getPayloadV3(executionPayloadContext.getPayloadId())
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(
            response -> {
              final SchemaDefinitions schemaDefinitions = spec.atSlot(slot).getSchemaDefinitions();
              final ExecutionPayloadSchema<?> payloadSchema =
                  SchemaDefinitionsBellatrix.required(schemaDefinitions)
                      .getExecutionPayloadSchema();
              final BlobsBundle blobsBundle = getBlobsBundle(response, schemaDefinitions);
              return (GetPayloadResponse)
                  new GetPayloadResponseDeneb(
                      response.executionPayload.asInternalExecutionPayload(payloadSchema),
                      response.blockValue,
                      blobsBundle);
            })
        .thenPeek(
            payloadAndValue ->
                LOG.trace(
                    "Response {}(payloadId={}, slot={}) -> {}",
                    getVersionedName(),
                    executionPayloadContext.getPayloadId(),
                    slot,
                    payloadAndValue));
  }

  private BlobsBundle getBlobsBundle(
      final GetPayloadV3Response response, final SchemaDefinitions schemaDefinitions) {
    return schemaDefinitions
        .toVersionDeneb()
        .map(SchemaDefinitionsDeneb::getBlobSchema)
        .flatMap(
            blobSchema ->
                Optional.ofNullable(response.blobsBundle)
                    .map(blobsBundleV1 -> blobsBundleV1.asInternalBlobsBundle(blobSchema)))
        .orElse(BlobsBundle.EMPTY_BUNDLE);
  }
}
