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

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;

public interface ReorgEventChannel {

  /**
   * Called each time the chain switches forks.
   *
   * <p>This method is not called when the initial best block is set at startup or when the chain
   * advances on the same fork.
   *
   * @param bestBlockRoot the block root of the new chain head
   * @param bestSlot the slot of the new chain head
   */
  void reorgOccurred(final Bytes32 bestBlockRoot, final UnsignedLong bestSlot);
}
