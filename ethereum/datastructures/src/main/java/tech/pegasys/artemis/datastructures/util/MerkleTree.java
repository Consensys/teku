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

package tech.pegasys.artemis.datastructures.util;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

public class MerkleTree<T> {
  private final List<List<Bytes32>> tree;
  private final List<Bytes32> zeroHashes;
  private final int height;
  private boolean dirty = true;

  public MerkleTree(int height) {
    assert (height > 1);
    this.height = height;
    tree = new ArrayList<List<Bytes32>>();
    for (int i = 0; i <= height; i++) {
      tree.add(new ArrayList<Bytes32>());
    }
    zeroHashes = generateZeroHashes(height);
  }

  public void add(Bytes32 leaf) {
    dirty = true;
    tree.get(0).add(leaf);
  }

  private void calcBranches() {
    for (int i = 0; i < height; i++) {
      List<Bytes32> parent = tree.get(i + 1);
      List<Bytes32> child = tree.get(i);
      for (int j = 0; j < child.size(); j += 2) {
        Bytes32 leftNode = child.get(j);
        Bytes32 rightNode = (j + 1 < child.size()) ? child.get(j + 1) : zeroHashes.get(i);
        if (parent.size() <= j / 2)
          parent.add(Hash.sha2_256(Bytes.concatenate(leftNode, rightNode)));
        else parent.set(j / 2, Hash.sha2_256(Bytes.concatenate(leftNode, rightNode)));
      }
    }
    dirty = false;
  }

  public SSZVector<Bytes32> getProofTreeByValue(Bytes32 value) {
    int index = tree.get(0).indexOf(value);
    return getProofTreeByIndex(index);
  }

  public SSZVector<Bytes32> getProofTreeByIndex(int index) {
    if (dirty) calcBranches();
    List<Bytes32> proof = new ArrayList<Bytes32>();
    for (int i = 0; i < height; i++) {
      index = index % 2 == 1 ? index - 1 : index + 1;
      if (index < tree.get(i).size()) proof.add(tree.get(i).get(index));
      else proof.add(zeroHashes.get(i));
      index /= 2;
    }
    proof.add(calcMixInValue());
    return new SSZVector<Bytes32>(proof, Bytes32.class);
  }

  private Bytes32 calcMixInValue() {
    return (Bytes32)
        Bytes.concatenate(
            Bytes.ofUnsignedLong(tree.get(0).size(), LITTLE_ENDIAN), Bytes.wrap(new byte[24]));
  }

  public static List<Bytes32> generateZeroHashes(int height) {
    List<Bytes32> zeroHashes = new ArrayList<Bytes32>();
    for (int i = 0; i < height; i++) zeroHashes.add(Bytes32.ZERO);
    for (int i = 0; i < height - 1; i++)
      zeroHashes.set(i + 1, Hash.sha2_256(Bytes.concatenate(zeroHashes.get(i), zeroHashes.get(i))));
    return zeroHashes;
  }

  public Bytes32 getRoot() {
    if (dirty) calcBranches();
    return tree.get(height).get(0);
  }
}
