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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface ReadOnlyForkChoiceStrategy {

  Optional<UInt64> blockSlot(Bytes32 blockRoot);

  Optional<Bytes32> blockParentRoot(Bytes32 blockRoot);

  Optional<Bytes32> getAncestor(Bytes32 blockRoot, UInt64 slot);

  Set<Bytes32> getBlockRootsAtSlot(UInt64 slot);

  /** @return A map from blockRoot to blockSlot for current chain heads */
  Map<Bytes32, UInt64> getChainHeads();

  Map<Bytes32, UInt64> getOptimisticChainHeads();

  boolean contains(Bytes32 blockRoot);
}
