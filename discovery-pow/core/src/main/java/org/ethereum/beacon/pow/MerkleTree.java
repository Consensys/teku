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

package org.ethereum.beacon.pow;

import java.util.List;
import tech.pegasys.artemis.ethereum.core.Hash32;

/**
 * Merkle Hash Tree <a
 * href="https://en.wikipedia.org/wiki/Merkle_tree">https://en.wikipedia.org/wiki/Merkle_tree</a>
 * with proofs
 *
 * @param <V> Element type
 */
public interface MerkleTree<V> {
  /**
   * Proofs for element with provided index on tree with specified size
   *
   * <p><strong>Note:</strong> result has encoded deposit count value as last element.
   *
   * @param index at index
   * @param size with all tree made of size elements
   * @return proofs
   */
  List<Hash32> getProof(int index, int size);

  /**
   * Root of merkle tree with all elements up to index
   *
   * <p><strong>Note:</strong> computed root includes deposit count by mixing it with tree root.
   *
   * @param index last element index
   * @return tree root
   */
  Hash32 getRoot(int index);

  /**
   * Inserts value in tree / storage
   *
   * @param value Element value
   */
  void addValue(V value);

  /** @return Index of last/highest element */
  int getLastIndex();
}
