/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.api.executionpayloadselector;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.AbstractSelectorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.metadata.ExecutionPayloadAndMetaData;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class ExecutionPayloadSelectorFactory
    extends AbstractSelectorFactory<ExecutionPayloadSelector> {

  private final Spec spec;

  public ExecutionPayloadSelectorFactory(final Spec spec, final CombinedChainDataClient client) {
    super(client);
    this.spec = spec;
  }

  @Override
  public ExecutionPayloadSelector blockRootSelector(final Bytes32 blockRoot) {
    return () ->
        client
            .getExecutionPayloadByBlockRoot(blockRoot)
            .thenApply(this::lookupExecutionPayloadData);
  }

  @Override
  public ExecutionPayloadSelector headSelector() {
    return () ->
        client
            .getChainHead()
            .map(this::fromChainHead)
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  @Override
  public ExecutionPayloadSelector finalizedSelector() {
    return () ->
        // Finalized checkpoint is always canonical
        client
            .getFinalizedBlock()
            .map(
                finalizedBlock ->
                    client
                        .getExecutionPayloadByBlockRoot(finalizedBlock.getRoot())
                        .thenApply(
                            executionPayload -> {
                              // The finalized checkpoint may change because of optimistically
                              // imported blocks
                              // at the head and if the head isn't optimistic, the finalized block
                              // can't be
                              // optimistic.
                              return lookupCanonicalExecutionPayloadData(
                                  executionPayload, client.isChainHeadOptimistic());
                            }))
            .orElseGet(() -> SafeFuture.completedFuture(Optional.empty()));
  }

  @Override
  public ExecutionPayloadSelector genesisSelector() {
    return () -> SafeFuture.completedFuture(Optional.empty());
  }

  @Override
  public ExecutionPayloadSelector slotSelector(final UInt64 slot) {
    return () -> forSlot(client.getChainHead(), slot);
  }

  private SafeFuture<Optional<ExecutionPayloadAndMetaData>> fromChainHead(final ChainHead head) {
    return head.getBlock()
        .thenCompose(
            maybeBlock -> {
              if (maybeBlock.isEmpty()) {
                return SafeFuture.completedFuture(Optional.empty());
              }
              return client.getExecutionPayloadByBlockRoot(maybeBlock.get().getRoot());
            })
        .thenApply(
            maybeExecutionPayload ->
                lookupCanonicalExecutionPayloadData(maybeExecutionPayload, head.isOptimistic()));
  }

  private SafeFuture<Optional<ExecutionPayloadAndMetaData>> forSlot(
      final Optional<ChainHead> maybeHead, final UInt64 slot) {
    return maybeHead
        .map(head -> forSlot(head, slot))
        .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  private SafeFuture<Optional<ExecutionPayloadAndMetaData>> forSlot(
      final ChainHead head, final UInt64 slot) {
    return client
        .getBlockAtSlotExact(slot, head.getRoot())
        .thenCompose(
            maybeBlock -> {
              if (maybeBlock.isEmpty()) {
                return SafeFuture.completedFuture(Optional.empty());
              }
              return client.getExecutionPayloadByBlockRoot(maybeBlock.get().getRoot());
            })
        .thenApply(
            maybeExecutionPayload ->
                lookupCanonicalExecutionPayloadData(maybeExecutionPayload, head.isOptimistic()));
  }

  private Optional<ExecutionPayloadAndMetaData> lookupExecutionPayloadData(
      final Optional<SignedExecutionPayloadEnvelope> maybeExecutionPayload) {
    // Ensure we use the same chain head when calculating metadata to ensure a consistent view.
    final Optional<ChainHead> chainHead = client.getChainHead();
    if (maybeExecutionPayload.isEmpty() || chainHead.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(lookupExecutionPayloadData(maybeExecutionPayload.get(), chainHead.get()));
  }

  private Optional<ExecutionPayloadAndMetaData> lookupCanonicalExecutionPayloadData(
      final Optional<SignedExecutionPayloadEnvelope> maybeExecutionPayload,
      final boolean isOptimistic) {
    return maybeExecutionPayload.map(
        executionPayload ->
            lookupExecutionPayloadData(
                executionPayload,
                isOptimistic,
                true,
                client.isFinalized(executionPayload.getMessage().getSlot())));
  }

  private ExecutionPayloadAndMetaData lookupExecutionPayloadData(
      final SignedExecutionPayloadEnvelope executionPayload, final ChainHead chainHead) {
    final SlotAndBlockRoot slotAndBlockRoot = executionPayload.getMessage().getSlotAndBlockRoot();
    return lookupExecutionPayloadData(
        executionPayload,
        // If the chain head is optimistic that will "taint" whether the block is canonical
        chainHead.isOptimistic() || client.isOptimisticBlock(slotAndBlockRoot.getBlockRoot()),
        client.isCanonicalBlock(
            slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), chainHead.getRoot()),
        client.isFinalized(slotAndBlockRoot.getSlot()));
  }

  private ExecutionPayloadAndMetaData lookupExecutionPayloadData(
      final SignedExecutionPayloadEnvelope executionPayload,
      final boolean isOptimistic,
      final boolean isCanonical,
      final boolean finalized) {
    return new ExecutionPayloadAndMetaData(
        executionPayload,
        spec.atSlot(executionPayload.getMessage().getSlot()).getMilestone(),
        isOptimistic,
        isCanonical,
        finalized);
  }
}
