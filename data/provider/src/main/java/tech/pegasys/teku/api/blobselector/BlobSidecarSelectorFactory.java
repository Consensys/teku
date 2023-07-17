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

package tech.pegasys.teku.api.blobselector;

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.AbstractSelectorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlobSidecarSelectorFactory extends AbstractSelectorFactory<BlobSidecarSelector> {

  public BlobSidecarSelectorFactory(final CombinedChainDataClient client) {
    super(client);
  }

  @Override
  public BlobSidecarSelector blockRootSelector(final Bytes32 blockRoot) {
    return indices ->
        client
            .getFinalizedSlotByBlockRoot(blockRoot)
            .thenCompose(
                maybeSlot -> {
                  if (maybeSlot.isPresent()) {
                    final SlotAndBlockRoot slotAndBlockRoot =
                        new SlotAndBlockRoot(maybeSlot.get(), blockRoot);
                    return getBlobSidecars(slotAndBlockRoot, indices);
                  }
                  return client
                      .getBlockByBlockRoot(blockRoot)
                      .thenCompose(maybeBlock -> getBlobSidecarsForBlock(maybeBlock, indices));
                });
  }

  @Override
  public BlobSidecarSelector headSelector() {
    return indices ->
        client
            .getChainHead()
            .map(head -> getBlobSidecars(head.getSlotAndBlockRoot(), indices))
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  @Override
  public BlobSidecarSelector genesisSelector() {
    return indices ->
        client
            .getBlockAtSlotExact(GENESIS_SLOT)
            .thenCompose(maybeGenesisBlock -> getBlobSidecarsForBlock(maybeGenesisBlock, indices));
  }

  @Override
  public BlobSidecarSelector finalizedSelector() {
    return indices ->
        client
            .getLatestFinalized()
            .map(anchorPoint -> getBlobSidecars(anchorPoint.getSlotAndBlockRoot(), indices))
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  @Override
  public BlobSidecarSelector slotSelector(final UInt64 slot) {
    return indices -> {
      if (client.isFinalized(slot)) {
        return getBlobSidecars(slot, indices);
      }
      return client
          .getBlockAtSlotExact(slot)
          .thenCompose(maybeBlock -> getBlobSidecarsForBlock(maybeBlock, indices));
    };
  }

  private SafeFuture<Optional<List<BlobSidecar>>> getBlobSidecarsForBlock(
      final Optional<SignedBeaconBlock> maybeBlock, final List<UInt64> indices) {
    if (maybeBlock.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    final SignedBeaconBlock block = maybeBlock.get();
    return getBlobSidecars(block.getSlotAndBlockRoot(), indices);
  }

  private SafeFuture<Optional<List<BlobSidecar>>> getBlobSidecars(
      final SlotAndBlockRoot slotAndBlockRoot, final List<UInt64> indices) {
    return client.getBlobSidecars(slotAndBlockRoot, indices).thenApply(Optional::of);
  }

  private SafeFuture<Optional<List<BlobSidecar>>> getBlobSidecars(
      final UInt64 slot, final List<UInt64> indices) {
    return client.getBlobSidecars(slot, indices).thenApply(Optional::of);
  }
}
