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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobsBundleV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsBundle;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip4844;

public class Eip4844ExecutionClientHandler extends CapellaExecutionClientHandler
    implements ExecutionClientHandler {
  private static final Logger LOG = LogManager.getLogger();

  public Eip4844ExecutionClientHandler(
      final Spec spec, final ExecutionEngineClient executionEngineClient) {
    super(spec, executionEngineClient);
  }

  @Override
  public SafeFuture<BlobsBundle> engineGetBlobsBundle(final Bytes8 payloadId, final UInt64 slot) {
    if (!spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.EIP4844)) {
      return super.engineGetBlobsBundle(payloadId, slot);
    }
    LOG.trace("calling engineGetBlobsBundle(payloadId={}, slot={})", payloadId, slot);
    return executionEngineClient
        .getBlobsBundleV1(payloadId)
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenCombine(
            SafeFuture.of(
                () ->
                    SchemaDefinitionsEip4844.required(spec.atSlot(slot).getSchemaDefinitions())
                        .getBlobSchema()),
            BlobsBundleV1::asInternalBlobsBundle)
        .thenPeek(
            blobsBundle ->
                LOG.trace(
                    "engineGetBlobsBundle(payloadId={}, slot={}) -> {}",
                    payloadId,
                    slot,
                    blobsBundle.toBriefBlobsString()));
  }
}
