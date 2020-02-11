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

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

public class SimpleMerkleTree extends MerkleTree {
  private boolean dirty = true;

  public SimpleMerkleTree(int treeDepth) {
    super(treeDepth);
  }

  @Override
  public void add(Bytes32 leaf) {
    dirty = true;
    tree.get(0).add(leaf);
  }

  private void calcBranches() {
    for (int i = 0; i < treeDepth; i++) {
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

  @Override
  public int getNumberOfLeaves() {
    return tree.get(0).size();
  }

  @Override
  public SSZVector<Bytes32> getProofTreeByIndex(int index) {
    if (dirty) calcBranches();
    return super.getProofTreeByIndex(index);
  }

  @Override
  public SSZVector<Bytes32> getProofWithViewBoundary(Bytes32 leaf, int viewLimit) {
    if (dirty) calcBranches();
    return super.getProofWithViewBoundary(leaf, viewLimit);
  }

  @Override
  public SSZVector<Bytes32> getProofWithViewBoundary(int index, int viewLimit) {
    if (dirty) calcBranches();
    return super.getProofWithViewBoundary(index, viewLimit);
  }

  @Override
  public Bytes32 getRoot() {
    if (dirty) calcBranches();
    return super.getRoot();
  }
}
