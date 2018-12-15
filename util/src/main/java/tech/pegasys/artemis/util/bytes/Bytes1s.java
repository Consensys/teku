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

package tech.pegasys.artemis.util.bytes;

/** Static utility methods to work with {@link Bytes1}. */
public class Bytes1s {
  private Bytes1s() {}

  public static void and(Bytes1 v1, Bytes1 v2, MutableBytes1 result) {
    for (int i = 0; i < Bytes1.SIZE; i++) {
      result.set(i, (byte) (v1.get(i) & v2.get(i)));
    }
  }

  public static Bytes1 and(Bytes1 v1, Bytes1 v2) {
    MutableBytes1 mb1 = MutableBytes1.create();
    and(v1, v2, mb1);
    return mb1;
  }

  public static void or(Bytes1 v1, Bytes1 v2, MutableBytes1 result) {
    for (int i = 0; i < Bytes1.SIZE; i++) {
      result.set(i, (byte) (v1.get(i) | v2.get(i)));
    }
  }

  public static Bytes1 or(Bytes1 v1, Bytes1 v2) {
    MutableBytes1 mb1 = MutableBytes1.create();
    or(v1, v2, mb1);
    return mb1;
  }

  public static void xor(Bytes1 v1, Bytes1 v2, MutableBytes1 result) {
    for (int i = 0; i < Bytes1.SIZE; i++) {
      result.set(i, (byte) (v1.get(i) ^ v2.get(i)));
    }
  }

  public static Bytes1 xor(Bytes1 v1, Bytes1 v2) {
    MutableBytes1 mb1 = MutableBytes1.create();
    xor(v1, v2, mb1);
    return mb1;
  }

  public static void not(Bytes1 v, MutableBytes1 result) {
    for (int i = 0; i < Bytes1.SIZE; i++) {
      result.set(i, (byte) ~v.get(i));
    }
  }

  public static Bytes1 not(Bytes1 v) {
    MutableBytes1 mb1 = MutableBytes1.create();
    not(v, mb1);
    return mb1;
  }

  public static String unprefixedHexString(Bytes1 v) {
    return v.toString().substring(2);
  }
}
