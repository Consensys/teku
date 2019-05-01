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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.Hash;

/** This class is a collection of tree hash root convenience methods */
public final class HashTreeUtil {

  public static enum SSZTypes {BASIC, CONTAINER, TUPLE_BASIC, TUPLE_CONTAINER, LIST_BASIC, LIST_CONTAINER};
  public static final int BYTES_PER_CHUNK = 32;

  private static Bytes32 hash_tree_root(SSZTypes sszType, Bytes... bytes) {
    // NOT YET IMPLEMENTED
    // TODO - My plan is to wrap all the hash_tree_root calculation in this class so our types aren't as complex.
    return Bytes32.ZERO;
  }

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
    return merkleize(pack(bytes));
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

  public static Bytes32 merkleize(List<Bytes32> sszChunks) {
    // A balanced binary tree must have a power of two number of leaves.
    // NOTE: is_power_of_two also serves as a zero size check here.
    while(!is_power_of_two(sszChunks.size())) {
      sszChunks.add(Bytes32.ZERO);
    }

    // Expand the size of the sszChunks list large enough to hold the entire tree.
    sszChunks.addAll(0, Collections.nCopies(sszChunks.size(), Bytes32.ZERO));

    // Iteratively calculate the root for each parent in the binary tree, starting at the leaves.
    for(int inlineTreeIndex = sszChunks.size() / 2 - 1; inlineTreeIndex > 0; inlineTreeIndex--) {
      sszChunks.set(inlineTreeIndex, Hash.keccak256(Bytes.concatenate(sszChunks.get(inlineTreeIndex * 2), sszChunks.get(inlineTreeIndex * 2 + 1))));
    }

    // Return the root element, which is at index 1. The math is easier this way.
    return sszChunks.get(1);
  }

  public static List<Bytes32> pack(Bytes... sszValues) {
    // Join all varags sszValues into one Bytes type
    Bytes concatenatedBytes = Bytes.concatenate(sszValues);

    // Pad so that concatenatedBytes length is divisible by BYTES_PER_CHUNK
    int packingRemainder = concatenatedBytes.size() % BYTES_PER_CHUNK;
    if(packingRemainder != 0) {
      concatenatedBytes = Bytes.concatenate(concatenatedBytes, Bytes.wrap(new byte[packingRemainder]));
    }
    
    // Wrap each BYTES_PER_CHUNK-byte value into a Bytes32
    List<Bytes32> chunkifiedBytes = new ArrayList<>();
    for(int chunk = 0; chunk < concatenatedBytes.size(); chunk += BYTES_PER_CHUNK) {
      chunkifiedBytes.add(Bytes32.wrap(concatenatedBytes, chunk));
    }

    return chunkifiedBytes;
  }

  public static Bytes32 mix_in_length(Bytes32 merkle_root, int length) {
    return Hash.keccak256(Bytes.concatenate(merkle_root, Bytes.ofUnsignedInt(length, LITTLE_ENDIAN)));
  }

  public static boolean is_power_of_two(int value) {
    return value > 0 && (value & (value - 1)) == 0;
  }
}
