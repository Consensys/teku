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

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.Hash;
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

  /**
   * Calculate the merkle root of the list of Bytes.
   *
   * @param list - The list of Bytes32 objects to calculate the merkle root for.
   * @return The merkle root.
   */
  /**
   * Hashes a list of homogeneous values.
   *
   * @param values a list of homogeneous values
   * @return the merkle hash of the list of values
   */
  public static Bytes merkleHash(List<Bytes> values) {
    Bytes littleEndianLength = Bytes.ofUnsignedInt(values.size(), LITTLE_ENDIAN);
    Bytes32 valuesLength = Bytes32.rightPad(littleEndianLength);

    List<Bytes> chunks;
    if (values.isEmpty()) {
      chunks = new ArrayList<>();
      chunks.add(Bytes.wrap(new byte[128]));
    } else if (values.get(0).size() < 128) {
      int itemsPerChunk = (int) Math.floor(128 / (double) values.get(0).size());
      chunks = new ArrayList<>();

      for (int i = 0; i * itemsPerChunk < values.size(); i++) {
        Bytes[] chunkItems =
            values
                .subList(i * itemsPerChunk, Math.min((i + 1) * itemsPerChunk, values.size()))
                .toArray(new Bytes[0]);
        chunks.add(Bytes.concatenate(chunkItems));
      }
    } else {
      chunks = values;
    }
    while (chunks.size() > 1) {
      if (chunks.size() % 2 == 1) {
        chunks.add(Bytes.wrap(new byte[128]));
      }
      Iterator<Bytes> iterator = chunks.iterator();
      List<Bytes> hashRound = new ArrayList<>();
      while (iterator.hasNext()) {
        hashRound.add(Hash.keccak256(Bytes.concatenate(iterator.next(), iterator.next())));
      }
      chunks = hashRound;
    }

    return Hash.keccak256(Bytes.concatenate(chunks.get(0), valuesLength));
  }
}
