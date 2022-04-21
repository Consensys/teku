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

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

class SimpleLeafNode implements LeafNode, TreeNode {
  private final Bytes data;
  private volatile Bytes32 cachedHash;

  public SimpleLeafNode(Bytes data) {
    checkArgument(data.size() <= MAX_BYTE_SIZE);
    if (data.size() == MAX_BYTE_SIZE) {
      // if data is Bytes32, it will pass throw with no object creation
      // otherwise translate types to Bytes32
      cachedHash = Bytes32.wrap(data.copy());
    } else if (data.size() == 0) {
      cachedHash = Bytes32.ZERO;
    }
    this.data = data;
  }

  @Override
  public Bytes getData() {
    return data;
  }

  @Override
  public Bytes getLeafData() {
    return data;
  }

  @Override
  public Bytes32 hashTreeRoot() {
    Bytes32 cachedHash = this.cachedHash;
    if (cachedHash != null) {
      return cachedHash;
    }

    return Bytes32.wrap(Arrays.copyOf(data.toArrayUnsafe(), MAX_BYTE_SIZE));
  }

  @Override
  public byte[] hashTreeRoot(MessageDigest messageDigest) {
    // this is not supposed to be used.
    // we expect hashTreeRoot() or getLeafData() to be called
    return Arrays.copyOf(data.toArrayUnsafe(), MAX_BYTE_SIZE);
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
