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

package tech.pegasys.teku.ssz.backing;

import org.apache.tuweni.bytes.Bytes32;

/**
 * Returns `hash_tree_root` conforming to SSZ spec:
 * https://github.com/ethereum/eth2.0-specs/blob/dev/ssz/simple-serialize.md#merkleization
 */
public interface Merkleizable {

  /**
   * Returns `hash_tree_root` conforming to SSZ spec:
   * https://github.com/ethereum/eth2.0-specs/blob/dev/ssz/simple-serialize.md#merkleization
   */
  Bytes32 hashTreeRoot();
}
