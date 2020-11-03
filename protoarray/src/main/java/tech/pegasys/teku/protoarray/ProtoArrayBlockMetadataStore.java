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

public class ProtoArrayBlockMetadataStore implements BlockMetadataStore {
  private final ProtoArrayForkChoiceStrategy forkChoiceStrategy;

  public ProtoArrayBlockMetadataStore(final ProtoArrayForkChoiceStrategy forkChoiceStrategy) {
    this.forkChoiceStrategy = forkChoiceStrategy;
  }

  @Override
  public boolean contains(final Bytes32 blockRoot) {
    return forkChoiceStrategy.contains(blockRoot);
  }

  @Override
  public void processHashesInChain(final Bytes32 head, final NodeProcessor processor) {
    forkChoiceStrategy.processChain(head, processor);
  }

  @Override
  public void processHashesInChainWhile(
      final Bytes32 head, final HaltableNodeProcessor nodeProcessor) {
    forkChoiceStrategy.processChainWhile(head, nodeProcessor);
  }

  @Override
  public Optional<UInt64> blockSlot(final Bytes32 blockRoot) {
    return forkChoiceStrategy.blockSlot(blockRoot);
  }

  @Override
  public BlockMetadataStore applyUpdate(
      final Collection<BlockAndCheckpointEpochs> addedBlocks,
      final Set<Bytes32> removedBlocks,
      final Checkpoint finalizedCheckpoint) {
    forkChoiceStrategy.applyTransaction(addedBlocks, removedBlocks, finalizedCheckpoint);
    return this;
  }

  @Override
  public void processAllInOrder(final NodeProcessor nodeProcessor) {
    forkChoiceStrategy.processAllInOrder(nodeProcessor);
  }
}
