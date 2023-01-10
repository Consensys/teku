/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.protoarray;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public interface BlockMetadataStore {

  boolean contains(Bytes32 blockRoot);

  void processHashesInChain(final Bytes32 head, NodeProcessor processor);

  void processHashesInChainWhile(Bytes32 head, HaltableNodeProcessor nodeProcessor);

  void processAllInOrder(NodeProcessor nodeProcessor);

  Optional<UInt64> blockSlot(Bytes32 blockRoot);

  Optional<Bytes32> executionBlockHash(Bytes32 beaconBlockRoot);

  Optional<SlotAndBlockRoot> findCommonAncestor(Bytes32 root1, Bytes32 root2);

  void applyUpdate(
      Collection<BlockAndCheckpoints> addedBlocks,
      Collection<Bytes32> pulledUpBlocks,
      Map<Bytes32, UInt64> removedBlocks,
      Checkpoint finalizedCheckpoint);

  interface HaltableNodeProcessor {

    static HaltableNodeProcessor fromNodeProcessor(final NodeProcessor processor) {
      return (child, slot, parent, executionHash) -> {
        processor.process(child, slot, parent);
        return true;
      };
    }

    /**
     * Process parent and child and return a status indicating whether to continue
     *
     * @param childRoot The child root
     * @param slot The child slot
     * @param parentRoot The parent root
     * @param executionHash the child execution payload hash or {@link Bytes32#ZERO} if there is no
     *     payload or it's the default payload.
     * @return True if processing should continue, false if processing should halt
     */
    boolean process(Bytes32 childRoot, UInt64 slot, Bytes32 parentRoot, Bytes32 executionHash);
  }

  interface NodeProcessor {

    /**
     * Process parent and child roots
     *
     * @param childRoot The child root
     * @param slot The child slot
     * @param parentRoot The parent root
     */
    void process(Bytes32 childRoot, UInt64 slot, Bytes32 parentRoot);
  }
}
