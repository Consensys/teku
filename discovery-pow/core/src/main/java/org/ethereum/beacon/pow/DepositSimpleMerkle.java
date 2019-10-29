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
import java.util.function.Function;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * Simplest implementation that keeps all input values in memory and recalculates tree on any query.
 *
 * <p>Minimal merkle tree <a
 * href="https://en.wikipedia.org/wiki/Merkle_tree">https://en.wikipedia.org/wiki/Merkle_tree</a>
 * implementation, adoption from python code from <a
 * href="https://github.com/ethereum/research/blob/master/spec_pythonizer/utils/merkle_minimal.py">https://github.com/ethereum/research/blob/master/spec_pythonizer/utils/merkle_minimal.py</a></a>
 */
public class DepositSimpleMerkle extends DepositDataMerkle {

  private final int treeDepth;
  private List<BytesValue> deposits = new ArrayList<>();

  public DepositSimpleMerkle(Function<BytesValue, Hash32> hashFunction, int treeDepth) {
    super(hashFunction, treeDepth);
    this.treeDepth = treeDepth;
  }

  // # Compute a Merkle root of a right-zerobyte-padded 2**32 sized tree
  // def calc_merkle_tree_from_leaves(values):
  //    values = list(values)
  //    tree = [values[::]]
  //    for h in range(32):
  //        if len(values) % 2 == 1:
  //            values.append(zerohashes[h])
  //        values = [hash(values[i] + values[i+1]) for i in range(0, len(values), 2)]
  //        tree.append(values[::])
  //    return tree
  private List<List<BytesValue>> calc_merkle_tree_from_leaves(List<BytesValue> valueList) {
    List<BytesValue> values = new ArrayList<>(valueList);
    List<List<BytesValue>> tree = new ArrayList<>();
    tree.add(values);
    for (int h = 0; h < treeDepth; ++h) {
      if (values.size() % 2 == 1) {
        values.add(getZeroHash(h));
      }
      List<BytesValue> valuesTemp = new ArrayList<>();
      for (int i = 0; i < values.size(); i += 2) {
        valuesTemp.add(getHashFunction().apply(values.get(i).concat(values.get(i + 1))));
      }
      values = valuesTemp;
      tree.add(values);
    }

    return tree;
  }

  // def get_merkle_root(values):
  //    return calc_merkle_tree_from_leaves(values)[-1][0]
  private BytesValue get_merkle_root(List<BytesValue> values) {
    List<List<BytesValue>> tree = calc_merkle_tree_from_leaves(values);
    return tree.get(tree.size() - 1).get(0);
  }

  // def get_merkle_proof(tree, item_index):
  //    proof = []
  //    for i in range(32):
  //        subindex = (item_index//2**i)^1
  //        proof.append(tree[i][subindex] if subindex < len(tree[i]) else zerohashes[i])
  //    return proof
  private List<Hash32> get_merkle_proof(List<List<BytesValue>> tree, int item_index) {
    List<Hash32> proof = new ArrayList<>();
    for (int i = 0; i < treeDepth; ++i) {
      int subIndex = (item_index / (1 << i)) ^ 1;
      if (subIndex < tree.get(i).size()) {
        proof.add(Hash32.wrap(Bytes32.leftPad(tree.get(i).get(subIndex))));
      } else {
        proof.add(getZeroHash(i));
      }
    }

    return proof;
  }

  @Override
  public List<Hash32> getProof(int index, int size) {
    verifyIndexNotTooBig(index);
    if (size > (getLastIndex() + 1)) {
      throw new RuntimeException(
          String.format("Max size is %s, asked for size %s!", getLastIndex() + 1, size));
    }
    List<Hash32> proof =
        get_merkle_proof(calc_merkle_tree_from_leaves(deposits.subList(0, size)), index);

    // add encoded length
    proof.add(Hash32.wrap(encodeLength(size)));

    return proof;
  }

  @Override
  public Hash32 getRoot(int index) {
    verifyIndexNotTooBig(index);
    return mixinLength(get_merkle_root(deposits.subList(0, index + 1)), index + 1);
  }

  @Override
  public void addValue(DepositData depositData) {
    insertDepositData(createDepositDataValue(depositData, getHashFunction()).extractArray());
  }

  private void insertDepositData(byte[] depositData) {
    this.deposits.add(BytesValue.wrap(depositData));
  }

  @Override
  public int getLastIndex() {
    return deposits.size() - 1;
  }
}
