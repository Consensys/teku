/*
 * Copyright 2021 ConsenSys AG.
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

import java.util.Arrays;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

class SimpleLeafNode implements LeafNode, TreeNode {

  private final byte[] data;

  public SimpleLeafNode(Bytes data) {
    checkArgument(data.size() <= MAX_BYTE_SIZE);
    this.data = data.toArrayUnsafe();
  }

  @Override
  public Bytes getData() {
    return Bytes.wrap(data);
  }

  @Override
  public Bytes32 hashTreeRoot() {
    if (data.length == MAX_BYTE_SIZE) {
      return Bytes32.wrap(data);
    } else {
      return Bytes32.wrap(Arrays.copyOf(data, MAX_BYTE_SIZE));
    }
  }

  @Override
  public TreeNode updated(TreeUpdates newNodes) {
    if (newNodes.isEmpty()) {
      return this;
    } else {
      newNodes.checkLeaf();
      return newNodes.getNode(0);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LeafNode)) {
      return false;
    }
    LeafNode otherLeaf = (LeafNode) o;
    return Objects.equals(getData(), otherLeaf.getData());
  }

  @Override
  public int hashCode() {
    return getData().hashCode();
  }

  @Override
  public String toString() {
    return "[" + getData() + "]";
  }
}
