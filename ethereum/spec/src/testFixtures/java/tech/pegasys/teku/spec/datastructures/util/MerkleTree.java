/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;

public class MerkleTree {

  private final List<List<Bytes32>> tree;
  private final List<Bytes32> zeroHashes;
  protected final int treeDepth; // Root does not count as depth, i.e. tree height is treeDepth + 1

  public MerkleTree(int treeDepth) {
    checkArgument(treeDepth > 1, "MerkleTree: treeDepth must be greater than 1");
    this.treeDepth = treeDepth;
    this.tree = new ArrayList<>();
    for (int i = 0; i <= treeDepth; i++) {
      this.tree.add(new ArrayList<>());
    }
    this.zeroHashes = generateZeroHashes(treeDepth);
  }

  protected static List<Bytes32> generateZeroHashes(int height) {
    List<Bytes32> zeroHashes = new ArrayList<>();
    zeroHashes.add(Bytes32.ZERO);
    for (int i = 1; i < height; i++) {
      zeroHashes.add(i, Hash.sha256(zeroHashes.get(i - 1), zeroHashes.get(i - 1)));
    }
    return zeroHashes;
  }

  public void add(Bytes32 leaf) {
    if (!tree.get(0).isEmpty()
        && tree.get(0).get(tree.get(0).size() - 1).equals(zeroHashes.get(0))) {
      tree.get(0).remove(tree.get(0).size() - 1);
    }
    int stageSize = tree.get(0).size();
    tree.get(0).add(leaf);
    for (int h = 0; h <= treeDepth; h++) {
      List<Bytes32> stage = tree.get(h);
      if (h > 0) {
        // Remove elements that should be modified
        stageSize = stageSize / 2;
        while (stage.size() != stageSize) {
          stage.remove(stage.size() - 1);
        }

        List<Bytes32> previousStage = tree.get(h - 1);
        int previousStageSize = previousStage.size();
        stage.add(
            Hash.sha256(
                previousStage.get(previousStageSize - 2),
                previousStage.get(previousStageSize - 1)));
      }
      if (stage.size() % 2 == 1 && h != treeDepth) {
        stage.add(zeroHashes.get(h));
      }
    }
  }

  public int getNumberOfLeaves() {
    int lastLeafIndex = tree.get(0).size() - 1;
    if (tree.get(0).get(lastLeafIndex).equals(Bytes32.ZERO)) {
      return tree.get(0).size() - 1;
    }
    return tree.get(0).size();
  }

  public List<Bytes32> getProof(Bytes32 value) {
    int index = tree.get(0).indexOf(value);
    if (index == -1) {
      throw new IllegalArgumentException("Leaf value is missing from the MerkleTree");
    }
    return getProof(index);
  }

  public List<Bytes32> getProof(int itemIndex) {
    List<Bytes32> proof = new ArrayList<>();
    for (int i = 0; i < treeDepth; i++) {

      // Get index of sibling node
      int siblingIndex = itemIndex % 2 == 1 ? itemIndex - 1 : itemIndex + 1;

      // If sibling is contained in the tree
      if (siblingIndex < tree.get(i).size()) {

        // Get the sibling from the tree
        proof.add(tree.get(i).get(siblingIndex));
      } else {

        // Get the zero hash at the appropriate
        // depth of the tree as sibling
        proof.add(zeroHashes.get(i));
      }

      itemIndex /= 2;
    }
    proof.add(calcMixInValue());
    return proof;
  }

  private Bytes32 calcViewBoundaryRoot(int depth, int viewLimit) {
    if (depth == 0) {
      return zeroHashes.get(0);
    }
    depth -= 1;
    Bytes32 deeperRoot = calcViewBoundaryRoot(depth, viewLimit);
    // Check if given the viewLimit at the leaf layer, is root in left or right subtree
    if ((viewLimit & (1 << depth)) != 0) {
      // For the right subtree
      return Hash.sha256(tree.get(depth).get((viewLimit >> depth) - 1), deeperRoot);
    } else {
      // For the left subtree
      return Hash.sha256(deeperRoot, zeroHashes.get(depth));
    }
  }

  /**
   * @param value of the leaf
   * @param viewLimit number of leaves in the tree
   * @return proof (i.e. collection of siblings on the way to root for the given leaf)
   */
  public List<Bytes32> getProofWithViewBoundary(Bytes32 value, int viewLimit) {
    return getProofWithViewBoundary(tree.get(0).indexOf(value), viewLimit);
  }

  /**
   * @param itemIndex of the leaf
   * @param viewLimit number of leaves in the tree
   * @return proof (i.e. collection of siblings on the way to root for the given leaf)
   */
  public List<Bytes32> getProofWithViewBoundary(int itemIndex, int viewLimit) {
    checkArgument(itemIndex < viewLimit, "MerkleTree: Index must be less than the view limit");

    List<Bytes32> proof = new ArrayList<>();
    for (int i = 0; i < treeDepth; i++) {
      // Get index of sibling node
      int siblingIndex = itemIndex % 2 == 1 ? itemIndex - 1 : itemIndex + 1;

      // Check how much of the tree at this level is strictly within the view limit.
      int limit = viewLimit >> i;

      checkArgument(
          limit <= tree.get(i).size(), "MerkleTree: Tree is too small for given limit at height");

      // If the sibling is equal to the limit,
      if (siblingIndex == limit) {
        // Go deeper to partially merkleize in zero-hashes.
        proof.add(calcViewBoundaryRoot(i, viewLimit));
      } else if (siblingIndex > limit) {
        // Beyond:
        // Just use a zero-hash as effective sibling.
        proof.add(zeroHashes.get(i));
      } else {
        // Within:
        // Return the tree node as-is without modifications
        proof.add(tree.get(i).get(siblingIndex));
      }
      itemIndex >>>= 1;
    }
    proof.add(calcMixInValue(viewLimit));
    return proof;
  }

  public Bytes32 calcMixInValue(int viewLimit) {
    return (Bytes32)
        Bytes.concatenate(Bytes.ofUnsignedLong(viewLimit, LITTLE_ENDIAN), Bytes.wrap(new byte[24]));
  }

  public Bytes32 calcMixInValue() {
    return calcMixInValue(getNumberOfLeaves());
  }

  public Bytes32 getRoot() {
    return Hash.sha256(tree.get(treeDepth).get(0), calcMixInValue());
  }

  @Override
  public String toString() {
    StringBuilder returnString = new StringBuilder();
    int numLeaves = (int) Math.pow(2, treeDepth);
    int height = 0;
    int stageFullSize;
    for (int i = treeDepth; i >= 0; i--) {
      stageFullSize = (int) Math.pow(2, height);
      height++;
      int stageNonZeroSize = tree.get(i).size();
      List<Bytes32> stageItems = new ArrayList<>(tree.get(i));
      for (int j = stageNonZeroSize; j < stageFullSize; j++) {
        stageItems.add(zeroHashes.get(i));
      }
      returnString.append("\n").append(centerPrint(stageItems, numLeaves)).append(stageFullSize);
    }
    return "MerkleTree{" + "tree=" + returnString + ", treeDepth=" + treeDepth + '}';
  }

  private String centerPrint(List<Bytes32> stageItems, int numLeaves) {
    String emptySpaceOnSide =
        IntStream.range(0, (numLeaves - stageItems.size()))
            .mapToObj(i -> "    ")
            .collect(Collectors.joining("    "));
    if (numLeaves == stageItems.size()) {
      emptySpaceOnSide = "                ";
    }
    String stageString =
        stageItems.stream()
            .map(item -> item.toHexString().substring(63))
            .collect(Collectors.joining(emptySpaceOnSide));

    return emptySpaceOnSide.concat(stageString).concat(emptySpaceOnSide);
  }
}
