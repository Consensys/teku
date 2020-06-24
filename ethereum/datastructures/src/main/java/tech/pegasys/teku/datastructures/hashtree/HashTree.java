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

package tech.pegasys.teku.datastructures.hashtree;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;

/** A tree where each node is identified by a Bytes32 hash */
public class HashTree {

  private final Bytes32 rootHash;
  private final Map<Bytes32, HashTreeNode> treeNodes;
  private final Map<Bytes32, Bytes32> childToParent;

  private HashTree(
      final Bytes32 rootHash,
      final Map<Bytes32, HashTreeNode> treeNodes,
      final Map<Bytes32, Bytes32> childToParent) {
    this.rootHash = rootHash;
    this.treeNodes = treeNodes;
    this.childToParent = childToParent;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder updater() {
    return withRoot(rootHash);
  }

  /**
   * Create a new tree rooted at the given hash, pruning any nodes that do not descend from the new
   * root.
   *
   * @param newRootHash The new root of the tree
   * @return A new {@code HashTree} containing only nodes that descend from the new root.
   */
  public Builder withRoot(final Bytes32 newRootHash) {
    checkArgument(containsBlock(newRootHash), "Unknown hash provided");

    Builder builder = builder().rootHash(newRootHash);
    preOrderStream(newRootHash)
        .forEach(
            root -> {
              final HashTreeNode treeNode = treeNodes.get(root);
              builder.putTreeNode(root, treeNode);
              getParent(root).ifPresent(parent -> builder.childAndParentRoots(root, parent));
            });

    return builder;
  }

  public Bytes32 getRootHash() {
    return rootHash;
  }

  public Optional<Bytes32> getParent(final Bytes32 childRoot) {
    return Optional.ofNullable(childToParent.get(childRoot));
  }

  /**
   * Process each node in the chain defined by {@code headRoot}
   *
   * @param headRoot The root defining the head of the chain to process
   * @param processor The callback to invoke for each child-parent pair
   */
  public void processHashesInChain(final Bytes32 headRoot, NodeProcessor processor) {
    processChainNodes(headRoot, HaltableNodeProcessor.fromNodeProcessor(processor));
  }

  /**
   * Accumulate the list of roots from the provided head hash up to the root hash. Stops
   * accumulating roots when {@code shouldContinue} returns false.
   *
   * @param headRoot The root defining the head of the chain to construct
   * @param shouldContinue The condition determining when to stop collecting ancestor roots
   * @return A list of roots in ascending order belonging to the chain defined by {@code headRoot}
   */
  public List<Bytes32> collectChainRoots(
      final Bytes32 headRoot, Function<Bytes32, Boolean> shouldContinue) {
    final Deque<Bytes32> chain = new ArrayDeque<>();
    processChainNodes(
        headRoot,
        (child, parent) -> {
          chain.addFirst(child);
          return shouldContinue.apply(child);
        });
    return new ArrayList<>(chain);
  }

  private void processChainNodes(final Bytes32 headRoot, HaltableNodeProcessor nodeProcessor) {
    checkArgument(containsBlock(headRoot), "Unknown root supplied: " + headRoot);

    Optional<Bytes32> currentRoot = Optional.of(headRoot);
    Optional<Bytes32> parentRoot = currentRoot.flatMap(this::getParent);
    while (parentRoot.isPresent()) {
      final boolean shouldContinue = nodeProcessor.process(currentRoot.get(), parentRoot.get());
      if (!shouldContinue) {
        break;
      }
      currentRoot = parentRoot;
      parentRoot = currentRoot.flatMap(this::getParent);
    }
  }

  public int countChildren(final Bytes32 hash) {
    return Optional.of(treeNodes.get(hash)).map(HashTreeNode::childCount).orElse(0);
  }

  public boolean containsBlock(final Bytes32 blockRoot) {
    return treeNodes.containsKey(blockRoot);
  }

  public int getBlockCount() {
    return treeNodes.size();
  }

  /** @return A stream of all tree nodes in pre-order traversal order */
  public Stream<Bytes32> preOrderStream() {
    return preOrderStream(rootHash);
  }

  private Stream<Bytes32> preOrderStream(final Bytes32 rootNodeHash) {
    final HashTreeNode rootNode = treeNodes.get(rootNodeHash);
    if (rootNode == null) {
      return Stream.empty();
    }
    return iteratorToStream(PreOrderTraversalTreeIterator.create(rootNode));
  }

  private Stream<Bytes32> iteratorToStream(final Iterator<Bytes32> iterator) {
    final Spliterator<Bytes32> split =
        Spliterators.spliteratorUnknownSize(
            iterator,
            Spliterator.IMMUTABLE
                | Spliterator.DISTINCT
                | Spliterator.NONNULL
                | Spliterator.ORDERED
                | Spliterator.SORTED);

    return StreamSupport.stream(split, false);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof HashTree)) {
      return false;
    }
    HashTree hashTree = (HashTree) o;
    return Objects.equals(rootHash, hashTree.rootHash)
        && Objects.equals(childToParent, hashTree.childToParent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rootHash, childToParent);
  }

  public static class Builder {
    private Bytes32 rootHash = null;
    private final Map<Bytes32, Bytes32> childToParentMap = new HashMap<>();

    // Cached nodes that may or may not belong in the new tree
    private final Map<Bytes32, HashTreeNode> existingTreeNodes = new HashMap<>();

    public HashTree build() {
      assertValid();

      // Save child / parent mappings
      final Map<Bytes32, Set<Bytes32>> parentToChildLookup = new HashMap<>();
      for (Entry<Bytes32, Bytes32> childToParent : childToParentMap.entrySet()) {
        final Set<Bytes32> childSet =
            parentToChildLookup.computeIfAbsent(childToParent.getValue(), (key) -> new HashSet<>());
        childSet.add(childToParent.getKey());
      }

      // Build HashTree, pruning roots that don't descend from the rootHash
      final Map<Bytes32, Bytes32> prunedChildToParentMap = new HashMap<>();
      final Deque<Bytes32> toProcess = new ArrayDeque<>();
      toProcess.push(rootHash);
      final Set<Bytes32> childrenProcessed = new HashSet<>();
      final Map<Bytes32, HashTreeNode> nodes = new HashMap<>();
      while (!toProcess.isEmpty()) {
        final Bytes32 currentRoot = toProcess.pop();
        final Set<Bytes32> childRoots =
            Optional.ofNullable(parentToChildLookup.get(currentRoot))
                .orElse(Collections.emptySet());
        if (childrenProcessed.contains(currentRoot) || childRoots.isEmpty()) {
          // Build node
          final Set<HashTreeNode> childNodes =
              childRoots.stream().map(nodes::get).collect(Collectors.toSet());
          checkState(childNodes.size() == childRoots.size(), "Failed to find required child nodes");

          final HashTreeNode currentNode =
              Optional.ofNullable(existingTreeNodes.get(currentRoot))
                  .filter(existingNode -> existingNode.childCount() == childNodes.size())
                  .filter(existingNode -> existingNode.getChildren().containsAll(childNodes))
                  .orElseGet(() -> new HashTreeNode(currentRoot, childNodes));
          childNodes.forEach(child -> prunedChildToParentMap.put(child.getHash(), currentRoot));
          nodes.put(currentRoot, currentNode);
        } else {
          // Push node back onto the stack with children
          toProcess.push(currentRoot);
          childRoots.forEach(toProcess::push);
          childrenProcessed.add(currentRoot);
        }
      }

      // Save root parent
      prunedChildToParentMap.put(rootHash, childToParentMap.get(rootHash));
      return new HashTree(rootHash, nodes, prunedChildToParentMap);
    }

    private void assertValid() {
      checkNotNull(rootHash, "Must supply a root block");
      checkState(
          childToParentMap.containsKey(rootHash), "Must provide parent information for root");
    }

    public Builder rootHash(final Bytes32 rootBlockHash) {
      checkNotNull(rootBlockHash);
      this.rootHash = rootBlockHash;
      return this;
    }

    public Builder childAndParentRoots(Map<Bytes32, Bytes32> childToParentMap) {
      checkNotNull(childToParentMap);
      this.childToParentMap.putAll(childToParentMap);
      return this;
    }

    public Builder childAndParentRoots(final Bytes32 childRoot, final Bytes32 parentRoot) {
      checkNotNull(childRoot);
      checkNotNull(parentRoot);
      childToParentMap.put(childRoot, parentRoot);
      return this;
    }

    public Builder block(final SignedBeaconBlock block) {
      checkNotNull(block);
      return childAndParentRoots(block.getRoot(), block.getParent_root());
    }

    public Builder blocks(final Collection<SignedBeaconBlock> blocks) {
      checkNotNull(blocks);
      blocks.forEach(this::block);
      return this;
    }

    // Internal setters for building from an existing tree
    void putTreeNode(Bytes32 root, HashTreeNode treeNode) {
      existingTreeNodes.put(root, treeNode);
    }
  }

  public interface NodeProcessor {
    /**
     * Process parent and child roots
     *
     * @param childRoot The child root
     * @param parentRoot The parent root
     */
    void process(final Bytes32 childRoot, final Bytes32 parentRoot);
  }

  private interface HaltableNodeProcessor {

    static HaltableNodeProcessor fromNodeProcessor(final NodeProcessor processor) {
      return (child, parent) -> {
        processor.process(child, parent);
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
    boolean process(final Bytes32 childRoot, final Bytes32 parentRoot);
  }
}
