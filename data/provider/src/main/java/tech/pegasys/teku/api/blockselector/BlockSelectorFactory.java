/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.api.blockselector;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.util.config.Constants;

public class BlockSelectorFactory {
  private final CombinedChainDataClient client;

  public BlockSelectorFactory(final CombinedChainDataClient combinedChainDataClient) {
    this.client = combinedChainDataClient;
  }

  /**
   * Default parsing of the slot parameter to determine the block to return - "head" - the head
   * block - "genesis" - the genesis block - "finalized" - the block in effect at the last slot of
   * the finalized epoch - 0x00 - the block root (bytes32) to return - {UINT64} - a specific slot to
   * retrieve a block from
   *
   * @param selectorMethod
   * @return the selector for the requested string
   */
  public BlockSelector defaultBlockSelector(final String selectorMethod) {
    if (selectorMethod.startsWith("0x")) {
      try {
        return forBlockRoot(Bytes32.fromHexString(selectorMethod));
      } catch (IllegalArgumentException e) {
        throw new BadRequestException("Invalid block: " + selectorMethod);
      }
    }
    switch (selectorMethod) {
      case ("head"):
        return headSelector();
      case ("genesis"):
        return genesisSelector();
      case ("finalized"):
        return finalizedSelector();
    }
    try {
      return forSlot(UInt64.valueOf(selectorMethod));
    } catch (NumberFormatException ex) {
      throw new BadRequestException("Invalid block: " + selectorMethod);
    }
  }

  public BlockSelector headSelector() {
    return () -> optionalToList(SafeFuture.completedFuture(client.getBestBlock()));
  }

  public BlockSelector finalizedSelector() {
    return () -> optionalToList(SafeFuture.completedFuture(client.getFinalizedBlock()));
  }

  public BlockSelector genesisSelector() {
    return () -> optionalToList(client.getBlockAtSlotExact(UInt64.valueOf(Constants.GENESIS_SLOT)));
  }

  public BlockSelector forSlot(final UInt64 slot) {
    return () -> optionalToList(client.getBlockAtSlotExact(slot));
  }

  public BlockSelector forBlockRoot(final Bytes32 blockRoot) {
    return () -> optionalToList(client.getBlockByBlockRoot(blockRoot));
  }

  private SafeFuture<List<SignedBeaconBlock>> optionalToList(
      final SafeFuture<Optional<SignedBeaconBlock>> future) {
    return future.thenApply(
        maybeBlock -> maybeBlock.map(List::of).orElseGet(Collections::emptyList));
  }
}
