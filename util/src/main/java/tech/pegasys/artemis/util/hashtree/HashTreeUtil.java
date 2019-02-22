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

package tech.pegasys.artemis.util.hashtree;

import java.util.List;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;

/** This class is a collection of tree hash root convenience methods */
public final class HashTreeUtil {

  /**
   * Calculate the hash tree root of the provided value
   *
   * @param value
   */
  public static Bytes32 hash_tree_root(Bytes value) {
    return SSZ.hashTreeRoot(value);
  }

  /**
   * Calculate the hash tree root of the list of validators provided
   *
   * @param validators
   */
  public static Bytes32 hash_tree_root(List<Bytes> list) {
    return hash_tree_root(
        SSZ.encode(
            writer -> {
              writer.writeBytesList(list);
            }));
  }

  /**
   * Calculate the hash tree root of the list of integers provided.
   *
   * <p><b>WARNING: This assume 64-bit encoding is intended for the integers provided.</b>
   *
   * @param integers
   * @return
   */
  public static Bytes32 integerListHashTreeRoot(List<Integer> integers) {
    return hash_tree_root(
        SSZ.encode(
            // TODO This can be replaced with writeUInt64List(List) once implemented in Cava.
            writer -> {
              writer.writeUIntList(64, integers);
            }));
  }
}
