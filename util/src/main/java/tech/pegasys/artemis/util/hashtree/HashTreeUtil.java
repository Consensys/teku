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
import java.util.List;
import java.util.stream.Collectors;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.Hash;

/** This class is a collection of tree hash root convenience methods */
public final class HashTreeUtil {

  /**
   * A enum defining different SSZ types. See
   * https://github.com/ethereum/eth2.0-specs/blob/v0.5.1/specs/simple-serialize.md.
   *
   * <p>Basic Types: - BASIC: A uint# or a byte (uint8) Composite Types: - CONTAINER: A collection
   * of arbitrary other SSZ types. - TUPLE: A fixed-length collection of homogenous values. -
   * _OF_BASIC: A tuple containing only basic SSZ types. - _OF_COMPOSITE: A tuple containing
   * homogenous SSZ composite types. - LIST: A variable-length collection of homogenous values. -
   * _OF_BASIC: A list containing only basic SSZ types. - _OF_COMPOSITE: A list containing
   * homogenous SSZ composite types.
   */
  public static enum SSZTypes {
    BASIC,
    CONTAINER,
    TUPLE_OF_BASIC,
    TUPLE_OF_COMPOSITE,
    LIST_OF_BASIC,
    LIST_OF_COMPOSITE
  };

  // BYTES_PER_CHUNK is rather tightly coupled to the value 32 due to the assumption that it fits in
  // the Byte32 type. Use care if this ever has to change.
  public static final int BYTES_PER_CHUNK = 32;

  public static Bytes32 hash_tree_root(SSZTypes sszType, Bytes... bytes) {
    switch (sszType) {
      case BASIC:
        return hash_tree_root_basic_type(bytes);
      case TUPLE_OF_BASIC:
        return hash_tree_root_tuple_of_basic_type(bytes);
      case TUPLE_OF_COMPOSITE:
        // TODO
        break;
      case LIST_OF_BASIC:
        return hash_tree_root_list_of_basic_type(bytes.length, bytes);
      case LIST_OF_COMPOSITE:
        throw new UnsupportedOperationException(
            "Use HashTreeUtil.hash_tree_root(SSZTypes.LIST_COMPOSITE, List) for a variable length list of composite SSZ types.");
      case CONTAINER:
        throw new UnsupportedOperationException(
            "hash_tree_root of SSZ Containers (often implemented by POJOs) must be done by the container POJO itself, as its individual fields cannot be enumerated without reflection.");
      default:
        break;
    }
    return Bytes32.ZERO;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static Bytes32 hash_tree_root(SSZTypes sszType, List bytes) {
    switch (sszType) {
      case LIST_OF_BASIC:
        if (!bytes.isEmpty() && bytes.get(0) instanceof Bytes) {
          return hash_tree_root_list_of_basic_type((List<Bytes>) bytes, bytes.size());
        }
        break;
      case LIST_OF_COMPOSITE:
        if (!bytes.isEmpty() && bytes.get(0) instanceof Merkleizable) {
          return hash_tree_root_list_composite_type((List<Merkleizable>) bytes, bytes.size());
        }
        break;
      case BASIC:
        throw new UnsupportedOperationException(
            "Use HashTreeUtil.hash_tree_root(SSZType.BASIC, Bytes...) for a basic SSZ type.");
      case TUPLE_OF_BASIC:
        throw new UnsupportedOperationException(
            "Use HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_BASIC, Bytes...) for a fixed length tuple of basic SSZ types.");
      case TUPLE_OF_COMPOSITE:
        throw new UnsupportedOperationException(
            "Use HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_COMPOSITE, Bytes...) for a fixed length tuple of composite SSZ types.");
      case CONTAINER:
        throw new UnsupportedOperationException(
            "hash_tree_root of SSZ Containers (often implemented by POJOs) must be done by the container POJO itself, as its individual fields cannot be enumerated without reflection.");
      default:
    }
    return Bytes32.ZERO;
  }

  public static Bytes32 merkleize(List<Bytes32> sszChunks) {
    List<Bytes32> mutableSSZChunks = new ArrayList<>(sszChunks);

    // A balanced binary tree must have a power of two number of leaves.
    // NOTE: is_power_of_two also serves as a zero size check here.
    while (!is_power_of_two(mutableSSZChunks.size())) {
      mutableSSZChunks.add(Bytes32.ZERO);
    }

    // Expand the size of the mutableSSZChunks list large enough to hold the entire tree.
    mutableSSZChunks.addAll(0, Collections.nCopies(mutableSSZChunks.size(), Bytes32.ZERO));

    // Iteratively calculate the root for each parent in the binary tree, starting at the leaves.
    for (int inlineTreeIndex = mutableSSZChunks.size() / 2 - 1;
        inlineTreeIndex > 0;
        inlineTreeIndex--) {
      mutableSSZChunks.set(
          inlineTreeIndex,
          Hash.keccak256(
              Bytes.concatenate(
                  mutableSSZChunks.get(inlineTreeIndex * 2),
                  mutableSSZChunks.get(inlineTreeIndex * 2 + 1))));
    }

    // Return the root element, which is at index 1. The math is easier this way.
    return mutableSSZChunks.get(1);
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
  private static Bytes32 hash_tree_root_basic_type(Bytes... bytes) {
    return merkleize(pack(bytes));
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
  private static Bytes32 hash_tree_root_tuple_of_basic_type(Bytes... bytes) {
    return hash_tree_root_basic_type(bytes);
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
  private static Bytes32 hash_tree_root_list_of_basic_type(int length, Bytes... bytes) {
    return mix_in_length(hash_tree_root_basic_type(bytes), length);
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
  private static Bytes32 hash_tree_root_list_of_basic_type(
      List<? extends Bytes> bytes, int length) {
    return mix_in_length(hash_tree_root_basic_type(bytes.toArray(new Bytes[0])), length);
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
  private static Bytes32 hash_tree_root_list_composite_type(
      List<? extends Merkleizable> bytes, int length) {
    return mix_in_length(
        merkleize(bytes.stream().map(item -> item.hash_tree_root()).collect(Collectors.toList())),
        length);
  }

  // TODO Make Private
  public static List<Bytes32> pack(Bytes... sszValues) {
    // Join all varags sszValues into one Bytes type
    Bytes concatenatedBytes = Bytes.concatenate(sszValues);

    // Pad so that concatenatedBytes length is divisible by BYTES_PER_CHUNK
    int packingRemainder = concatenatedBytes.size() % BYTES_PER_CHUNK;
    if (packingRemainder != 0) {
      concatenatedBytes =
          Bytes.concatenate(
              concatenatedBytes, Bytes.wrap(new byte[BYTES_PER_CHUNK - packingRemainder]));
    }

    // Wrap each BYTES_PER_CHUNK-byte value into a Bytes32
    List<Bytes32> chunkifiedBytes = new ArrayList<>();
    for (int chunk = 0; chunk < concatenatedBytes.size(); chunk += BYTES_PER_CHUNK) {
      chunkifiedBytes.add(Bytes32.wrap(concatenatedBytes, chunk));
    }

    return chunkifiedBytes;
  }

  private static Bytes32 mix_in_length(Bytes32 merkle_root, int length) {
    // Append the little-endian length mixin to the given merkle root, and return its hash.
    return Hash.keccak256(
        Bytes.concatenate(merkle_root, Bytes.ofUnsignedInt(length, LITTLE_ENDIAN)));
  }

  private static boolean is_power_of_two(int value) {
    return value > 0 && (value & (value - 1)) == 0;
  }
}
