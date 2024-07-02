/*
 * Copyright Consensys Software Inc., 2025
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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

public interface ReadOnlyForkChoiceStrategy {

  Optional<UInt64> blockSlot(Bytes32 blockRoot);

  Optional<Bytes32> blockParentRoot(Bytes32 blockRoot);

  Optional<UInt64> executionBlockNumber(Bytes32 blockRoot);

  Optional<Bytes32> executionBlockHash(Bytes32 blockRoot);

  Optional<Bytes32> getAncestor(Bytes32 blockRoot, UInt64 slot);

  Optional<SlotAndBlockRoot> findCommonAncestor(Bytes32 blockRoot1, Bytes32 blockRoot2);

  List<Bytes32> getBlockRootsAtSlot(UInt64 slot);

  default List<ProtoNodeData> getChainHeads() {
    return getChainHeads(false);
  }

  List<ProtoNodeData> getChainHeads(boolean includeNonViableHeads);

  List<ProtoNodeData> getViableChainHeads();

  Optional<Bytes32> getOptimisticallySyncedTransitionBlockRoot(Bytes32 head);

  List<ProtoNodeData> getBlockData();

  boolean contains(Bytes32 blockRoot);

  Optional<Boolean> isOptimistic(Bytes32 blockRoot);

  boolean isFullyValidated(final Bytes32 blockRoot);

  Optional<ProtoNodeData> getBlockData(Bytes32 blockRoot);

  Optional<UInt64> getWeight(Bytes32 blockRoot);
}
