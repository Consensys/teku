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

import static com.google.common.base.Preconditions.checkArgument;

import net.consensys.artemis.util.uint.Int256;
import net.consensys.artemis.util.uint.UInt256;
import net.consensys.artemis.util.uint.UInt256Bytes;


/**
 * A {@link BytesValue} that is guaranteed to contain exactly 3 bytes.
 *
 * @see Bytes3 for static methods to create and work with {@link Bytes3}.
 */
public interface Bytes3 extends BytesValue {
  int SIZE = 3;

  Bytes3 FALSE = UInt256Bytes.ofBytes3(0);
  Bytes3 TRUE = UInt256Bytes.ofBytes3(1);
  Bytes3 ZERO = wrap(new byte[3]);

  /**
   * Wraps the provided byte array, which must be of length 3, as a {@link Bytes3}.
   *
   * <p>
   * Note that value is not copied, only wrapped, and thus any future update to {@code value} will
   * be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @return A {@link Bytes3} wrapping {@code value}.
   * @throws IllegalArgumentException if {@code value.length != 3}.
   */
  static Bytes3 wrap(byte[] bytes) {
    checkArgument(bytes.length == SIZE, "Expected %s bytes but got %s", SIZE, bytes.length);
    return wrap(bytes, 0);
  }

  /**
   * Wraps a slice/sub-part of the provided array as a {@link Bytes3}.
   *
   * <p>
   * Note that value is not copied, only wrapped, and thus any future update to {@code value} within
   * the wrapped parts will be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @param offset The index (inclusive) in {@code value} of the first byte exposed by the returned
   *        value. In other words, you will have {@code wrap(value, i).get(0) == value[i]}.
   * @return A {@link Bytes3} that exposes the bytes of {@code value} from {@code offset}
   *         (inclusive) to {@code offset + 3} (exclusive).
   * @throws IndexOutOfBoundsException if {@code offset &lt; 0 || (value.length &gt; 0 && offset >=
   *     value.length)}.
   * @throws IllegalArgumentException if {@code length &lt; 0 || offset + 3 &gt; value.length}.
   */
  static Bytes3 wrap(byte[] bytes, int offset) {
    return new ArrayWrappingBytes3(bytes, offset);
  }

  /**
   * Wraps a slice/sub-part of the provided value as a {@link Bytes3}.
   *
   * <p>
   * Note that value is not copied, only wrapped, and thus any future update to {@code value} within
   * the wrapped parts will be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @param offset The index (inclusive) in {@code value} of the first byte exposed by the returned
   *        value. In other words, you will have {@code wrap(value, i).get(0) == value.get(i)}.
   * @return A {@link Bytes3} that exposes the bytes of {@code value} from {@code offset}
   *         (inclusive) to {@code offset + 3} (exclusive).
   * @throws IndexOutOfBoundsException if {@code offset &lt; 0 || (value.size() &gt; 0 && offset >=
   *     value.size())}.
   * @throws IllegalArgumentException if {@code length &lt; 0 || offset + 3 &gt; value.size()}.
   */
  static Bytes3 wrap(BytesValue bytes, int offset) {
    BytesValue slice = bytes.slice(offset, Bytes3.SIZE);
    return slice instanceof Bytes3 ? (Bytes3) slice : new WrappingBytes3(slice);
  }

  /**
   * Left pad a {@link BytesValue} with zero bytes to create a {@link Bytes3}
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes3} that exposes the left-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() &gt; 3}.
   */
  static Bytes3 leftPad(BytesValue value) {
    checkArgument(value.size() <= SIZE, "Expected at most %s bytes but got only %s", SIZE,
        value.size());

    MutableBytes3 bytes = MutableBytes3.create();
    value.copyTo(bytes, SIZE - value.size());
    return bytes;
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes3}.
   *
   * <p>
   * This method is lenient in that {@code str} may of an odd length, in which case it will behave
   * exactly as if it had an additional 0 in front.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x". That
   *        representation may contain less than 3 bytes, in which case the result is left padded
   *        with zeros (see {@link #fromHexStringStrict} if this is not what you want).
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *         representation or contains more than 3 bytes.
   */
  static Bytes3 fromHexStringLenient(String str) {
    return wrap(BytesValues.fromRawHexString(str, SIZE, true));
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes3}.
   *
   * <p>
   * This method is strict in that {@code str} must of an even length.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x". That
   *        representation may contain less than 3 bytes, in which case the result is left padded
   *        with zeros (see {@link #fromHexStringStrict} if this is not what you want).
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *         representation, is of an odd length, or contains more than 3 bytes.
   */
  static Bytes3 fromHexString(String str) {
    return wrap(BytesValues.fromRawHexString(str, SIZE, false));
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes3}.
   *
   * <p>
   * This method is extra strict in that {@code str} must of an even length and the provided
   * representation must have exactly 3 bytes.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x".
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *         representation, is of an odd length or does not contain exactly 3 bytes.
   */
  static Bytes3 fromHexStringStrict(String str) {
    return wrap(BytesValues.fromRawHexString(str, -1, false));
  }

  @Override
  default int size() {
    return SIZE;
  }

  @Override
  Bytes3 copy();

  @Override
  MutableBytes3 mutableCopy();

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
