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

public interface SSZType {
  int SSZ_LENGTH_SIZE = 4;

  static Bytes lengthToBytes(int length) {
    return Bytes.ofUnsignedInt(length, ByteOrder.LITTLE_ENDIAN);
  }

  static int bytesToLength(Bytes bytes) {
    if (!bytes.slice(SSZ_LENGTH_SIZE).isZero()) {
      throw new IllegalArgumentException("Invalid length bytes: " + bytes);
    }
    return bytes.slice(0, SSZ_LENGTH_SIZE).toInt(ByteOrder.LITTLE_ENDIAN);
  }

  boolean isFixedSize();

  int getFixedPartSize();

  int getVariablePartSize(TreeNode node);

  default int getSszSize(TreeNode node) {
    return getFixedPartSize() + getVariablePartSize(node);
  }

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

  int sszSerialize(TreeNode node, Consumer<Bytes> writer);

  TreeNode sszDeserialize(Bytes ssz);
}
