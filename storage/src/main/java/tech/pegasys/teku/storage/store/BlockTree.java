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

package tech.pegasys.teku.storage.store;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.BlockMetadataStore;

public class BlockTree implements BlockMetadataStore {
  private final HashTree hashTree;
  final Map<Bytes32, UInt64> blockRootToSlot;

  private BlockTree(final HashTree hashTree, final Map<Bytes32, UInt64> blockRootToSlot) {
    validate(hashTree, blockRootToSlot);
    this.hashTree = hashTree;
    this.blockRootToSlot = blockRootToSlot;
  }

  public static BlockTree create(
      final HashTree hashTree, final Map<Bytes32, UInt64> blockRootToSlot) {
    return new BlockTree(hashTree, blockRootToSlot);
  }

  private void validate(final HashTree hashTree, final Map<Bytes32, UInt64> blockRootToSlot) {
    checkArgument(
        hashTree.size() == blockRootToSlot.size(),
        "Slot lookup and hash tree must contain the same number of elements");
    checkArgument(
        Sets.difference(hashTree.getAllRoots(), blockRootToSlot.keySet()).isEmpty(),
        "Slot lookup and hash tree must contain the same roots");
  }

  public BlockTree updated(final Bytes32 newRoot, Collection<SignedBeaconBlock> newBlocks) {
    final HashTree updatedHashTree = hashTree.withRoot(newRoot).blocks(newBlocks).build();

    // Create new root to slot mapping
    final Map<Bytes32, UInt64> updatedBlockRootToSlot = new HashMap<>(blockRootToSlot);
    newBlocks.forEach(b -> updatedBlockRootToSlot.put(b.getRoot(), b.getSlot()));
    updatedBlockRootToSlot.keySet().removeIf(next -> !updatedHashTree.contains(next));

    return new BlockTree(updatedHashTree, updatedBlockRootToSlot);
  }

  public HashTree getHashTree() {
    return hashTree;
  }

  public Bytes32 getRootHash() {
    return hashTree.getRootHash();
  }

  @Override
  public boolean contains(final Bytes32 blockRoot) {
    return hashTree.contains(blockRoot);
  }

  @Override
  public void processHashesInChain(final Bytes32 head, final NodeProcessor processor) {
    hashTree.processHashesInChain(
        head, (root, parent) -> processor.process(root, getSlot(root), parent));
  }

  @Override
  public void processHashesInChainWhile(
      final Bytes32 head, final HaltableNodeProcessor nodeProcessor) {
    hashTree.processHashesInChainWhile(
        head, (root, parent) -> nodeProcessor.process(root, getSlot(root), parent));
  }

  @Override
  public void processAllInOrder(final NodeProcessor nodeProcessor) {
    hashTree
        .preOrderStream()
        .forEach(
            blockRoot ->
                nodeProcessor.process(
                    blockRoot, getSlot(blockRoot), hashTree.getParent(blockRoot).orElseThrow()));
  }

  public Set<Bytes32> getAllRoots() {
    return hashTree.getAllRoots();
  }

  /**
   * @return A list of block roots ordered to guarantee that parent roots will be sorted earlier
   *     than child roots
   */
  public List<Bytes32> getOrderedBlockRoots() {
    return hashTree.breadthFirstStream().collect(Collectors.toList());
  }

  @Override
  public BlockMetadataStore applyUpdate(
      final Collection<BlockAndCheckpointEpochs> addedBlocks,
      final Set<Bytes32> removedBlocks,
      final Checkpoint finalizedCheckpoint) {

    if (finalizedCheckpoint.getRoot().equals(hashTree.getRootHash()) && addedBlocks.isEmpty()) {
      // No changes to apply
      return this;
    }
    return updated(
        finalizedCheckpoint.getRoot(),
        addedBlocks.stream()
            .map(BlockAndCheckpointEpochs::getBlock)
            .filter(block -> !removedBlocks.contains(block.getRoot()))
            .collect(Collectors.toList()));
  }

  public UInt64 getEpoch(Bytes32 blockRoot) {
    final UInt64 slot = getSlot(blockRoot);
    return compute_epoch_at_slot(slot);
  }

  @Override
  public Optional<UInt64> blockSlot(Bytes32 blockRoot) {
    return Optional.ofNullable(blockRootToSlot.get(blockRoot));
  }

  public UInt64 getSlot(Bytes32 blockRoot) {
    assertBlockIsInTree(blockRoot);
    return blockRootToSlot.get(blockRoot);
  }

  public int size() {
    return hashTree.size();
  }

  private void assertBlockIsInTree(Bytes32 blockRoot) {
    if (!contains(blockRoot)) {
      throw new IllegalArgumentException(
          "Provided block root is not in the current tree: " + blockRoot);
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof BlockTree)) {
      return false;
    }
    final BlockTree blockTree = (BlockTree) o;
    return Objects.equals(getHashTree(), blockTree.getHashTree())
        && Objects.equals(blockRootToSlot, blockTree.blockRootToSlot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getHashTree(), blockRootToSlot);
  }
}
