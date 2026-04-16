/*
 * Copyright Consensys Software Inc., 2026
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

  /**
   * Walks the parent chain from blockRoot to find the ancestor node at the given slot.
   *
   * <p>This mirrors the Gloas {@code get_ancestor(...)} helper, which returns a full {@link
   * ForkChoiceNode} identity.
   */
  default Optional<ForkChoiceNode> getAncestorNode(final Bytes32 blockRoot, final UInt64 slot) {
    return getAncestor(blockRoot, slot).map(ForkChoiceNode::createBase);
  }

  Optional<SlotAndBlockRoot> findCommonAncestor(Bytes32 blockRoot1, Bytes32 blockRoot2);

  List<Bytes32> getBlockRootsAtSlot(UInt64 slot);

  /**
   * Returns the current terminal fork-choice heads.
   *
   * <p>This is a node-facing API. In pre-Gloas it returns the base block nodes. In the Gloas
   * three-state tree it may return EMPTY/FULL nodes for the same block root, because those are the
   * actual candidate heads selected by the modified {@code get_head(...)} spec logic.
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#modified-get_head
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-get_node_children
   */
  default List<ProtoNodeData> getChainHeads() {
    return getChainHeads(false);
  }

  List<ProtoNodeData> getChainHeads(boolean includeNonViableHeads);

  Optional<Bytes32> getOptimisticallySyncedTransitionBlockRoot(Bytes32 head);

  List<ProtoNodeData> getBlockData();

  boolean contains(Bytes32 blockRoot);

  Optional<Boolean> isOptimistic(Bytes32 blockRoot);

  boolean isFullyValidated(final Bytes32 blockRoot);

  Optional<ProtoNodeData> getBlockData(Bytes32 blockRoot);

  default Optional<ProtoNodeData> getNodeData(final ForkChoiceNode node) {
    return getBlockData(node.blockRoot(), node.payloadStatus());
  }

  /**
   * Gets block data for a specific node identity (blockRoot + payloadStatus). In the Gloas
   * three-state tree, the same blockRoot may have multiple nodes (PENDING, EMPTY, FULL). This
   * method resolves to the correct node based on the payload status.
   *
   * <p>This is the read-side mirror of the Gloas spec helpers that distinguish the canonical block
   * root from the EMPTY/FULL child returned by `get_node_children(...)` and selected by
   * `get_head(...)`:
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-get_node_children
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#modified-get_head
   *
   * <p>Default: delegates to {@link #getBlockData(Bytes32)}, ignoring payloadStatus.
   */
  default Optional<ProtoNodeData> getBlockData(
      final Bytes32 blockRoot, final ForkChoicePayloadStatus payloadStatus) {
    return getBlockData(blockRoot);
  }

  Optional<UInt64> getWeight(Bytes32 blockRoot);
}
