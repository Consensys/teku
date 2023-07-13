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

package tech.pegasys.teku.api;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public abstract class AbstractBlockIdSelectorFactory<T> {

  public static final String HEAD = "head";
  public static final String GENESIS = "genesis";
  public static final String FINALIZED = "finalized";

  protected CombinedChainDataClient client;

  public AbstractBlockIdSelectorFactory(final CombinedChainDataClient client) {
    this.client = client;
  }

  /** Parsing of the block_id parameter to determine the selector to return */
  public T createSelector(final String blockId) {
    if (blockId.startsWith("0x")) {
      try {
        return forBlockRoot(Bytes32.fromHexString(blockId));
      } catch (final IllegalArgumentException e) {
        throw new BadRequestException("Invalid block id: " + blockId);
      }
    }
    switch (blockId) {
      case HEAD:
        return headSelector();
      case GENESIS:
        return genesisSelector();
      case FINALIZED:
        return finalizedSelector();
    }
    try {
      return forSlot(UInt64.valueOf(blockId));
    } catch (final NumberFormatException ex) {
      throw new BadRequestException("Invalid block id: " + blockId);
    }
  }

  public abstract T forBlockRoot(Bytes32 blockRoot);

  public abstract T headSelector();

  public abstract T genesisSelector();

  public abstract T finalizedSelector();

  public abstract T forSlot(UInt64 slot);
}
