/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.api.blockrootselector;

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.AbstractSelectorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlockRootSelectorFactory extends AbstractSelectorFactory<BlockRootSelector> {

  private final Spec spec;

  public BlockRootSelectorFactory(final Spec spec, final CombinedChainDataClient client) {
    super(client);
    this.spec = spec;
  }

  @Override
  public BlockRootSelector blockRootSelector(final Bytes32 blockRoot) {
    return () -> client.getBlockByBlockRoot(blockRoot).thenApply(this::fromBlock);
  }

  @Override
  public BlockRootSelector headSelector() {
    return () ->
        client
            .getChainHead()
            .map(this::fromChainHead)
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  @Override
  public BlockRootSelector finalizedSelector() {
    return () -> {
      final Optional<Bytes32> maybeFinalizedBlockRoot = client.getFinalizedBlockRoot();
      final Optional<UInt64> maybeFinalizedSlot = client.getFinalizedBlockSlot();
      if (maybeFinalizedBlockRoot.isPresent() && maybeFinalizedSlot.isPresent()) {
        return SafeFuture.completedFuture(
            Optional.of(
                lookupBlockRootData(
                    maybeFinalizedBlockRoot.get(),
                    maybeFinalizedSlot.get(),
                    client.isChainHeadOptimistic(),
                    true,
                    true)));
      } else {
        return SafeFuture.completedFuture(
            lookupCanonicalBlockRootData(
                client.getFinalizedBlock(), client.isChainHeadOptimistic()));
      }
    };
  }

  @Override
  public BlockRootSelector genesisSelector() {
    return () ->
        client
            .getBlockRootAtSlotExact(GENESIS_SLOT)
            .thenApply(maybeBlock -> lookupCanonicalBlockRootData(maybeBlock, false, GENESIS_SLOT));
  }

  @Override
  public BlockRootSelector slotSelector(final UInt64 slot) {
    return () -> forSlot(client.getChainHead(), slot);
  }

  private Optional<ObjectAndMetaData<Bytes32>> fromBlock(
      final Optional<SignedBeaconBlock> maybeBlock) {
    final Optional<ChainHead> chainHead = client.getChainHead();
    if (maybeBlock.isEmpty() || chainHead.isEmpty()) {
      return Optional.empty();
    }
    return maybeBlock.map(
        block ->
            lookupBlockRootData(
                block.getRoot(),
                block.getSlot(),
                chainHead.get().isOptimistic(),
                client.isCanonicalBlock(
                    block.getSlot(), block.getRoot(), chainHead.get().getRoot()),
                client.isFinalized(block.getSlot())));
  }

  private SafeFuture<Optional<ObjectAndMetaData<Bytes32>>> fromChainHead(final ChainHead head) {
    return head.getBlock()
        .thenApply(maybeBlock -> lookupCanonicalBlockRootData(maybeBlock, head.isOptimistic()));
  }

  private SafeFuture<Optional<ObjectAndMetaData<Bytes32>>> forSlot(
      final Optional<ChainHead> maybeHead, final UInt64 slot) {
    return maybeHead
        .map(head -> forSlot(head, slot))
        .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  private SafeFuture<Optional<ObjectAndMetaData<Bytes32>>> forSlot(
      final ChainHead head, final UInt64 slot) {
    return client
        .getBlockRootAtSlotExact(slot)
        .thenApply(
            maybeBlockRoot ->
                lookupCanonicalBlockRootData(maybeBlockRoot, head.isOptimistic(), slot));
  }

  private Optional<ObjectAndMetaData<Bytes32>> lookupCanonicalBlockRootData(
      final Optional<Bytes32> maybeBlockRoot, final boolean isOptimistic, final UInt64 slot) {
    return maybeBlockRoot.map(
        blockRoot ->
            lookupBlockRootData(blockRoot, slot, isOptimistic, true, client.isFinalized(slot)));
  }

  private Optional<ObjectAndMetaData<Bytes32>> lookupCanonicalBlockRootData(
      final Optional<SignedBeaconBlock> maybeBlock, final boolean isOptimistic) {
    return maybeBlock.map(
        block ->
            lookupBlockRootData(
                block.getRoot(),
                block.getSlot(),
                isOptimistic,
                true,
                client.isFinalized(block.getSlot())));
  }

  private ObjectAndMetaData<Bytes32> lookupBlockRootData(
      final Bytes32 blockRoot,
      final UInt64 slot,
      final boolean isOptimistic,
      final boolean isCanonical,
      final boolean finalized) {
    return new ObjectAndMetaData<>(
        blockRoot, spec.atSlot(slot).getMilestone(), isOptimistic, isCanonical, finalized);
  }
}
