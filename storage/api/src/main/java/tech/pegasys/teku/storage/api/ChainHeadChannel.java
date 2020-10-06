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

package tech.pegasys.teku.storage.api;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.events.VoidReturningChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface ChainHeadChannel extends VoidReturningChannelInterface {
  /**
   * Called each time chain head is updated, and will contain reorg context if appropriate
   *
   * <p>A Re-rorg is not flagged when the initial best block is set at startup or when the chain
   * advances on the same fork.
   *
   * @param slot the slot of the new chain head
   * @param stateRoot the state root of the state containing the new chain head
   * @param bestBlockRoot the block root of the new chain head
   * @param epochTransition if a new epoch has begun
   * @param optionalReorgContext Roots and common ancestor if a re-org occured
   */
  void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
      Optional<ReorgContext> optionalReorgContext);
}
