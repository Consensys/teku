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

package tech.pegasys.teku.ssz.backing.type;

import java.util.List;

public class TypeHints {
  public final boolean superLeafNode;
  public final List<Integer> superBranchDepths;

  public static TypeHints none() {
    return new TypeHints(false, null);
  }

  public static TypeHints superLeaf() {
    return new TypeHints(true, null);
  }

  public static TypeHints superBranch(List<Integer> superBranchDepths) {
    return new TypeHints(false, superBranchDepths);
  }

  private TypeHints(boolean superLeafNode, List<Integer> superBranchDepths) {
    this.superLeafNode = superLeafNode;
    this.superBranchDepths = superBranchDepths;
  }

  public boolean isSuperLeafNode() {
    return superLeafNode;
  }

  public List<Integer> getSuperBranchDepths() {
    return superBranchDepths;
  }

  public boolean isSuperBranchNodes() {
    return superBranchDepths != null;
  }
}
