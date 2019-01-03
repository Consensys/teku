/*
 * Copyright 2018 ConsenSys AG.
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

package tech.pegasys.artemis.ethereum.core;

import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.bytes.MutableBytes32;
import tech.pegasys.artemis.util.uint.UInt256Bytes;

import java.util.ArrayList;
import java.util.Collections;

import static com.google.common.base.Charsets.UTF_8;
import static tech.pegasys.artemis.util.bytes.Bytes32.fromHexStringStrict;
import static tech.pegasys.artemis.util.bytes.Bytes32.intToBytes32;

public class TreeHash {

  private static int SSZ_CHUNK_SIZE = 128;

  public <T> ArrayList<T> hash_tree_root(ArrayList<BytesValue> value) {
    ArrayList<Bytes32> lst = new ArrayList<>(value.size());

    // if the output is less than 32 bytes, right-zero-pad it to 32 bytes
    if (Bytes32.lessThan32Bytes(value)) {
      return Bytes32.rightPad(value);
    }

    for (int i = 0; i < value.size(); i++) {
      ArrayList<BytesValue> new_value = new ArrayList<>();
      new_value.add(value.get(i));
      lst.add(hash_tree_root(new_value));
      lst.set(i, hash_tree_root(value.get(i)));
    }

    return merkle_hash(lst);
  }


  /**
   * Merkle tree hash of a list of homogenous, non-empty items.
   * @param lst
   * @return
   */
  private Hash merkle_hash(ArrayList<Bytes32> lst) {
    // Store length of list (to compensate for non-bijectiveness of padding)
    Bytes32 datalen = intToBytes32(lst.size());
    ArrayList<Bytes32> chunkz = new ArrayList<>(lst.size());

    if (lst.size() == 0) {
      // Handle empty list case
      chunkz = new ArrayList<>(Collections.nCopies(SSZ_CHUNK_SIZE, Bytes32.FALSE));

    } else if (lst.get(0).size() < SSZ_CHUNK_SIZE) {
      // See how many items fit in a chunk
      int items_per_chunk = SSZ_CHUNK_SIZE / lst.get(0).size();

      // Build a list of chunks based on the number of items in the chunk
      chunkz = new ArrayList<>(items_per_chunk);
      for (int i = 0; i < lst.size(); i += items_per_chunk) {

        StringBuilder new_val = new StringBuilder();
        for (int j = 0; j < items_per_chunk; j++) {
          new_val.append(new String(lst.get(i + j).extractArray(), UTF_8));
        }
        chunkz.set(i, fromHexStringStrict(new_val.toString()));
      }

    } else {
      // Leave large items alone
      chunkz = lst;
    }

    // Tree-hash
    ArrayList<Bytes32> new_chunkz = new ArrayList<>();
    while (chunkz.size() > 1) {
      if (chunkz.size() % 2 == 1) {
        chunkz.addAll(Collections.nCopies(SSZ_CHUNK_SIZE, Bytes32.FALSE));
      }
      for (int i = 0; i < chunkz.size(); i += 2) {
        MutableBytes32 chunk_to_hash = MutableBytes32.create();
        UInt256Bytes.add(chunkz.get(i), chunkz.get(i+1), chunk_to_hash);
        new_chunkz.add(Hash.hash(chunk_to_hash));
      }
    }

    // Return hash of root and length data
    MutableBytes32 sum_ = MutableBytes32.create();
    UInt256Bytes.add(new_chunkz.get(0), datalen, sum_);
    return Hash.hash(sum_);
  }
}
