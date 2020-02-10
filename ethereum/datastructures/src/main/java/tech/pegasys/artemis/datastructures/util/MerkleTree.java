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

package tech.pegasys.artemis.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

abstract class MerkleTree {
  protected final List<List<Bytes32>> tree;
  protected final List<Bytes32> zeroHashes;
  protected final int treeDepth;

  protected MerkleTree(int treeDepth) {
    checkArgument(treeDepth > 1, "MerkleTree: treeDepth must be greater than 1");
    this.treeDepth = treeDepth;
    tree = new ArrayList<>();
    for (int i = 0; i <= treeDepth; i++) {
      tree.add(new ArrayList<>());
    }
    zeroHashes = generateZeroHashes(treeDepth);
  }

  abstract void add(Bytes32 leaf);

  protected abstract int getNumberOfLeaves();

  protected static List<Bytes32> generateZeroHashes(int height) {
    List<Bytes32> zeroHashes = new ArrayList<>();
    for (int i = 0; i < height; i++) {
      zeroHashes.add(Bytes32.ZERO);
    }
    for (int i = 0; i < height - 1; i++) {
      zeroHashes.set(i + 1, Hash.sha2_256(Bytes.concatenate(zeroHashes.get(i), zeroHashes.get(i))));
    }
    return zeroHashes;
  }

  public SSZVector<Bytes32> getProofTreeByValue(Bytes32 value) {
    int index = tree.get(0).indexOf(value);
    return getProofTreeByIndex(index);
  }

  public SSZVector<Bytes32> getProofTreeByIndex(int index) {
    List<Bytes32> proof = new ArrayList<>();
    for (int i = 0; i < treeDepth; i++) {
      index = index % 2 == 1 ? index - 1 : index + 1;
      if (index < tree.get(i).size()) proof.add(tree.get(i).get(index));
      else proof.add(zeroHashes.get(i));
      index /= 2;
    }
    proof.add(calcMixInValue());
    return new SSZVector<>(proof, Bytes32.class);
  }

  private Bytes32 calcMixInValue() {
    return (Bytes32)
        Bytes.concatenate(
            Bytes.ofUnsignedLong(getNumberOfLeaves(), LITTLE_ENDIAN), Bytes.wrap(new byte[24]));
  }

  public Bytes32 getRoot() {
    return tree.get(treeDepth).get(0);
  }
}
