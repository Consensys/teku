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

package net.consensys.artemis.util.bytes;

/** Static utility methods to work with {@link Bytes32}. */
public abstract class Bytes32s {
  private Bytes32s() {}

  public static void and(Bytes32 v1, Bytes32 v2, MutableBytes32 result) {
    for (int i = 0; i < Bytes32.SIZE; i++) {
      result.set(i, (byte) (v1.get(i) & v2.get(i)));
    }
  }

  public static Bytes32 and(Bytes32 v1, Bytes32 v2) {
    MutableBytes32 mb32 = MutableBytes32.create();
    and(v1, v2, mb32);
    return mb32;
  }

  public static void or(Bytes32 v1, Bytes32 v2, MutableBytes32 result) {
    for (int i = 0; i < Bytes32.SIZE; i++) {
      result.set(i, (byte) (v1.get(i) | v2.get(i)));
    }
  }

  public static Bytes32 or(Bytes32 v1, Bytes32 v2) {
    MutableBytes32 mb32 = MutableBytes32.create();
    or(v1, v2, mb32);
    return mb32;
  }

  public static void xor(Bytes32 v1, Bytes32 v2, MutableBytes32 result) {
    for (int i = 0; i < Bytes32.SIZE; i++) {
      result.set(i, (byte) (v1.get(i) ^ v2.get(i)));
    }
  }

  public static Bytes32 xor(Bytes32 v1, Bytes32 v2) {
    MutableBytes32 mb32 = MutableBytes32.create();
    xor(v1, v2, mb32);
    return mb32;
  }

  public static void not(Bytes32 v, MutableBytes32 result) {
    for (int i = 0; i < Bytes32.SIZE; i++) {
      result.set(i, (byte) (~v.get(i)));
    }
  }

  public static Bytes32 not(Bytes32 v) {
    MutableBytes32 mb32 = MutableBytes32.create();
    not(v, mb32);
    return mb32;
  }

  public static String unprefixedHexString(Bytes32 v) {
    return v.toString().substring(2);
  }
}
