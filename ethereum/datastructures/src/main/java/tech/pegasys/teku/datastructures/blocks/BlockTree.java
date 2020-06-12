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

package tech.pegasys.teku.datastructures.blocks;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;

/** Represents a tree of blocks where all block share a common root block. */
public class BlockTree {
  private final SignedBeaconBlock rootBlock;
  private final Map<Bytes32, SignedBeaconBlock> blocks;
  private final Map<Bytes32, Set<SignedBeaconBlock>> parentToChildLookup;

  private BlockTree(
      final SignedBeaconBlock rootBlock,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final Map<Bytes32, Set<SignedBeaconBlock>> parentToChildLookup) {
    this.rootBlock = rootBlock;
    this.blocks = blocks;
    this.parentToChildLookup = parentToChildLookup;
  }

  public static Builder builder() {
    return new Builder();
  }

  public SignedBeaconBlock getRootBlock() {
    return rootBlock;
  }

  public Set<SignedBeaconBlock> getChildren(final SignedBeaconBlock parentBlock) {
    return getChildren(parentBlock.getRoot());
  }

  public Set<SignedBeaconBlock> getChildren(final Bytes32 parentRoot) {
    return Optional.ofNullable(parentToChildLookup.get(parentRoot)).orElse(Collections.emptySet());
  }

  public boolean containsBlock(final Bytes32 blockRoot) {
    return blocks.containsKey(blockRoot);
  }

  public Optional<SignedBeaconBlock> getBlock(final Bytes32 root) {
    return Optional.ofNullable(blocks.get(root));
  }

  public int getBlockCount() {
    return blocks.size();
  }

  public static class Builder {
    private SignedBeaconBlock rootBlock = null;
    private final Map<Bytes32, SignedBeaconBlock> blocks = new HashMap<>();

    public BlockTree build() {
      assertValid();

      // Build initial parent to child lookup
      final Map<Bytes32, Set<SignedBeaconBlock>> tmpParentToChildLookup = new HashMap<>();
      for (SignedBeaconBlock currentBlock : blocks.values()) {
        final Set<SignedBeaconBlock> childSet =
            tmpParentToChildLookup.computeIfAbsent(
                currentBlock.getParent_root(), (key) -> new HashSet<>());
        childSet.add(currentBlock);
      }

      // Prune blocks that don't descend from the root block
      final Map<Bytes32, SignedBeaconBlock> validBlocks = new HashMap<>();
      final Map<Bytes32, Set<SignedBeaconBlock>> validParentToChildLookup = new HashMap<>();
      final Deque<SignedBeaconBlock> toProcess = new ArrayDeque<>();
      toProcess.push(rootBlock);
      while (!toProcess.isEmpty()) {
        final SignedBeaconBlock currentBlock = toProcess.pop();
        validBlocks.put(currentBlock.getRoot(), currentBlock);
        Optional.ofNullable(tmpParentToChildLookup.get(currentBlock.getRoot()))
            .ifPresent(
                (childRoots) -> {
                  validParentToChildLookup.put(currentBlock.getRoot(), childRoots);
                  toProcess.addAll(childRoots);
                });
      }

      return new BlockTree(rootBlock, validBlocks, validParentToChildLookup);
    }

    private void assertValid() {
      checkNotNull(rootBlock, "Must supply a root block");
    }

    public Builder rootBlock(final SignedBeaconBlock rootBlock) {
      checkNotNull(rootBlock);
      this.rootBlock = rootBlock;
      blocks.put(rootBlock.getRoot(), rootBlock);
      return this;
    }

    public Builder block(final SignedBeaconBlock block) {
      checkNotNull(block);
      blocks.put(block.getRoot(), block);
      return this;
    }

    public Builder blocks(final Collection<SignedBeaconBlock> blocks) {
      checkNotNull(blocks);
      blocks.forEach(b -> this.blocks.put(b.getRoot(), b));
      return this;
    }
  }
}
