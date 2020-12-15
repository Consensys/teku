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

import java.nio.ByteOrder;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SSZDeserializeException;
import tech.pegasys.teku.ssz.sos.SszReader;

/** Collection of SSZ related methods for {@link ViewType} */
public interface SSZType {

  // the size of SSZ UIn32 lengths and offsets
  int SSZ_LENGTH_SIZE = 4;

  // serializes int length to SSZ 4 bytes
  static Bytes lengthToBytes(int length) {
    return Bytes.ofUnsignedInt(length, ByteOrder.LITTLE_ENDIAN);
  }

  // deserializes int length from SSZ 4 bytes
  static int bytesToLength(Bytes bytes) {
    if (!bytes.slice(SSZ_LENGTH_SIZE).isZero()) {
      throw new SSZDeserializeException("Invalid length bytes: " + bytes);
    }
    int ret = bytes.slice(0, SSZ_LENGTH_SIZE).toInt(ByteOrder.LITTLE_ENDIAN);
    if (ret < 0) {
      throw new SSZDeserializeException("Invalid length: " + ret);
    }
    return ret;
  }

  /** Indicates whether the type is fixed or variable size */
  boolean isFixedSize();

  /** Returns the size of the fixed SSZ part for this type */
  int getFixedPartSize();

  /** Returns the size of the variable SSZ part for this type and specified backing subtree */
  int getVariablePartSize(TreeNode node);

  /** Calculates the full SSZ size in bytes for this type and specified backing subtree */
  default int getSszSize(TreeNode node) {
    return getFixedPartSize() + getVariablePartSize(node);
  }

  /** SSZ serializes the backing tree instance of this type */
  default Bytes sszSerialize(TreeNode node) {
    byte[] buf = new byte[getSszSize(node)];
    sszSerialize(
        node,
        new Consumer<>() {
          int off = 0;

          @Override
          public void accept(Bytes bytes) {
            System.arraycopy(bytes.toArrayUnsafe(), 0, buf, off, bytes.size());
            off += bytes.size();
          }
        });
    return Bytes.wrap(buf);
  }

  /**
   * SSZ serializes the backing tree of this type and returns the data as bytes 'stream' via passed
   * {@code writer}
   */
  int sszSerialize(TreeNode node, Consumer<Bytes> writer);

  TreeNode sszDeserializeTree(SszReader reader);
}
