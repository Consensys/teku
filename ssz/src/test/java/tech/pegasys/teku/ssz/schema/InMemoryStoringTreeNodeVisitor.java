/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.ssz.schema;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.tree.LeafDataNode;
import tech.pegasys.teku.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.ssz.tree.TreeNodeVisitor;

public class InMemoryStoringTreeNodeVisitor implements TreeNodeVisitor, TreeNodeSource {

  private final Map<Bytes32, CompressedBranchInfo> branchNodes = new HashMap<>();
  private final Map<Bytes32, Bytes> leafNodes = new HashMap<>();

  @Override
  public boolean canSkipBranch(final Bytes32 root, final long gIndex) {
    return branchNodes.containsKey(root);
  }

  @Override
  public void onBranchNode(
      final Bytes32 root, final long gIndex, final int depth, final Bytes32[] children) {
    branchNodes.putIfAbsent(
        root, new CompressedBranchInfo(depth, Arrays.copyOf(children, children.length)));
  }

  @Override
  public void onLeafNode(final LeafDataNode node, final long gIndex) {
    if (node.getData().size() > Bytes32.SIZE) {
      leafNodes.putIfAbsent(node.hashTreeRoot(), node.getData());
    }
  }

  @Override
  public Optional<CompressedBranchInfo> loadBranchNode(final Bytes32 rootHash, final long gIndex) {
    return Optional.ofNullable(branchNodes.get(rootHash));
  }

  @Override
  public Bytes loadLeafNode(final Bytes32 rootHash, final long gIndex) {
    return leafNodes.getOrDefault(rootHash, rootHash);
  }
}
