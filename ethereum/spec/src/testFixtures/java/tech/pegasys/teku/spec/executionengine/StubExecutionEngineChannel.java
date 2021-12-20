/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.executionengine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsMerge;

public class StubExecutionEngineChannel implements ExecutionEngineChannel {

  private final Map<Bytes32, PowBlock> knownBlocks = new ConcurrentHashMap<>();
  private final LRUCache<Bytes8, HeadAndAttributes> payloadIdToHeadAndAttrsCache;
  private final AtomicLong payloadIdCounter = new AtomicLong(0);
  private final Spec spec;

  private ExecutePayloadResult executePayloadResult = ExecutePayloadResult.VALID;

  public StubExecutionEngineChannel(Spec spec) {
    this.payloadIdToHeadAndAttrsCache = LRUCache.create(10);
    this.spec = spec;
  }

  public void addPowBlock(final PowBlock block) {
    knownBlocks.put(block.getBlockHash(), block);
  }

  @Override
  public SafeFuture<Optional<PowBlock>> getPowBlock(final Bytes32 blockHash) {
    return SafeFuture.completedFuture(Optional.ofNullable(knownBlocks.get(blockHash)));
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    return SafeFuture.failedFuture(
        new UnsupportedOperationException("getPowChainHead not supported"));
  }

  @Override
  public SafeFuture<ForkChoiceUpdatedResult> forkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final Optional<PayloadAttributes> payloadAttributes) {
    return SafeFuture.completedFuture(
        new ForkChoiceUpdatedResult(
            ForkChoiceUpdatedStatus.SUCCESS,
            payloadAttributes.map(
                payloadAttributes1 -> {
                  Bytes8 payloadId =
                      Bytes8.leftPad(Bytes.ofUnsignedInt(payloadIdCounter.incrementAndGet()));
                  payloadIdToHeadAndAttrsCache.invalidateWithNewValue(
                      payloadId,
                      new HeadAndAttributes(
                          forkChoiceState.getHeadBlockHash(), payloadAttributes1));
                  return payloadId;
                })));
  }

  @Override
  public SafeFuture<ExecutionPayload> getPayload(final Bytes8 payloadId, final UInt64 slot) {
    Optional<SchemaDefinitionsMerge> schemaDefinitionsMerge =
        spec.atSlot(slot).getSchemaDefinitions().toVersionMerge();

    if (schemaDefinitionsMerge.isEmpty()) {
      return SafeFuture.failedFuture(
          new UnsupportedOperationException("getPayload not supported for non-Merge milestones"));
    }

    Optional<HeadAndAttributes> maybeHeadAndAttrs =
        payloadIdToHeadAndAttrsCache.getCached(payloadId);
    if (maybeHeadAndAttrs.isEmpty()) {
      return SafeFuture.failedFuture(new RuntimeException("payloadId not found in cache"));
    }

    HeadAndAttributes headAndAttrs = maybeHeadAndAttrs.get();
    PayloadAttributes payloadAttributes = headAndAttrs.attributes;

    return SafeFuture.completedFuture(
        schemaDefinitionsMerge
            .get()
            .getExecutionPayloadSchema()
            .create(
                headAndAttrs.head,
                payloadAttributes.getFeeRecipient(),
                Bytes32.ZERO,
                Bytes32.ZERO,
                Bytes.EMPTY,
                payloadAttributes.getRandom(),
                UInt64.valueOf(payloadIdCounter.get()),
                UInt64.ONE,
                UInt64.ZERO,
                payloadAttributes.getTimestamp(),
                Bytes.EMPTY,
                UInt256.ONE,
                Bytes32.random(),
                List.of()));
  }

  @Override
  public SafeFuture<ExecutePayloadResult> executePayload(final ExecutionPayload executionPayload) {
    return SafeFuture.completedFuture(executePayloadResult);
  }

  public ExecutePayloadResult getExecutePayloadResult() {
    return executePayloadResult;
  }

  public void setExecutePayloadResult(ExecutePayloadResult executePayloadResult) {
    this.executePayloadResult = executePayloadResult;
  }

  private static class HeadAndAttributes {
    private final Bytes32 head;
    private final PayloadAttributes attributes;

    private HeadAndAttributes(Bytes32 head, PayloadAttributes attributes) {
      this.head = head;
      this.attributes = attributes;
    }
  }
}
