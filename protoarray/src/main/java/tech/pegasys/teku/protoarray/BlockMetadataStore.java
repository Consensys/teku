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

package tech.pegasys.teku.protoarray;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface BlockMetadataStore {

  boolean contains(Bytes32 blockRoot);

  void processHashesInChain(final Bytes32 head, NodeProcessor processor);

  void processHashesInChainWhile(Bytes32 head, HaltableNodeProcessor nodeProcessor);

  void processAllInOrder(NodeProcessor nodeProcessor);

  Optional<UInt64> blockSlot(Bytes32 blockRoot);

  BlockMetadataStore applyUpdate(
      Collection<BlockAndCheckpointEpochs> addedBlocks,
      Set<Bytes32> removedBlocks,
      Checkpoint finalizedCheckpoint);

  interface HaltableNodeProcessor {

    static HaltableNodeProcessor fromNodeProcessor(final NodeProcessor processor) {
      return (child, slot, parent) -> {
        processor.process(child, slot, parent);
        return true;
      };
    }

    /**
     * Process parent and child and return a status indicating whether to continue
     *
     * @param childRoot The child root
     * @param parentRoot The parent root
     * @return True if processing should continue, false if processing should halt
     */
    boolean process(Bytes32 childRoot, UInt64 slot, Bytes32 parentRoot);
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
