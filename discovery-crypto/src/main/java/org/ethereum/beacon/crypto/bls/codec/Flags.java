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

package org.ethereum.beacon.crypto.bls.codec;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import org.apache.milagro.amcl.BLS381.BIG;
import org.ethereum.beacon.crypto.bls.milagro.FPs;

/**
 * A class to work with flags which are a part of representation format of elliptic curve points.
 *
 * <p>These flags are stored in three highest bits of {@code x} coordinate of the point in a
 * following order: {@code {c_flag | b_flag | a_flag}} where {@code c_flag} lands to the highest bit
 * of {@code x}.
 *
 * @see PointData
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/bls_signature.md#point-representations">https://github.com/ethereum/eth2.0-specs/blob/master/specs/bls_signature.md#point-representations</a>
 */
public class Flags {

  /**
   * A bit that holds a sign of {@code y} coordinate.
   *
   * <p>Check {@link FPs#getSign(BIG, BIG)} to get the idea of what sign is.
   */
  static final int A = 1;
  /**
   * A bit that is set to {@code 1} when point is a point at infinity, otherwise, it must be set to
   * {@code 0}.
   */
  static final int B = 2;
  /**
   * A bit signaling that point is encoded in a compressed format. In our case always set to {@code
   * 1}.
   */
  static final int C = 4;

  static final int SIGN = A;
  static final int INFINITY = B;

  private final int bits;

  private Flags(int bits) {
    this.bits = bits;
  }

  /**
   * Creates an instance from the infinity and the sign.
   *
   * @param infinity whether point is a point at infinity.
   * @param sign a sign.
   * @return the instance of flags.
   */
  static Flags create(boolean infinity, int sign) {
    if (infinity) {
      return new Flags(C | INFINITY);
    } else if (sign > 0) {
      return new Flags(C | SIGN);
    } else {
      return new Flags(C);
    }
  }

  /**
   * Creates an instance with all flags set to {@code 0}.
   *
   * @return the instance.
   */
  static Flags empty() {
    return new Flags(0);
  }

  /**
   * Reads flag values from a byte.
   *
   * @param value a byte to read values from.
   * @return an instance.
   */
  @VisibleForTesting
  public static Flags read(byte value) {
    return new Flags((value >> 5) & 0x7);
  }

  /**
   * Erases flags from given byte value.
   *
   * @param value a value.
   * @return given value with its three highest bits set to {@code 0}.
   */
  static byte erase(byte value) {
    return (byte) (value & 0x1F);
  }

  /**
   * Writes flags to given byte value.
   *
   * @param value a value.
   * @return given byte value with written flags in it.
   */
  byte write(byte value) {
    return (byte) ((value | (bits << 5)) & 0xFF);
  }

  /**
   * Zero check.
   *
   * @return {@code true} if all flags are set to {@code 0}, {@code false} otherwise.
   */
  boolean isZero() {
    return bits == 0;
  }

  /**
   * Returns a value of a given flag.
   *
   * @param flag bit mask of a flag.
   * @return {@code 1} if flag is set, {@code 0} otherwise.
   */
  public int test(int flag) {
    return (bits & flag) > 0 ? 1 : 0;
  }

  /**
   * Checks for sign flag value.
   *
   * @return {@code true} if sign flag is set, {@code false} otherwise.
   */
  @VisibleForTesting
  public boolean isSignSet() {
    return test(SIGN) > 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Flags flags = (Flags) o;
    return bits == flags.bits;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("c", test(C))
        .add("inf", test(INFINITY))
        .add("sign", test(SIGN))
        .toString();
  }
}
