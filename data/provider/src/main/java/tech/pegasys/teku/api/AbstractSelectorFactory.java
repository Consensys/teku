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

public abstract class AbstractSelectorFactory<T> {

  public static final String HEAD = "head";
  public static final String GENESIS = "genesis";
  public static final String FINALIZED = "finalized";
  public static final String JUSTIFIED = "justified";

  protected CombinedChainDataClient client;

  public AbstractSelectorFactory(final CombinedChainDataClient client) {
    this.client = client;
  }

  /** Parsing of the state_id parameter to determine the selector to return */
  public T createSelectorForStateId(final String stateId) {
    if (isHexString(stateId)) {
      try {
        return stateRootSelector(Bytes32.fromHexString(stateId));
      } catch (final IllegalArgumentException e) {
        throw createBadArgumentException(stateId);
      }
    }
    return createSelectorForKeywordOrSlot(stateId);
  }

  /** Parsing of the block_id parameter to determine the selector to return */
  public T createSelectorForBlockId(final String blockId) {
    if (isHexString(blockId)) {
      try {
        return blockRootSelector(Bytes32.fromHexString(blockId));
      } catch (final IllegalArgumentException e) {
        throw createBadArgumentException(blockId);
      }
    }
    return createSelectorForKeywordOrSlot(blockId);
  }

  public T createSelectorForKeywordOrSlot(final String identifier) {
    switch (identifier) {
      case HEAD:
        return headSelector();
      case GENESIS:
        return genesisSelector();
      case FINALIZED:
        return finalizedSelector();
      case JUSTIFIED:
        return justifiedSelector();
    }
    try {
      return slotSelector(UInt64.valueOf(identifier));
    } catch (final NumberFormatException ex) {
      throw createBadArgumentException(identifier);
    }
  }

  public abstract T stateRootSelector(Bytes32 stateRoot);

  public abstract T blockRootSelector(Bytes32 blockRoot);

  public abstract T headSelector();

  public abstract T genesisSelector();

  public abstract T finalizedSelector();

  public abstract T justifiedSelector();

  public abstract T slotSelector(UInt64 slot);

  private boolean isHexString(final String identifier) {
    return identifier.startsWith("0x");
  }

  private BadRequestException createBadArgumentException(final String identifier) {
    return new BadRequestException("Invalid identifier: " + identifier);
  }
}
