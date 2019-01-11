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

import static tech.pegasys.artemis.crypto.Hash.keccak256;

import com.fasterxml.jackson.annotation.JsonCreator;
import tech.pegasys.artemis.ethereum.rlp.RLP;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.bytes.DelegatingBytes32;

/** A 32-bytes hash value as used in Ethereum blocks, that is the result of the KEC algorithm. */
public class Hash extends DelegatingBytes32 {

  public static final Hash ZERO = new Hash(Bytes32.ZERO);

  public static final Hash EMPTY_TRIE_HASH = Hash.hash(RLP.NULL);

  public static final Hash EMPTY_LIST_HASH = Hash.hash(RLP.EMPTY_LIST);

  public static final Hash EMPTY = hash(BytesValue.EMPTY);

  private Hash(Bytes32 bytes) {
    super(bytes);
  }

  public static Hash hash(BytesValue value) {
    return new Hash(keccak256(value));
  }

  public static Hash wrap(Bytes32 bytes) {
    return new Hash(bytes);
  }

  /**
   * Parse an hexadecimal string representing a hash value.
   *
   * @param str An hexadecimal string (with or without the leading '0x') representing a valid hash
   *     value.
   * @return The parsed hash.
   * @throws NullPointerException if the provided string is {@code null}.
   * @throws IllegalArgumentException if the string is either not hexadecimal, or not the valid
   *     representation of a hash (not 32 bytes).
   */
  @JsonCreator
  public static Hash fromHexString(String str) {
    return new Hash(Bytes32.fromHexStringStrict(str));
  }

  public static Hash fromHexStringLenient(String str) {
    return new Hash(Bytes32.fromHexStringLenient(str));
  }
}
