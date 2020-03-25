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

package tech.pegasys.artemis.util.backing.tree;

import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.BranchNodeImpl;
import tech.pegasys.artemis.util.backing.type.CompositeViewType;

public class CachedBranchNode extends BranchNodeImpl {

  public static TreeNode cacheNode(CompositeViewType type, TreeNode node) {
    if (node instanceof CachedBranchNode) {
      return node;
    } else if (!(node instanceof BranchNode)) {
      return node;
    } else {
      BranchNode branchNode = (BranchNode) node;
      return new CachedBranchNode(type, branchNode.left(), branchNode.right());
    }
  }

  private final TreeNode[] cache = new TreeNode[32 * 1024];
  private final long minCachedTarget;
  //  private final Map<Long, TreeNode> cache = new ConcurrentHashMap<>();
  //  private final AtomicLong getCount = new AtomicLong();
  //  private final AtomicLong missCount = new AtomicLong();

  public CachedBranchNode(CompositeViewType type, TreeNode left, TreeNode right) {
    super(left, right);
    minCachedTarget = type.getGeneralizedIndex(0);
  }

  @NotNull
  @Override
  public TreeNode get(long target) {
    if (target == 1) return this;
    if (target < minCachedTarget) {
      return CachedBranchNode.super.get(target);
    } else {
      TreeNode cachedNode = cache[(int) (target - minCachedTarget)];
      if (cachedNode == null) {
        cachedNode = CachedBranchNode.super.get(target);
        cache[(int) (target - minCachedTarget)] = cachedNode;
      }
      return cachedNode;
    }
    //    getCount.incrementAndGet();
    //    return cache.computeIfAbsent(
    //        target,
    //        idx -> {
    //          missCount.incrementAndGet();
    //          return CachedBranchNode.super.get(idx);
    //        });
  }
}
