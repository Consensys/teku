/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.pow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import org.ethereum.beacon.util.ConsumerList;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * Sames as {@link DepositSimpleMerkle} but it's not keeping all elements, so any sliced tree could
 * be created. Instead, it keeps last N elements and all other are put in the tree, so only slices
 * of trees with last N elements could be created.
 */
public class DepositBufferedMerkle extends DepositDataMerkle {

  private final int treeDepth;
  private final List<Hash32> lastElements;
  private int treeDepositCount = 0;
  private List<List<BytesValue>> committedTree;

  /**
   * Buffered Merkle tree using
   *
   * @param hashFunction hash function
   * @param treeDepth tree with depth of
   * @param bufferDeposits number of not committed deposits, we could easily roll to any state
   *     backwards if it doesn't involve skipping more than this number of deposits
   */
  public DepositBufferedMerkle(
      Function<BytesValue, Hash32> hashFunction, int treeDepth, int bufferDeposits) {
    super(hashFunction, treeDepth);
    this.treeDepth = treeDepth;
    Consumer<Hash32> consumer =
        depositData -> {
          treeDepositCount++;
          addValue(committedTree, depositData);
        };
    this.lastElements = ConsumerList.create(bufferDeposits, consumer);
    this.committedTree = new ArrayList<>();
    IntStream.range(0, treeDepth + 1).forEach(x -> committedTree.add(new ArrayList<>()));
  }

  private void addValue(List<List<BytesValue>> tree, BytesValue value) {
    if (!tree.get(0).isEmpty() && tree.get(0).get(tree.get(0).size() - 1) == getZeroHash(0)) {
      tree.get(0).remove(tree.get(0).size() - 1);
    }
    int stageSize = tree.get(0).size();
    tree.get(0).add(value);
    for (int h = 0; h < treeDepth + 1; ++h) {
      List<BytesValue> stage = tree.get(h);
      if (h > 0) {
        // Remove elements that should be modified
        stageSize = stageSize / 2;
        while (stage.size() != stageSize) {
          stage.remove(stage.size() - 1);
        }

        List<BytesValue> previousStage = tree.get(h - 1);
        int previousStageSize = previousStage.size();
        stage.add(
            getHashFunction()
                .apply(
                    previousStage
                        .get(previousStageSize - 2)
                        .concat(previousStage.get(previousStageSize - 1))));
      }
      if (stage.size() % 2 == 1 && h != treeDepth) {
        stage.add(getZeroHash(h));
      }
    }
  }

  @Override
  public List<Hash32> getProof(int index, int size) {
    List<List<BytesValue>> tree = buildTreeForIndex(size - 1);
    List<Hash32> proof = new ArrayList<>();
    for (int i = 0; i < treeDepth; ++i) {
      int subIndex = (index / (1 << i)) ^ 1;
      if (subIndex < tree.get(i).size()) {
        proof.add(Hash32.wrap(Bytes32.leftPad(tree.get(i).get(subIndex))));
      } else {
        proof.add(getZeroHash(i));
      }
    }

    proof.add(Hash32.wrap(encodeLength(size)));

    return proof;
  }

  private BytesValue get_merkle_root(List<List<BytesValue>> tree) {
    return tree.get(tree.size() - 1).get(0);
  }

  private List<List<BytesValue>> buildTreeForIndex(int index) {
    verifyIndexNotTooBig(index);
    verifyIndexNotTooOld(index);

    List<List<BytesValue>> treeCopy = new ArrayList<>();
    committedTree.forEach(s -> treeCopy.add(new ArrayList<>(s)));
    // index of 1st == 0
    for (int i = treeDepositCount; i < index + 1; ++i) {
      addValue(treeCopy, lastElements.get(i - treeDepositCount));
    }

    return treeCopy;
  }

  @Override
  public Hash32 getRoot(int index) {
    List<List<BytesValue>> tree = buildTreeForIndex(index);
    BytesValue root = get_merkle_root(tree);
    return mixinLength(root, index + 1);
  }

  private void verifyIndexNotTooOld(int index) {
    if (index < treeDepositCount) {
      throw new RuntimeException(
          String.format(
              "Too old element index queried, %s, minimum: %s!", index, treeDepositCount));
    }
  }

  @Override
  public void addValue(DepositData value) {
    lastElements.add(createDepositDataValue(value, getHashFunction()));
  }

  @Override
  public int getLastIndex() {
    return treeDepositCount + lastElements.size() - 1;
  }
}
