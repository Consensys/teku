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

package tech.pegasys.teku.ssz.backing.tree;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.LEFTMOST_G_INDEX;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.RIGHTMOST_G_INDEX;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxIsSelf;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.ssz.backing.type.ViewType;

public class SszNodeTemplate {

  public static final class Location {
    public final int offset;
    public final int length;
    public final boolean leaf;

    public Location(int offset, int length, boolean leaf) {
      this.offset = offset;
      this.length = length;
      this.leaf = leaf;
    }
  }

  public static SszNodeTemplate createFromType(ViewType viewType) {
    checkArgument(viewType.isFixedSize(), "Only fixed size containers supported");

    return createFromTree(viewType.getDefaultTree());
  }

  // This should be CANONICAL binary tree
  public static SszNodeTemplate createFromTree(TreeNode defaultTree) {
    IdentityHashMap<TreeNode, Location> nodeToLoc = new IdentityHashMap<>();
    calcOffsets(0, defaultTree, GIndexUtil.SELF_G_INDEX, nodeToLoc);
    return new SszNodeTemplate(nodeToLoc, defaultTree);
  }

  private static Location calcOffsets(
      int offset, TreeNode node, long thisGIdx, IdentityHashMap<TreeNode, Location> nodeToLoc) {
    final Location nodeLocation;
    if (node instanceof LeafNode) {
      nodeLocation = new Location(offset, ((LeafNode) node).getData().size(), true);
    } else if (node instanceof BranchNode) {
      BranchNode branchNode = (BranchNode) node;
      Location leftLoc =
          calcOffsets(offset, branchNode.left(), GIndexUtil.gIdxLeftGIndex(thisGIdx), nodeToLoc);
      Location rightLoc =
          calcOffsets(
              offset + leftLoc.length,
              branchNode.right(),
              GIndexUtil.gIdxRightGIndex(thisGIdx),
              nodeToLoc);
      nodeLocation = new Location(offset, leftLoc.length + rightLoc.length, false);
    } else {
      throw new IllegalArgumentException(
          "Canonical binary tree (only LeafNode and BranchNode instances) expected here");
    }
    nodeToLoc.put(node, nodeLocation);
    return nodeLocation;
  }

  private static List<Bytes> nodeSsz(TreeNode node) {
    List<Bytes> sszBytes = new ArrayList<>();
    TreeUtil.iterateLeavesData(node, LEFTMOST_G_INDEX, RIGHTMOST_G_INDEX, sszBytes::add);
    return sszBytes;
  }

  private final IdentityHashMap<TreeNode, Location> nodeToLoc;
  private final TreeNode defaultTree;
  private final Map<Long, SszNodeTemplate> subTemplatesCache = new ConcurrentHashMap<>();

  public SszNodeTemplate(
      IdentityHashMap<TreeNode, Location> nodeToLoc,
      TreeNode defaultTree) {
    this.nodeToLoc = nodeToLoc;
    this.defaultTree = defaultTree;
  }

  public Location getNodeSszLocation(long generalizedIndex) {
    return nodeToLoc.get(defaultTree.get(generalizedIndex));
  }

  public int getSszLength() {
    return nodeToLoc.get(defaultTree).length;
  }

  public SszNodeTemplate getSubTemplate(long generalizedIndex) {
    return subTemplatesCache.computeIfAbsent(generalizedIndex, this::calcSubTemplate);
  }

  private SszNodeTemplate calcSubTemplate(long generalizedIndex) {
    if (gIdxIsSelf(generalizedIndex)) {
      return this;
    }
    TreeNode subTree = defaultTree.get(generalizedIndex);
    IdentityHashMap<TreeNode, Location> nodeToLocSubMap = new IdentityHashMap<>();
    subTree.iterateAll(n -> nodeToLocSubMap.put(n, nodeToLoc.get(n)));
    return new SszNodeTemplate(nodeToLocSubMap, subTree);
  }

  public void update(long generalizedIndex, TreeNode newNode, MutableBytes dest) {
    update(generalizedIndex, nodeSsz(newNode), dest);
  }

  private void update(long generalizedIndex, List<Bytes> nodeSsz, MutableBytes dest) {
    // sub-optimal update implementation
    // implement other method to optimize
    Location leafPos = getNodeSszLocation(generalizedIndex);
    int off = 0;
    for (int i = 0; i < nodeSsz.size(); i++) {
      Bytes newSszChunk = nodeSsz.get(i);
      newSszChunk.copyTo(dest, leafPos.offset + off);
      off += newSszChunk.size();
    }
    checkArgument(off == leafPos.length);
  }

  public Bytes32 calculateHashTreeRoot(Bytes ssz, int offset) {
    return calcHash(ssz, defaultTree, offset);
  }

  private Bytes32 calcHash(Bytes ssz, TreeNode node, int offset) {
    if (node instanceof LeafNode) {
      Location location = nodeToLoc.get(node);
      return Bytes32.rightPad(ssz.slice(offset + location.offset, location.length));
    } else {
      BranchNode branchNode = (BranchNode) node;
      return Hash.sha2_256(
          Bytes.wrap(
              calcHash(ssz, branchNode.left(), offset), calcHash(ssz, branchNode.right(), offset)));
    }
  }
}
