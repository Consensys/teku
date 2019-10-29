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

package tech.pegasys.artemis.ethereum.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Random;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.DelegatingBytes32;

/** A 32-bytes hash value as used in Ethereum blocks, that is the result of the KEC algorithm. */
public class Hash32 extends DelegatingBytes32 implements Hash {

  public static final Hash32 ZERO = new Hash32(Bytes32.ZERO);

  private Hash32(final Bytes32 bytes) {
    super(bytes);
  }

  public static Hash32 wrap(final Bytes32 bytes) {
    return new Hash32(bytes);
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
  public static Hash32 fromHexString(final String str) {
    return new Hash32(Bytes32.fromHexStringStrict(str));
  }

  public static Hash32 fromHexStringLenient(final String str) {
    return new Hash32(Bytes32.fromHexStringLenient(str));
  }

  /**
   * Constructs a randomly generated hash value.
   *
   * @param rnd random number generator.
   * @return random hash value.
   */
  public static Hash32 random(Random rnd) {
    return wrap(Bytes32.random(rnd));
  }

  public String toStringShort() {
    return super.toString().substring(2, 10);
  }
}
