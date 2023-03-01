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
import tech.pegasys.teku.ethereum.executionclient.schema.BlobsBundleV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsBundle;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class EngineGetBlobsBundleV1 extends AbstractEngineJsonRpcMethod<BlobsBundle> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;

  public EngineGetBlobsBundleV1(
      final ExecutionEngineClient executionEngineClient, final Spec spec) {
    super(executionEngineClient);
    this.spec = spec;
  }

  @Override
  public String getName() {
    return "engine_getBlobsBundle";
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public SafeFuture<BlobsBundle> execute(final JsonRpcRequestParams params) {
    final Bytes8 payloadId = params.getRequiredParameter(0, Bytes8.class);
    final UInt64 slot = params.getRequiredParameter(1, UInt64.class);

    if (!spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
      throw new IllegalArgumentException(
          String.format(
              "Pre-Deneb execution client handler is called to get Deneb BlobsBundleV1 for payload `%s`, slot %s",
              payloadId, slot));
    }
    LOG.trace("calling engineGetBlobsBundle(payloadId={}, slot={})", payloadId, slot);
    return executionEngineClient
        .getBlobsBundleV1(payloadId)
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenCombine(
            SafeFuture.of(
                () ->
                    SchemaDefinitionsDeneb.required(spec.atSlot(slot).getSchemaDefinitions())
                        .getBlobSchema()),
            BlobsBundleV1::asInternalBlobsBundle)
        .thenPeek(
            blobsBundle ->
                LOG.trace(
                    () ->
                        String.format(
                            "engineGetBlobsBundle(payloadId=%s, slot=%s) -> %s",
                            payloadId, slot, blobsBundle.toBriefBlobsString())));
  }
}
