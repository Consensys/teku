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

/** Static utility methods to work with {@link Bytes48}. */
public abstract class Bytes48s {
  private Bytes48s() {}

  public static void and(Bytes48 v1, Bytes48 v2, MutableBytes48 result) {
    for (int i = 0; i < Bytes48.SIZE; i++) {
      result.set(i, (byte) (v1.get(i) & v2.get(i)));
    }
  }

  public static Bytes48 and(Bytes48 v1, Bytes48 v2) {
    MutableBytes48 mb48 = MutableBytes48.create();
    and(v1, v2, mb48);
    return mb48;
  }

  public static void or(Bytes48 v1, Bytes48 v2, MutableBytes48 result) {
    for (int i = 0; i < Bytes48.SIZE; i++) {
      result.set(i, (byte) (v1.get(i) | v2.get(i)));
    }
  }

  public static Bytes48 or(Bytes48 v1, Bytes48 v2) {
    MutableBytes48 mb48 = MutableBytes48.create();
    or(v1, v2, mb48);
    return mb48;
  }

  public static void xor(Bytes48 v1, Bytes48 v2, MutableBytes48 result) {
    for (int i = 0; i < Bytes48.SIZE; i++) {
      result.set(i, (byte) (v1.get(i) ^ v2.get(i)));
    }
  }

  public static Bytes48 xor(Bytes48 v1, Bytes48 v2) {
    MutableBytes48 mb48 = MutableBytes48.create();
    xor(v1, v2, mb48);
    return mb48;
  }

  public static void not(Bytes48 v, MutableBytes48 result) {
    for (int i = 0; i < Bytes48.SIZE; i++) {
      result.set(i, (byte) ~v.get(i));
    }
  }

  public static Bytes48 not(Bytes48 v) {
    MutableBytes48 mb48 = MutableBytes48.create();
    not(v, mb48);
    return mb48;
  }

  public static String unprefixedHexString(Bytes48 v) {
    return v.toString().substring(2);
  }
}
