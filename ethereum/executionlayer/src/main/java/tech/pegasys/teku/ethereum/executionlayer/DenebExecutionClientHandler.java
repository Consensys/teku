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
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsBundle;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class DenebExecutionClientHandler extends CapellaExecutionClientHandler
    implements ExecutionClientHandler {
  private static final Logger LOG = LogManager.getLogger();

  public DenebExecutionClientHandler(
      final Spec spec, final ExecutionEngineClient executionEngineClient) {
    super(spec, executionEngineClient);
  }

  @Override
  public SafeFuture<ExecutionPayloadWithValue> engineGetPayload(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
    LOG.trace(
        "calling engineGetPayloadV3(payloadId={}, slot={})",
        executionPayloadContext.getPayloadId(),
        slot);
    return executionEngineClient
        .getPayloadV3(executionPayloadContext.getPayloadId())
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(
            response -> {
              final ExecutionPayloadSchema<?> payloadSchema =
                  SchemaDefinitionsBellatrix.required(spec.atSlot(slot).getSchemaDefinitions())
                      .getExecutionPayloadSchema();
              return new ExecutionPayloadWithValue(
                  response.executionPayload.asInternalExecutionPayload(payloadSchema),
                  response.blockValue);
            })
        .thenPeek(
            payloadAndValue ->
                LOG.trace(
                    "engineGetPayloadV3(payloadId={}, slot={}) -> {}",
                    executionPayloadContext.getPayloadId(),
                    slot,
                    payloadAndValue));
  }

  @Override
  public SafeFuture<PayloadStatus> engineNewPayload(final ExecutionPayload executionPayload) {
    LOG.trace("calling engineNewPayloadV3(executionPayload={})", executionPayload);
    final ExecutionPayloadV1 executionPayloadV1;
    if (executionPayload.toVersionDeneb().isPresent()) {
      executionPayloadV1 = ExecutionPayloadV3.fromInternalExecutionPayload(executionPayload);
    } else if (executionPayload.toVersionCapella().isPresent()) {
      executionPayloadV1 = ExecutionPayloadV2.fromInternalExecutionPayload(executionPayload);
    } else {
      executionPayloadV1 = ExecutionPayloadV1.fromInternalExecutionPayload(executionPayload);
    }
    return executionEngineClient
        .newPayloadV3(executionPayloadV1)
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(PayloadStatusV1::asInternalExecutionPayload)
        .thenPeek(
            payloadStatus ->
                LOG.trace(
                    "engineNewPayloadV3(executionPayload={}) -> {}",
                    executionPayload,
                    payloadStatus))
        .exceptionally(PayloadStatus::failedExecution);
  }

  @Override
  public SafeFuture<BlobsBundle> engineGetBlobsBundle(final Bytes8 payloadId, final UInt64 slot) {
    if (!spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
      return super.engineGetBlobsBundle(payloadId, slot);
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
