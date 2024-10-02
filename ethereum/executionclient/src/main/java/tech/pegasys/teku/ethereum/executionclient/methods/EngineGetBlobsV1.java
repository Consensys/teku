/*
 * Copyright Consensys Software Inc., 2024
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
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndProof;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class EngineGetBlobsV1 extends AbstractEngineJsonRpcMethod<List<BlobAndProof>> {

  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;

  public EngineGetBlobsV1(final ExecutionEngineClient executionEngineClient, final Spec spec) {
    super(executionEngineClient);
    this.spec = spec;
  }

  @Override
  public String getName() {
    return EngineApiMethod.ENGINE_GET_BLOBS.getName();
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public boolean isOptional() {
    return true;
  }

  @Override
  public SafeFuture<List<BlobAndProof>> execute(final JsonRpcRequestParams params) {

    final List<VersionedHash> blobVersionedHashes =
        params.getRequiredListParameter(0, VersionedHash.class);

    final UInt64 slot = params.getRequiredParameter(1, UInt64.class);

    LOG.trace(
        "Calling {}(blobVersionedHashes={}, slot={})",
        getVersionedName(),
        blobVersionedHashes,
        slot);

    return executionEngineClient
        .getBlobsV1(blobVersionedHashes)
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(
            response -> {
              final SchemaDefinitions schemaDefinitions = spec.atSlot(slot).getSchemaDefinitions();
              final BlobSchema blobSchema =
                  SchemaDefinitionsDeneb.required(schemaDefinitions).getBlobSchema();
              return response.stream()
                  .map(
                      blobAndProofV1 ->
                          blobAndProofV1 == null
                              ? null
                              : blobAndProofV1.asInternalBlobsAndProofs(blobSchema))
                  .toList();
            })
        .thenPeek(
            blobsAndProofs ->
                LOG.trace(
                    "Response {}(blobVersionedHashes={}) -> {}",
                    getVersionedName(),
                    blobVersionedHashes,
                    blobsAndProofs));
  }
}
