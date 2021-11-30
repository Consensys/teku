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

package tech.pegasys.teku.spec.datastructures.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;

public class OptimizedMerkleTree extends MerkleTree {

  public OptimizedMerkleTree(int treeDepth) {
    super(treeDepth);
  }

  @Override
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
                Bytes.concatenate(
                    previousStage.get(previousStageSize - 2),
                    previousStage.get(previousStageSize - 1))));
      }
      if (stage.size() % 2 == 1 && h != treeDepth) {
        stage.add(zeroHashes.get(h));
      }
    }
  }

  @Override
  public int getNumberOfLeaves() {
    int lastLeafIndex = tree.get(0).size() - 1;
    if (tree.get(0).get(lastLeafIndex).equals(Bytes32.ZERO)) {
      return tree.get(0).size() - 1;
    }
    return tree.get(0).size();
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
