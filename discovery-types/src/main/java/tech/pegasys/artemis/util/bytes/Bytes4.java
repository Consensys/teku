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

/**
 * A {@link BytesValue} that is guaranteed to contain exactly 4 bytes.
 *
 * @see Bytes4 for static methods to create and work with {@link Bytes4}.
 */
public interface Bytes4 extends BytesValue {
  int SIZE = 4;

  Bytes4 ZERO = wrap(new byte[4]);

  /**
   * Converts int to Bytes4.
   *
   * @param seed converted
   * @return converted Bytes4
   */
  static Bytes4 ofUnsignedInt(long seed) {
    byte[] bytes = new byte[4];
    bytes[0] = (byte) (seed >> 24);
    bytes[1] = (byte) (seed >> 16);
    bytes[2] = (byte) (seed >> 8);
    bytes[3] = (byte) seed;
    return Bytes4.wrap(bytes);
  }

  /**
   * Converts int to little-endian Bytes4.
   *
   * @param seed converted
   * @return converted Bytes4
   */
  static Bytes4 ofUnsignedIntLittleEndian(long seed) {
    byte[] bytes = new byte[4];
    bytes[0] = (byte) seed;
    bytes[1] = (byte) (seed >> 8);
    bytes[2] = (byte) (seed >> 16);
    bytes[3] = (byte) (seed >> 24);
    return Bytes4.wrap(bytes);
  }

  /**
   * Wraps the provided byte array, which must be of length 4, as a {@link Bytes4}.
   *
   * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
   * will be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @return A {@link Bytes4} wrapping {@code value}.
   * @throws IllegalArgumentException if {@code value.length != 4}.
   */
  static Bytes4 wrap(byte[] bytes) {
    checkArgument(bytes.length == SIZE, "Expected %s bytes but got %s", SIZE, bytes.length);
    return wrap(bytes, 0);
  }

  /**
   * Wraps a slice/sub-part of the provided array as a {@link Bytes4}.
   *
   * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
   * within the wrapped parts will be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @param offset The index (inclusive) in {@code value} of the first byte exposed by the returned
   *     value. In other words, you will have {@code wrap(value, i).get(0) == value[i]}.
   * @return A {@link Bytes4} that exposes the bytes of {@code value} from {@code offset}
   *     (inclusive) to {@code offset + 4} (exclusive).
   * @throws IndexOutOfBoundsException if {@code offset &lt; 0 || (value.length &gt; 0 && offset >=
   *     value.length)}.
   * @throws IllegalArgumentException if {@code length &lt; 0 || offset + 4 &gt; value.length}.
   */
  static Bytes4 wrap(byte[] bytes, int offset) {
    return new ArrayWrappingBytes4(bytes, offset);
  }

  /**
   * Wraps a slice/sub-part of the provided value as a {@link Bytes4}.
   *
   * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
   * within the wrapped parts will be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @param offset The index (inclusive) in {@code value} of the first byte exposed by the returned
   *     value. In other words, you will have {@code wrap(value, i).get(0) == value.get(i)}.
   * @return A {@link Bytes4} that exposes the bytes of {@code value} from {@code offset}
   *     (inclusive) to {@code offset + 4} (exclusive).
   * @throws IndexOutOfBoundsException if {@code offset &lt; 0 || (value.size() &gt; 0 && offset >=
   *     value.size())}.
   * @throws IllegalArgumentException if {@code length &lt; 0 || offset + 4 &gt; value.size()}.
   */
  static Bytes4 wrap(BytesValue bytes, int offset) {
    BytesValue slice = bytes.slice(offset, Bytes4.SIZE);
    return slice instanceof Bytes4 ? (Bytes4) slice : new WrappingBytes4(slice);
  }

  /**
   * Left pad a {@link BytesValue} with zero bytes to create a {@link Bytes4}
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes4} that exposes the left-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() &gt; 4}.
   */
  static Bytes4 leftPad(BytesValue value) {
    checkArgument(
        value.size() <= SIZE, "Expected at most %s bytes but got only %s", SIZE, value.size());

    MutableBytes4 bytes = MutableBytes4.create();
    value.copyTo(bytes, SIZE - value.size());
    return bytes;
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes4}.
   *
   * <p>This method is lenient in that {@code str} may of an odd length, in which case it will
   * behave exactly as if it had an additional 0 in front.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x". That
   *     representation may contain less than 4 bytes, in which case the result is left padded with
   *     zeros (see {@link #fromHexStringStrict} if this is not what you want).
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation or contains more than 4 bytes.
   */
  static Bytes4 fromHexStringLenient(String str) {
    return wrap(BytesValues.fromRawHexString(str, SIZE, true));
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes4}.
   *
   * <p>This method is strict in that {@code str} must of an even length.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x". That
   *     representation may contain less than 4 bytes, in which case the result is left padded with
   *     zeros (see {@link #fromHexStringStrict} if this is not what you want).
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation, is of an odd length, or contains more than 4 bytes.
   */
  static Bytes4 fromHexString(String str) {
    return wrap(BytesValues.fromRawHexString(str, SIZE, false));
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes4}.
   *
   * <p>This method is extra strict in that {@code str} must of an even length and the provided
   * representation must have exactly 4 bytes.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x".
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation, is of an odd length or does not contain exactly 4 bytes.
   */
  static Bytes4 fromHexStringStrict(String str) {
    return wrap(BytesValues.fromRawHexString(str, -1, false));
  }

  @Override
  default int size() {
    return SIZE;
  }

  @Override
  Bytes4 copy();

  @Override
  MutableBytes4 mutableCopy();
}
