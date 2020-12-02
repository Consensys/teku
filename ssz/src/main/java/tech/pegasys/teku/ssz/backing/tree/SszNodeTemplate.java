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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.ssz.backing.type.ViewType;

public class SszNodeTemplate {

  public static final class Location {
    public final int offset;
    public final int length;

    public Location(int offset, int length) {
      this.offset = offset;
      this.length = length;
    }
  }

  private static Location calcOffsets(
      int offset, TreeNode node, long thisGIdx, Map<Long, Location> locations) {
    if (node instanceof LeafNode) {
      Location location = new Location(offset, ((LeafNode) node).getData().size());
      locations.put(thisGIdx, location);
      return location;
    } else {
      BranchNode branchNode = (BranchNode) node;
      Location leftLoc =
          calcOffsets(offset, branchNode.left(), GIndexUtil.gIdxLeftGIndex(thisGIdx), locations);
      Location rightLoc =
          calcOffsets(
              offset + leftLoc.length,
              branchNode.right(),
              GIndexUtil.gIdxRightGIndex(thisGIdx),
              locations);
      Location thisLoc = new Location(offset, leftLoc.length + rightLoc.length);
      locations.put(thisGIdx, thisLoc);
      return thisLoc;
    }
  }

  public static SszNodeTemplate createFromType(ViewType containerType) {
    checkArgument(containerType.isFixedSize(), "Only fixed size containers supported");

    // This should be CANONICAL binary tree
    TreeNode defaultTree = containerType.getDefaultTree();

    Map<Long, Location> gIndexToLoc = new HashMap<>();
    Location rootLoc = calcOffsets(0, defaultTree, GIndexUtil.SELF_G_INDEX, gIndexToLoc);
    Map<TreeNode, Location> nodeToLoc = new IdentityHashMap<>();
    AtomicInteger off = new AtomicInteger();
    defaultTree.iterateAll(
        (node, idx) -> {
          if (node instanceof LeafNode) {
            int leafSszSize = ((LeafNode) node).getData().size();
            Location nodeSszLocation = new Location(off.get(), leafSszSize);
            nodeToLoc.put(node, nodeSszLocation);
            off.addAndGet(leafSszSize);
          }
          return true;
        });
    assert rootLoc.length == off.get();
    return new SszNodeTemplate(gIndexToLoc, nodeToLoc, off.get(), defaultTree);
  }

  private final Map<Long, Location> gIndexToLoc;
  private final Map<TreeNode, Location> nodeToLoc;
  private final int sszLength;
  private final TreeNode defaultTree;

  public SszNodeTemplate(
      Map<Long, Location> gIndexToLoc,
      Map<TreeNode, Location> nodeToLoc,
      int sszLength,
      TreeNode defaultTree) {
    this.gIndexToLoc = gIndexToLoc;
    this.nodeToLoc = nodeToLoc;
    this.sszLength = sszLength;
    this.defaultTree = defaultTree;
  }

  public Location getNodeSszLocation(long generalizedIndex) {
    return gIndexToLoc.get(generalizedIndex);
  }

  public void update(long generalizedIndex, TreeNode newNode, MutableBytes dest) {
    // sub-optimal update implementation
    // implement other method to optimize
    Location leafPos = getNodeSszLocation(generalizedIndex);
    List<Bytes> nodeSsz = nodeSsz(newNode);
    int off = 0;
    for (int i = 0; i < nodeSsz.size(); i++) {
      Bytes newSszChunk = nodeSsz.get(i);
      newSszChunk.copyTo(dest, leafPos.offset + off);
      off += newSszChunk.size();
    }
    checkArgument(off == leafPos.length);
  }

  private static List<Bytes> nodeSsz(TreeNode node) {
    List<Bytes> sszBytes = new ArrayList<>();
    TreeUtil.iterateLeavesData(node, LEFTMOST_G_INDEX, RIGHTMOST_G_INDEX, sszBytes::add);
    return sszBytes;
  }

  public int getSszLength() {
    return sszLength;
  }

  public Bytes32 calculateHashTreeRoot(Bytes ssz, int offset) {
    return calcHash(ssz, offset, defaultTree);
  }

  private Bytes32 calcHash(Bytes ssz, int offset, TreeNode node) {
    if (node instanceof LeafNode) {
      Location location = nodeToLoc.get(node);
      return Bytes32.rightPad(ssz.slice(offset + location.offset, location.length));
    } else {
      BranchNode branchNode = (BranchNode) node;
      return Hash.sha2_256(
          Bytes.wrap(
              calcHash(ssz, offset, branchNode.left()), calcHash(ssz, offset, branchNode.right())));
    }
  }
}
