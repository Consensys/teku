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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.Hash;
import net.consensys.cava.ssz.SSZ;

/** This class is a collection of tree hash root convenience methods */
public final class HashTreeUtil {

  /**
   * Create the hash tree root of a set of values of basic SSZ types or tuples of basic types. Basic
   * SSZ types are uintN, bool, and byte. bytesN (i.e. Bytes32) is a tuple of basic types. NOTE:
   * Bytes (and not Bytes32, Bytes48 etc.) IS NOT a basic type or a tuple of basic types.
   *
   * @param bytes One Bytes value or a list of homogeneous Bytes values.
   * @return The SSZ tree root hash of the values.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.5.1/specs/simple-serialize.md">SSZ
   *     Spec v0.5.1</a>
   */
  public static Bytes32 hash_tree_root_basic_type(Bytes... bytes) {
    if (bytes.length == 1) {
      if (bytes[0].size() > 32) {
        return Hash.keccak256(bytes[0]);
      } else {
        return Bytes32.rightPad(bytes[0]);
      }
    } else {
      Bytes hash = merkleHash(new ArrayList<>(Arrays.asList(bytes)));
      return Bytes32.rightPad(hash);
    }
  }

  /**
   * Create the hash tree root of a list of values of basic SSZ types. This is only to be used for
   * SSZ lists and not SSZ tuples. See the "see also" for more info.
   *
   * @param bytes A list of homogeneous Bytes values representing basic SSZ types.
   * @return The SSZ tree root hash of the list of values.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.5.1/specs/simple-serialize.md">SSZ
   *     Spec v0.5.1</a>
   */
  public static Bytes32 hash_tree_root_list_of_basic_type(List<? extends Bytes> bytes, int length) {
    return mix_in_length(hash_tree_root_basic_type(bytes.toArray(new Bytes[0])), length);
  }

  public static Bytes32 mix_in_length(Bytes32 merkle_root, int length) {
    return Hash.keccak256(Bytes.concatenate(merkle_root, Bytes.ofUnsignedInt(length, LITTLE_ENDIAN)));
  }

  /**
   * Hashes a list of homogeneous values.
   *
   * @param values a list of homogeneous values
   * @return the merkle hash of the list of values
   */
  public static Bytes32 merkleHash(List<Bytes> values) {
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
