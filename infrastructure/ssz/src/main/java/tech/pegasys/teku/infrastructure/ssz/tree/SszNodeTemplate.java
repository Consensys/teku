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

package tech.pegasys.teku.infrastructure.ssz.tree;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.LEFTMOST_G_INDEX;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.RIGHTMOST_G_INDEX;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.SELF_G_INDEX;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.gIdxIsSelf;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.gIdxLeftGIndex;
import static tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.gIdxRightGIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;

/**
 * Represents the tree structure for a fixed size SSZ type See {@link SszSuperNode} docs for more
 * details
 */
public class SszNodeTemplate {

  static final class Location {
    private final int offset;
    private final int length;
    private final boolean leaf;

    public Location(int offset, int length, boolean leaf) {
      this.offset = offset;
      this.length = length;
      this.leaf = leaf;
    }

    public Location withAddedOffset(int addOffset) {
      return new Location(getOffset() + addOffset, getLength(), isLeaf());
    }

    public int getOffset() {
      return offset;
    }

    public int getLength() {
      return length;
    }

    public boolean isLeaf() {
      return leaf;
    }
  }

  public static SszNodeTemplate createFromType(SszSchema<?> sszSchema) {
    checkArgument(sszSchema.isFixedSize(), "Only fixed size types supported");

    return createFromTree(sszSchema.getDefaultTree());
  }

  // This should be CANONICAL binary tree
  private static SszNodeTemplate createFromTree(TreeNode defaultTree) {
    Map<Long, Location> gIdxToLoc =
        binaryTraverse(
            GIndexUtil.SELF_G_INDEX,
            defaultTree,
            new BinaryVisitor<>() {
              @Override
              public Map<Long, Location> visitLeaf(long gIndex, LeafNode node) {
                Map<Long, Location> ret = new HashMap<>();
                ret.put(gIndex, new Location(0, node.getData().size(), true));
                return ret;
              }

              @Override
              public Map<Long, Location> visitBranch(
                  long gIndex,
                  TreeNode node,
                  Map<Long, Location> leftVisitResult,
                  Map<Long, Location> rightVisitResult) {
                Location leftChildLoc = leftVisitResult.get(gIdxLeftGIndex(gIndex));
                Location rightChildLoc = rightVisitResult.get(gIdxRightGIndex(gIndex));
                rightVisitResult.replaceAll(
                    (idx, loc) -> loc.withAddedOffset(leftChildLoc.getLength()));
                leftVisitResult.putAll(rightVisitResult);
                leftVisitResult.put(
                    gIndex,
                    new Location(0, leftChildLoc.getLength() + rightChildLoc.getLength(), false));
                return leftVisitResult;
              }
            });
    return new SszNodeTemplate(gIdxToLoc, defaultTree);
  }

  private static List<Bytes> nodeSsz(TreeNode node) {
    List<Bytes> sszBytes = new ArrayList<>();
    TreeUtil.iterateLeavesData(node, LEFTMOST_G_INDEX, RIGHTMOST_G_INDEX, sszBytes::add);
    return sszBytes;
  }

  private final Map<Long, Location> gIdxToLoc;
  private final TreeNode defaultTree;
  private final Map<Long, SszNodeTemplate> subTemplatesCache = new ConcurrentHashMap<>();

  public SszNodeTemplate(Map<Long, Location> gIdxToLoc, TreeNode defaultTree) {
    this.gIdxToLoc = gIdxToLoc;
    this.defaultTree = defaultTree;
  }

  public Location getNodeSszLocation(long generalizedIndex) {
    return gIdxToLoc.get(generalizedIndex);
  }

  public int getSszLength() {
    return gIdxToLoc.get(SELF_G_INDEX).getLength();
  }

  public SszNodeTemplate getSubTemplate(long generalizedIndex) {
    return subTemplatesCache.computeIfAbsent(generalizedIndex, this::calcSubTemplate);
  }

  private SszNodeTemplate calcSubTemplate(long generalizedIndex) {
    if (gIdxIsSelf(generalizedIndex)) {
      return this;
    }
    TreeNode subTree = defaultTree.get(generalizedIndex);
    return createFromTree(subTree);
  }

  public void update(long generalizedIndex, TreeNode newNode, MutableBytes dest) {
    update(generalizedIndex, nodeSsz(newNode), dest);
  }

  private void update(long generalizedIndex, List<Bytes> nodeSsz, MutableBytes dest) {
    Location leafPos = getNodeSszLocation(generalizedIndex);
    int off = 0;
    for (int i = 0; i < nodeSsz.size(); i++) {
      Bytes newSszChunk = nodeSsz.get(i);
      newSszChunk.copyTo(dest, leafPos.getOffset() + off);
      off += newSszChunk.size();
    }
    checkArgument(off == leafPos.getLength());
  }

  public Bytes32 calculateHashTreeRoot(Bytes ssz, int offset) {
    return binaryTraverse(
        SELF_G_INDEX,
        defaultTree,
        new BinaryVisitor<>() {
          @Override
          public Bytes32 visitLeaf(long gIndex, LeafNode node) {
            Location location = gIdxToLoc.get(gIndex);
            return Bytes32.rightPad(ssz.slice(offset + location.getOffset(), location.getLength()));
          }

          @Override
          public Bytes32 visitBranch(
              long gIndex, TreeNode node, Bytes32 leftVisitResult, Bytes32 rightVisitResult) {
            return Hash.sha256(Bytes.wrap(leftVisitResult, rightVisitResult));
          }
        });
  }

  private static <T> T binaryTraverse(long gIndex, TreeNode node, BinaryVisitor<T> visitor) {
    if (node instanceof LeafNode) {
      return visitor.visitLeaf(gIndex, (LeafNode) node);
    } else if (node instanceof BranchNode) {
      BranchNode branchNode = (BranchNode) node;
      return visitor.visitBranch(
          gIndex,
          branchNode,
          binaryTraverse(gIdxLeftGIndex(gIndex), branchNode.left(), visitor),
          binaryTraverse(gIdxRightGIndex(gIndex), branchNode.right(), visitor));
    } else {
      throw new IllegalArgumentException("Unexpected node type: " + node.getClass());
    }
  }

  private interface BinaryVisitor<T> {

    T visitLeaf(long gIndex, LeafNode node);

    T visitBranch(long gIndex, TreeNode node, T leftVisitResult, T rightVisitResult);
  }
}
