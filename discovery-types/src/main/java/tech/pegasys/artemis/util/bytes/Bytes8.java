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

/**
 * A {@link BytesValue} that is guaranteed to contain exactly 8 bytes.
 *
 * @see Bytes8 for static methods to create and work with {@link Bytes8}.
 */
public interface Bytes8 extends BytesValue {
  int SIZE = 8;

  Bytes8 ZERO = wrap(new byte[8]);

  /**
   * Converts long to Bytes8.
   *
   * @param seed converted
   * @return converted Bytes8
   * @throws IllegalArgumentException if seed is a negative value.
   */
  static Bytes8 longToBytes8(long seed) {
    byte[] bytes = new byte[8];
    bytes[0] = (byte) (seed >> 56);
    bytes[1] = (byte) (seed >> 48);
    bytes[2] = (byte) (seed >> 40);
    bytes[3] = (byte) (seed >> 32);
    bytes[4] = (byte) (seed >> 24);
    bytes[5] = (byte) (seed >> 16);
    bytes[6] = (byte) (seed >> 8);
    bytes[7] = (byte) seed;
    return Bytes8.wrap(bytes);
  }

  /**
   * Converts long to little-endian Bytes8.
   *
   * @param seed converted
   * @return converted Bytes8
   * @throws IllegalArgumentException if seed is a negative value.
   */
  static Bytes8 longToBytes8LittleEndian(long seed) {
    byte[] bytes = new byte[8];
    bytes[0] = (byte) seed;
    bytes[1] = (byte) (seed >> 8);
    bytes[2] = (byte) (seed >> 16);
    bytes[3] = (byte) (seed >> 24);
    bytes[4] = (byte) (seed >> 32);
    bytes[5] = (byte) (seed >> 40);
    bytes[6] = (byte) (seed >> 48);
    bytes[7] = (byte) (seed >> 56);
    return Bytes8.wrap(bytes);
  }

  /**
   * Wraps the provided byte array, which must be of length 8, as a {@link Bytes8}.
   *
   * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
   * will be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @return A {@link Bytes8} wrapping {@code value}.
   * @throws IllegalArgumentException if {@code value.length != 8}.
   */
  static Bytes8 wrap(byte[] bytes) {
    checkArgument(bytes.length == SIZE, "Expected %s bytes but got %s", SIZE, bytes.length);
    return wrap(bytes, 0);
  }

  /**
   * Wraps a slice/sub-part of the provided array as a {@link Bytes8}.
   *
   * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
   * within the wrapped parts will be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @param offset The index (inclusive) in {@code value} of the first byte exposed by the returned
   *     value. In other words, you will have {@code wrap(value, i).get(0) == value[i]}.
   * @return A {@link Bytes8} that exposes the bytes of {@code value} from {@code offset}
   *     (inclusive) to {@code offset + 8} (exclusive).
   * @throws IndexOutOfBoundsException if {@code offset &lt; 0 || (value.length &gt; 0 && offset >=
   *     value.length)}.
   * @throws IllegalArgumentException if {@code length &lt; 0 || offset + 8 &gt; value.length}.
   */
  static Bytes8 wrap(byte[] bytes, int offset) {
    return new ArrayWrappingBytes8(bytes, offset);
  }

  /**
   * Wraps a slice/sub-part of the provided value as a {@link Bytes8}.
   *
   * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
   * within the wrapped parts will be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @param offset The index (inclusive) in {@code value} of the first byte exposed by the returned
   *     value. In other words, you will have {@code wrap(value, i).get(0) == value.get(i)}.
   * @return A {@link Bytes8} that exposes the bytes of {@code value} from {@code offset}
   *     (inclusive) to {@code offset + 8} (exclusive).
   * @throws IndexOutOfBoundsException if {@code offset &lt; 0 || (value.size() &gt; 0 && offset >=
   *     value.size())}.
   * @throws IllegalArgumentException if {@code length &lt; 0 || offset + 8 &gt; value.size()}.
   */
  static Bytes8 wrap(BytesValue bytes, int offset) {
    BytesValue slice = bytes.slice(offset, Bytes8.SIZE);
    return slice instanceof Bytes8 ? (Bytes8) slice : new WrappingBytes8(slice);
  }

  /**
   * Left pad a {@link BytesValue} with zero bytes to create a {@link Bytes8}
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes8} that exposes the left-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() &gt; 8}.
   */
  static Bytes8 leftPad(BytesValue value) {
    checkArgument(
        value.size() <= SIZE, "Expected at most %s bytes but got only %s", SIZE, value.size());

    MutableBytes8 bytes = MutableBytes8.create();
    value.copyTo(bytes, SIZE - value.size());
    return bytes;
  }

  /**
   * Right pad a {@link BytesValue} with zero bytes to create a {@link Bytes8}
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes8} that exposes the right-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() &gt; 8}.
   */
  static Bytes8 rightPad(BytesValue value) {
    checkArgument(
        value.size() <= SIZE, "Expected at most %s bytes but got only %s", SIZE, value.size());

    MutableBytes8 bytes = MutableBytes8.create();
    value.copyTo(bytes, 0);
    return bytes;
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes8}.
   *
   * <p>This method is lenient in that {@code str} may of an odd length, in which case it will
   * behave exactly as if it had an additional 0 in front.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x". That
   *     representation may contain less than 8 bytes, in which case the result is left padded with
   *     zeros (see {@link #fromHexStringStrict} if this is not what you want).
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation or contains more than 8 bytes.
   */
  static Bytes8 fromHexStringLenient(String str) {
    return wrap(BytesValues.fromRawHexString(str, SIZE, true));
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes8}.
   *
   * <p>This method is strict in that {@code str} must of an even length.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x". That
   *     representation may contain less than 8 bytes, in which case the result is left padded with
   *     zeros (see {@link #fromHexStringStrict} if this is not what you want).
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation, is of an odd length, or contains more than 8 bytes.
   */
  static Bytes8 fromHexString(String str) {
    return wrap(BytesValues.fromRawHexString(str, SIZE, false));
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes8}.
   *
   * <p>This method is extra strict in that {@code str} must of an even length and the provided
   * representation must have exactly 8 bytes.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x".
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation, is of an odd length or does not contain exactly 8 bytes.
   */
  static Bytes8 fromHexStringStrict(String str) {
    return wrap(BytesValues.fromRawHexString(str, -1, false));
  }

  @Override
  default int size() {
    return SIZE;
  }

  @Override
  Bytes8 copy();

  @Override
  MutableBytes8 mutableCopy();

  /**
   * Return a {@link UInt256} that wraps these bytes.
   *
   * <p>Note that the returned {@link UInt256} is essentially a "view" of these bytes, and so if
   * this value is mutable and is muted, the returned {@link UInt256} will reflect those changes.
   *
   * @return A {@link UInt256} that wraps these bytes.
   */
  default UInt256 asUInt256() {
    return UInt256.wrap(this);
  }

  /**
   * Return a {@link Int256} that wraps these bytes.
   *
   * <p>Note that the returned {@link UInt256} is essentially a "view" of these bytes, and so if
   * this value is mutable and is muted, the returned {@link UInt256} will reflect those changes.
   *
   * @return A {@link Int256} that wraps these bytes.
   */
  default Int256 asInt256() {
    return Int256.wrap(this);
  }
}
