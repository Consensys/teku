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

package tech.pegasys.teku.api.datacolumnselector;

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.AbstractSelectorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class DataColumnSidecarSelectorFactory
    extends AbstractSelectorFactory<DataColumnSidecarSelector> {

  public DataColumnSidecarSelectorFactory(final CombinedChainDataClient client) {
    super(client);
  }

  @Override
  public DataColumnSidecarSelector blockRootSelector(final Bytes32 blockRoot) {
    return indices ->
        client
            .getFinalizedSlotByBlockRoot(blockRoot)
            .thenCompose(
                maybeSlot -> {
                  if (maybeSlot.isPresent()) {
                    return getDataColumnSidecars(maybeSlot.get(), indices);
                  }
                  return client
                      .getBlockByBlockRoot(blockRoot)
                      .thenCompose(
                          maybeBlock -> getDataColumnSidecarsForBlock(maybeBlock, indices));
                });
  }

  @Override
  public DataColumnSidecarSelector headSelector() {
    return indices ->
        client
            .getChainHead()
            .map(head -> getDataColumnSidecars(head.getSlot(), indices))
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  @Override
  public DataColumnSidecarSelector genesisSelector() {
    return indices ->
        client
            .getBlockAtSlotExact(GENESIS_SLOT)
            .thenCompose(
                maybeGenesisBlock -> getDataColumnSidecarsForBlock(maybeGenesisBlock, indices));
  }

  @Override
  public DataColumnSidecarSelector finalizedSelector() {
    return indices ->
        client
            .getLatestFinalized()
            .map(anchorPoint -> getDataColumnSidecars(anchorPoint.getSlot(), indices))
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  @Override
  public DataColumnSidecarSelector slotSelector(final UInt64 slot) {
    return indices -> getDataColumnSidecars(slot, indices);
  }

  private SafeFuture<Optional<List<DataColumnSidecar>>> getDataColumnSidecarsForBlock(
      final Optional<SignedBeaconBlock> maybeBlock, final List<UInt64> indices) {
    if (maybeBlock.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    final Optional<BeaconBlockBodyDeneb> maybeDenebBlock =
        maybeBlock.get().getMessage().getBody().toVersionDeneb();
    if (maybeDenebBlock.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    if (maybeDenebBlock.get().getBlobKzgCommitments().isEmpty()) {
      return SafeFuture.completedFuture(Optional.of(Collections.emptyList()));
    }
    final SignedBeaconBlock block = maybeBlock.get();
    return getDataColumnSidecars(block.getSlot(), indices);
  }

  private SafeFuture<Optional<List<DataColumnSidecar>>> getDataColumnSidecars(
      final UInt64 slot, final List<UInt64> indices) {
    return client.getDataColumnSidecars(slot, indices).thenApply(Optional::of);
  }
}
