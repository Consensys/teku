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

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.artemis.util.uint.Int256;
import tech.pegasys.artemis.util.uint.UInt256;
import tech.pegasys.artemis.util.uint.UInt256Bytes;


/**
 * A {@link BytesValue} that is guaranteed to contain exactly 1 byte.
 *
 * @see Bytes1 for static methods to create and work with {@link Bytes1}.
 */
public interface Bytes1 extends BytesValue {
  int SIZE = 1;

  Bytes1 FALSE = UInt256Bytes.ofBytes1(0);
  Bytes1 TRUE = UInt256Bytes.ofBytes1(1);
  Bytes1 ZERO = wrap(new byte[1]);

  /**
   * Converts int to Bytes1.
   *
   * @param seed  converted
   * @return      converted Bytes1
   * @throws IllegalArgumentException if seed is a negative value.
   */
  static Bytes1 intToBytes1(int seed) {
    checkArgument(seed > 0, "Expected positive seed but got %s", seed);
    byte[] bytes = new byte[]{(byte) seed};
    return Bytes1.wrap(bytes);
  }

  /**
   * Wraps the provided byte array, which must be of length 1, as a {@link Bytes1}.
   *
   * <p>
   * Note that value is not copied, only wrapped, and thus any future update to {@code value} will
   * be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @return A {@link Bytes1} wrapping {@code value}.
   * @throws IllegalArgumentException if {@code value.length != 1}.
   */
  static Bytes1 wrap(byte[] bytes) {
    checkArgument(bytes.length == SIZE, "Expected %s bytes but got %s", SIZE, bytes.length);
    return new ArrayWrappingBytes1(bytes);
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes1}.
   *
   * <p>
   * This method is lenient in that {@code str} may of an odd length, in which case it will behave
   * exactly as if it had an additional 0 in front.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x". That
   *        representation may contain less than 1 byte, in which case the result is left padded
   *        with zeros (see {@link #fromHexStringStrict} if this is not what you want).
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *         representation or contains more than 1 byte.
   */
  static Bytes1 fromHexStringLenient(String str) {
    return wrap(BytesValues.fromRawHexString(str, SIZE, true));
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes1}.
   *
   * <p>
   * This method is strict in that {@code str} must of an even length.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x". That
   *        representation may contain less than 1 byte, in which case the result is left padded
   *        with zeros (see {@link #fromHexStringStrict} if this is not what you want).
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *         representation, is of an odd length, or contains more than 1 byte.
   */
  static Bytes1 fromHexString(String str) {
    return wrap(BytesValues.fromRawHexString(str, SIZE, false));
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes1}.
   *
   * <p>
   * This method is extra strict in that {@code str} must of an even length and the provided
   * representation must have exactly 1 byte.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x".
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *         representation, is of an odd length or does not contain exactly 1 byte.
   */
  static Bytes1 fromHexStringStrict(String str) {
    return wrap(BytesValues.fromRawHexString(str, -1, false));
  }

  @Override
  default int size() {
    return SIZE;
  }

  @Override
  Bytes1 copy();

  @Override
  MutableBytes1 mutableCopy();

  /**
   * Return a {@link UInt256} that wraps these bytes.
   *
   * <p>
   * Note that the returned {@link UInt256} is essentially a "view" of these bytes, and so if this
   * value is mutable and is muted, the returned {@link UInt256} will reflect those changes.
   *
   * @return A {@link UInt256} that wraps these bytes.
   */
  default UInt256 asUInt256() {
    return UInt256.wrap(this);
  }

  /**
   * Return a {@link Int256} that wraps these bytes.
   *
   * <p>
   * Note that the returned {@link UInt256} is essentially a "view" of these bytes, and so if this
   * value is mutable and is muted, the returned {@link UInt256} will reflect those changes.
   *
   * @return A {@link Int256} that wraps these bytes.
   */
  default Int256 asInt256() {
    return Int256.wrap(this);
  }
}
